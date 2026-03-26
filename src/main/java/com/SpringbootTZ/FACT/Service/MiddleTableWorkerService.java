package com.SpringbootTZ.FACT.Service;

import com.SpringbootTZ.FACT.Entity.InterestChangeNotify;
import com.SpringbootTZ.FACT.Mapper.DailyNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.InterestNotifyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 最小闭环 Worker：
 * 1) 扫描三张中间表 process_status=0
 * 2) 仅做日志处理（不执行业务计算）
 * 3) 回写 process_status（成功=1，失败=2）
 *
 * 说明：
 * - 默认关闭（app.worker.middle.enabled=false），避免影响现有 UnifiedNotifyService 业务链路
 * - 适用于“无数据库设计验证 / 先打通消费闭环”阶段
 */
@Service
public class MiddleTableWorkerService {

    private static final Logger log = LoggerFactory.getLogger(MiddleTableWorkerService.class);

    private final InterestNotifyMapper interestNotifyMapper;
    private final DailyNotifyMapper dailyNotifyMapper;
    private final InstallmentNotifyMapper installmentNotifyMapper;

    @Value("${app.worker.middle.enabled:false}")
    private boolean enabled;

    @Value("${app.worker.middle.batch-size:200}")
    private int batchSize;

    @Value("${app.worker.middle.max-retry:3}")
    private int maxRetry;

    private final AtomicReference<RunStats> lastRunStats = new AtomicReference<>(RunStats.empty());

    public MiddleTableWorkerService(InterestNotifyMapper interestNotifyMapper,
                                    DailyNotifyMapper dailyNotifyMapper,
                                    InstallmentNotifyMapper installmentNotifyMapper) {
        this.interestNotifyMapper = interestNotifyMapper;
        this.dailyNotifyMapper = dailyNotifyMapper;
        this.installmentNotifyMapper = installmentNotifyMapper;
    }

    @Scheduled(fixedDelayString = "${app.worker.middle.fixed-delay-ms:30000}")
    public synchronized void scheduledRun() {
        if (!enabled) {
            return;
        }
        runOnce();
    }

    /**
     * 允许被管理接口手动触发一次，便于联调。
     */
    public synchronized void runOnce() {
        RunStats stats = new RunStats();
        long start = System.currentTimeMillis();

        processInterestChangeNotify(stats);
        processDailyMiddleRecords(stats);
        processInstallmentMiddleRecords(stats);

        stats.elapsedMs = System.currentTimeMillis() - start;
        lastRunStats.set(stats);
        log.info("[worker] run summary elapsedMs={}, interest(s/f/skipped)={}/{}/{}, daily(s/f/skipped)={}/{}/{}, installment(s/f/skipped)={}/{}/{}",
                stats.elapsedMs,
                stats.interestSuccess, stats.interestFail, stats.interestSkippedByRetryLimit,
                stats.dailySuccess, stats.dailyFail, stats.dailySkippedByRetryLimit,
                stats.installmentSuccess, stats.installmentFail, stats.installmentSkippedByRetryLimit);
    }

    public Map<String, Object> getStatusSnapshot() {
        RunStats stats = lastRunStats.get();
        Integer interestPending = nvl(interestNotifyMapper.countPending());
        Integer dailyPending = nvl(dailyNotifyMapper.countPending());
        Integer installmentPending = nvl(installmentNotifyMapper.countPending());
        int safeMaxRetry = safeMaxRetry();

        return mapOf(
                "enabled", enabled,
                "batchSize", batchSize,
                "maxRetry", safeMaxRetry,
                "pendingInterest", interestPending,
                "pendingDaily", dailyPending,
                "pendingInstallment", installmentPending,
                "failedInterest", nvl(interestNotifyMapper.countFailed()),
                "failedDaily", nvl(dailyNotifyMapper.countFailed()),
                "failedInstallment", nvl(installmentNotifyMapper.countFailed()),
                "retryLimitHitDaily", nvl(dailyNotifyMapper.countRetryLimitHits(safeMaxRetry)),
                "retryLimitHitInstallment", nvl(installmentNotifyMapper.countRetryLimitHits(safeMaxRetry)),
                "lastRun", stats.toMap()
        );
    }

    public List<Map<String, Object>> listFailedSamples(String table, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);
        String t = normalizeTable(table);
        if ("interest".equals(t)) {
            return interestNotifyMapper.listFailedSamples(safeLimit);
        }
        if ("daily".equals(t)) {
            return dailyNotifyMapper.listFailedSamples(safeLimit);
        }
        if ("installment".equals(t)) {
            return installmentNotifyMapper.listFailedSamples(safeLimit);
        }
        throw new IllegalArgumentException("不支持的 table: " + table + "，仅支持 interest/daily/installment");
    }

    public int resetFailedToPending(String table) {
        String t = normalizeTable(table);
        if ("interest".equals(t)) {
            return interestNotifyMapper.resetFailedToPending();
        }
        if ("daily".equals(t)) {
            return dailyNotifyMapper.resetFailedToPending();
        }
        if ("installment".equals(t)) {
            return installmentNotifyMapper.resetFailedToPending();
        }
        throw new IllegalArgumentException("不支持的 table: " + table + "，仅支持 interest/daily/installment");
    }

    private void processInterestChangeNotify(RunStats stats) {
        List<InterestChangeNotify> rows = interestNotifyMapper.getStatusNotLimit(safeBatchSize());
        if (rows == null || rows.isEmpty()) {
            return;
        }
        log.info("[worker] interest_change_notify 本轮处理 {} 条", rows.size());
        for (InterestChangeNotify row : rows) {
            Long id = row.getId();
            try {
                log.info("[worker] interest_change_notify consume id={}, sourceId={}, billNo={}, rate={}, effectiveTime={}",
                        id, row.getSourceId(), row.getBillNo(), row.getCurrentRate(), row.getRateEffectiveTime());

                // 最小闭环阶段：仅日志 + 标记成功
                interestNotifyMapper.updateProcessStatus(id);
                stats.interestSuccess++;
            } catch (Exception e) {
                log.error("[worker] interest_change_notify consume failed id={}", id, e);
                interestNotifyMapper.updateProcessStatusFailed(id);
                stats.interestFail++;
            }
        }
    }

    private void processDailyMiddleRecords(RunStats stats) {
        List<Map<String, Object>> rows = dailyNotifyMapper.getStatusNotLimit(safeBatchSize());
        if (rows == null || rows.isEmpty()) {
            return;
        }
        log.info("[worker] Middle_Insert_Records 本轮处理 {} 条", rows.size());
        for (Map<String, Object> row : rows) {
            Integer id = toInt(row.get("id"));
            try {
                if (id == null) {
                    throw new IllegalArgumentException("id 为空");
                }
                Integer retryCount = toInt(row.get("retry_count"));
                if (retryCount != null && retryCount >= safeMaxRetry()) {
                    stats.dailySkippedByRetryLimit++;
                    log.warn("[worker] Middle_Insert_Records skip by retry limit id={}, retryCount={}, maxRetry={}",
                            id, retryCount, safeMaxRetry());
                    continue;
                }
                String targetTableId = toStr(row.get("target_table_id"));
                String monitoredField = toStr(row.get("monitored_field"));
                log.info("[worker] Middle_Insert_Records consume id={}, target_table_id={}, monitored_field={}",
                        id, targetTableId, monitoredField);

                dailyNotifyMapper.updateProcessStatus(id);
                stats.dailySuccess++;
            } catch (Exception e) {
                log.error("[worker] Middle_Insert_Records consume failed id={}", id, e);
                if (id != null) {
                    dailyNotifyMapper.incrementRetryCount(id);
                    dailyNotifyMapper.updateProcessStatusFailed(id, safeFailReason(e));
                }
                stats.dailyFail++;
            }
        }
    }

    private void processInstallmentMiddleRecords(RunStats stats) {
        List<Map<String, Object>> rows = installmentNotifyMapper.getStatusNotLimit(safeBatchSize());
        if (rows == null || rows.isEmpty()) {
            return;
        }
        log.info("[worker] Middle_Installment_Records 本轮处理 {} 条", rows.size());
        for (Map<String, Object> row : rows) {
            Integer id = toInt(row.get("id"));
            try {
                if (id == null) {
                    throw new IllegalArgumentException("id 为空");
                }
                Integer retryCount = toInt(row.get("retry_count"));
                if (retryCount != null && retryCount >= safeMaxRetry()) {
                    stats.installmentSkippedByRetryLimit++;
                    log.warn("[worker] Middle_Installment_Records skip by retry limit id={}, retryCount={}, maxRetry={}",
                            id, retryCount, safeMaxRetry());
                    continue;
                }
                String targetTableId = toStr(row.get("target_table_id"));
                String monitoredField = toStr(row.get("monitored_field"));
                log.info("[worker] Middle_Installment_Records consume id={}, target_table_id={}, monitored_field={}",
                        id, targetTableId, monitoredField);

                installmentNotifyMapper.updateProcessStatus(id);
                stats.installmentSuccess++;
            } catch (Exception e) {
                log.error("[worker] Middle_Installment_Records consume failed id={}", id, e);
                if (id != null) {
                    installmentNotifyMapper.incrementRetryCount(id);
                    installmentNotifyMapper.updateProcessStatusFailed(id, safeFailReason(e));
                }
                stats.installmentFail++;
            }
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.valueOf(s);
    }

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String safeFailReason(Exception e) {
        String msg = e == null ? "unknown" : e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = e == null ? "unknown" : e.getClass().getSimpleName();
        }
        msg = msg.trim();
        return msg.length() <= 900 ? msg : msg.substring(0, 900);
    }

    private int safeBatchSize() {
        return batchSize <= 0 ? 200 : batchSize;
    }

    private int safeMaxRetry() {
        return maxRetry < 0 ? 0 : maxRetry;
    }

    private static String normalizeTable(String table) {
        if (table == null) {
            return "";
        }
        return table.trim().toLowerCase();
    }

    private static Integer nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static class RunStats {
        private long elapsedMs;
        private int interestSuccess;
        private int interestFail;
        private int interestSkippedByRetryLimit;
        private int dailySuccess;
        private int dailyFail;
        private int dailySkippedByRetryLimit;
        private int installmentSuccess;
        private int installmentFail;
        private int installmentSkippedByRetryLimit;

        static RunStats empty() {
            return new RunStats();
        }

        Map<String, Object> toMap() {
            return mapOf(
                    "elapsedMs", elapsedMs,
                    "interestSuccess", interestSuccess,
                    "interestFail", interestFail,
                    "interestSkippedByRetryLimit", interestSkippedByRetryLimit,
                    "dailySuccess", dailySuccess,
                    "dailyFail", dailyFail,
                    "dailySkippedByRetryLimit", dailySkippedByRetryLimit,
                    "installmentSuccess", installmentSuccess,
                    "installmentFail", installmentFail,
                    "installmentSkippedByRetryLimit", installmentSkippedByRetryLimit
            );
        }
    }
}

