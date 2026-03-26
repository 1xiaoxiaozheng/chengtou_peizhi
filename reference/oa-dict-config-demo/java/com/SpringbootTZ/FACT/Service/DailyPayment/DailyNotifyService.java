package com.SpringbootTZ.FACT.Service.DailyPayment;

import com.SpringbootTZ.FACT.Mapper.DailyNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.OaDictConfigMapper;
import com.SpringbootTZ.FACT.Mapper.seeyonMapper;
import com.SpringbootTZ.FACT.Service.SumDataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用于按日还款计划表的中间表，处理新增还本和下柜的时候需要重新计算该表单的利息等内容。定时任务五分钟一次。
 *
 * <p>【演示版】中间表 / 按日主表明细表名 / 单据编号字段从 oa_dict_config 读取，由 {@link OaDictConfigMapper} 加载；
 * 替换主工程同名类时请同步替换 {@link DailyNotifyMapper}（reference/oa-dict-config-demo）。</p>
 */
@Service
public class DailyNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DailyNotifyService.class);

    private static final String[] OA_KEYS_FOR_DAILY_NOTIFY = {
            "middle.insert.table",
            "oa.plan.detail.table",
            "oa.plan.main.table",
            "oa.plan.serial.field"
    };

    private final DailyNotifyMapper dailyNotifyMapper;
    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final DailyRepaymentPlanService dailyRepaymentPlanService;
    private final UpdateDataToDaily updateDataToDaily;
    private final SumDataUtil sumDataUtil;
    private final seeyonMapper seeyonMapper;
    private final OaDictConfigMapper oaDictConfigMapper;

    @Autowired
    public DailyNotifyService(DailyNotifyMapper dailyNotifyMapper,
                             DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
                             DailyRepaymentPlanService dailyRepaymentPlanService,
                             UpdateDataToDaily updateDataToDaily,
                             SumDataUtil sumDataUtil,
                             seeyonMapper seeyonMapper,
                             OaDictConfigMapper oaDictConfigMapper) {
        this.dailyNotifyMapper = dailyNotifyMapper;
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.dailyRepaymentPlanService = dailyRepaymentPlanService;
        this.updateDataToDaily = updateDataToDaily;
        this.sumDataUtil = sumDataUtil;
        this.seeyonMapper = seeyonMapper;
        this.oaDictConfigMapper = oaDictConfigMapper;
    }

    /** 批量更新时每批最大ID数量，避免SQL Server的IN子句参数限制(2100) */
    private static final int BATCH_UPDATE_SIZE = 500;

    /**
     * 处理按日还款计划表的中间表记录
     * 优化：按主表ID合并任务，同一主表的多条任务（如批量导入7000行计划还本）只执行一次完整计算，
     * 避免重复计算导致处理时间过长。
     */
    public synchronized void processMiddleTableRecords() {
        try {
            Map<String, String> cfg = loadOaConfig();

            // 联表查询待处理记录，获取主表ID用于合并
            List<Map<String, Object>> records = dailyNotifyMapper.getPendingRecordsWithMainTableId(cfg);

            if (records == null || records.isEmpty()) {
                // 无联表结果的记录（如明细已被删除）用原接口查一次，逐条标记为已处理
                processOrphanRecords(cfg);
                return;
            }

            log.info("找到 {} 条待处理的记录，按主表合并后处理", records.size());

            // 按主表ID分组：同一主表的多个任务合并为一次处理
            Map<String, List<Integer>> mainTableToIds = records.stream()
                    .filter(r -> {
                        Object mid = r.get("formmain_id");
                        return mid != null && !mid.toString().trim().isEmpty();
                    })
                    .collect(Collectors.groupingBy(
                            r -> r.get("formmain_id").toString().trim(),
                            LinkedHashMap::new,
                            Collectors.mapping(r -> (Integer) r.get("id"), Collectors.toList())));

            log.info("合并为 {} 个主表待处理", mainTableToIds.size());

            for (Map.Entry<String, List<Integer>> entry : mainTableToIds.entrySet()) {
                String mainTableId = entry.getKey();
                List<Integer> ids = entry.getValue();

                log.info("处理主表 mainTableId: {}, 合并了 {} 条任务", mainTableId, ids.size());

                try {
                    processDailyTableByMainTableId(mainTableId);
                    batchUpdateProcessStatus(ids, cfg);
                    log.info("主表处理成功，已标记 {} 条任务完成", ids.size());
                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && (errorMessage.contains("未找到主表信息")
                            || errorMessage.contains("未找到主表ID对应的流水号"))) {
                        log.warn("主表已被删除，批量标记为已处理，mainTableId: {}, 共 {} 条", mainTableId, ids.size());
                        batchUpdateProcessStatus(ids, cfg);
                    } else {
                        log.error("主表处理失败，mainTableId: {}, 共 {} 条任务", mainTableId, ids.size(), e);
                        String failReason = errorMessage != null ? errorMessage : "处理失败: " + e.getClass().getSimpleName();
                        if (failReason.length() > 1000) {
                            failReason = failReason.substring(0, 1000);
                        }
                        batchUpdateProcessStatusFailed(ids, failReason, cfg);
                        batchIncrementRetryCount(ids, cfg);
                    }
                }
            }
            log.info("中间表扫描完成，共处理 {} 个主表", mainTableToIds.size());
        } catch (Exception e) {
            log.error("扫描中间表时发生错误", e);
        }
    }

    /**
     * 处理无法联表的主表记录（明细可能已删除），逐条标记为已处理避免堆积
     */
    private void processOrphanRecords(Map<String, String> cfg) {
        List<Map<String, Object>> orphans = dailyNotifyMapper.getStatusNot(cfg);
        if (orphans == null || orphans.isEmpty()) {
            log.debug("未找到待处理的记录");
            return;
        }
        for (Map<String, Object> r : orphans) {
            Integer id = (Integer) r.get("id");
            String targetTableId = (String) r.get("target_table_id");
            if (id == null) continue;
            try {
                String mainTableId = dailyRepaymentPlanMapper.getMainTableIdByDetailTableId(targetTableId);
                if (mainTableId == null || mainTableId.trim().isEmpty()) {
                    log.info("明细已删除，标记为已处理，id: {}, target_table_id: {}", id, targetTableId);
                    dailyNotifyMapper.updateProcessStatus(id, cfg);
                }
            } catch (Exception e) {
                log.debug("无法解析主表，跳过孤儿记录 id: {}", id);
            }
        }
    }

    /**
     * 供分期任务判断：当前在 oa_dict_config 所指向的按日链路下，仍有待处理中间表任务的贷款流水号集合。
     * 配置在页面修改后，下一次定时扫描即可读到新值（无缓存）。
     */
    public Set<String> getPendingDailySerialNumbersForInstallmentGate() {
        try {
            Map<String, String> cfg = loadOaConfig();
            List<String> list = dailyNotifyMapper.getPendingSerialNumbers(cfg);
            if (list == null || list.isEmpty()) {
                return Collections.emptySet();
            }
            return new HashSet<>(list);
        } catch (Exception e) {
            log.warn("读取按日待处理流水号失败（分期任务不因此中断）: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private Map<String, String> loadOaConfig() {
        Map<String, String> cfg = oaDictConfigMapper.selectAsMap();
        for (String k : OA_KEYS_FOR_DAILY_NOTIFY) {
            if (!cfg.containsKey(k) || cfg.get(k) == null || cfg.get(k).trim().isEmpty()) {
                throw new IllegalStateException("oa_dict_config 缺少或未配置 config_key: " + k);
            }
        }
        return cfg;
    }

    private void batchUpdateProcessStatus(List<Integer> ids, Map<String, String> cfg) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            dailyNotifyMapper.batchUpdateProcessStatus(ids.subList(i, end), cfg);
        }
    }

    private void batchUpdateProcessStatusFailed(List<Integer> ids, String failReason, Map<String, String> cfg) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            dailyNotifyMapper.batchUpdateProcessStatusFailed(ids.subList(i, end), failReason, cfg);
        }
    }

    private void batchIncrementRetryCount(List<Integer> ids, Map<String, String> cfg) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            dailyNotifyMapper.batchIncrementRetryCount(ids.subList(i, end), cfg);
        }
    }

    /**
     * 根据主表ID处理按日表（供外部调用，如分期表处理前需先刷新按日表）
     * 
     * @param mainTableId 主表ID（formmain_0029）
     */
    public void processDailyTableByMainTableId(String mainTableId) {
        try {
            if (mainTableId == null || mainTableId.trim().isEmpty()) {
                throw new RuntimeException("主表ID为空");
            }
            log.info("开始处理主表，mainTableId: {}", mainTableId);

            // 1. 根据主表ID获取流水号
            String loanSerialNo = dailyRepaymentPlanMapper.getSerialNumberByMainTableId(mainTableId);
            if (loanSerialNo == null || loanSerialNo.trim().isEmpty()) {
                throw new RuntimeException("未找到主表ID对应的流水号，main_table_id: " + mainTableId);
            }

            log.info("获取到流水号: {}, 主表ID: {}", loanSerialNo, mainTableId);

            // 2. 获取主表信息，检查"是否添加明细表"字段
            Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(mainTableId);
            if (mainTableInfo == null) {
                throw new RuntimeException("未找到主表信息，main_table_id: " + mainTableId);
            }

            // field0067 是否属于IRR：若枚举 SHOWVALUE 为"是"，则不添加按日表日期、不进行付息计算，将是否添加明细表日期置为"已添加"后直接跳过（避免定时任务重复扫描）
            Object field0067Obj = mainTableInfo.get("field0067");
            if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                if ("是".equals(irrShowValue)) {
                    dailyRepaymentPlanMapper.updateIsAddDetailFlag(mainTableId);
                    log.info("是否属于IRR=是，跳过按日表日期添加与付息计算，已将是否添加明细表日期置为已添加，mainTableId: {}", mainTableId);
                    return;
                }
            }

            // 新数据字典：field0061 是否添加明细表日期
            String isAddDetail = mainTableInfo.get("field0061") != null
                    ? mainTableInfo.get("field0061").toString()
                    : null;

            // 4. 如果"是否添加明细表"字段为null，先调用fillDate加载日期
            if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                log.info("检测到'是否添加明细表'字段为null，开始加载日期，主表ID: {}", mainTableId);
                updateDataToDaily.fillDate(loanSerialNo);
                log.info("日期加载完成，主表ID: {}", mainTableId);
            }

            // 5. 更新最新的利率和利率生效日期
            log.info("开始更新利率，loanSerialNo: {}", loanSerialNo);
            updateDataToDaily.updateInterestAndLoanSerialNo(loanSerialNo);
            log.info("利率更新完成，loanSerialNo: {}", loanSerialNo);

            // 6. 计算利息和贷款余额
            log.info("开始计算利息和贷款余额，loanSerialNo: {}", loanSerialNo);
            dailyRepaymentPlanService.calculateAndUpdateDailyData(loanSerialNo, updateDataToDaily);
            log.info("计算完成，loanSerialNo: {}", loanSerialNo);

            // 8. 更新合计行的贷款余额
            log.info("开始更新合计行贷款余额，loanSerialNo: {}", loanSerialNo);
            updateDataToDaily.updateSummaryRowLoanBalance(loanSerialNo);
            log.info("合计行贷款余额更新完成，loanSerialNo: {}", loanSerialNo);

            // 9. 更新按日还款计划表的所有合计字段（field0034、field0036、field0037）
            log.info("开始更新按日还款计划表的所有合计字段，mainTableId: {}", mainTableId);
            sumDataUtil.updateDailyRepaymentPlanSummary(mainTableId);
            log.info("按日还款计划表的所有合计字段更新完成，mainTableId: {}", mainTableId);

            log.info("主表处理完成，mainTableId: {}, loanSerialNo: {}", mainTableId, loanSerialNo);

        } catch (Exception e) {
            log.error("处理主表时发生错误，mainTableId: {}", mainTableId, e);
            throw e;
        }
    }



    /**
     * 从monitored_field中提取值
     * 格式：field0027=60000.000000
     * 返回：60000.000000
     * 
     * @param monitoredField 监听的字段内容
     * @return 提取的值，如果格式不正确返回null
     */
    private String extractValueFromMonitoredField(String monitoredField) {
        if (monitoredField == null || monitoredField.trim().isEmpty()) {
            return null;
        }

        // 查找等号的位置
        int equalsIndex = monitoredField.indexOf('=');
        if (equalsIndex == -1 || equalsIndex == monitoredField.length() - 1) {
            // 没有等号或等号在最后，格式不正确
            return null;
        }

        // 提取等号后面的值
        String value = monitoredField.substring(equalsIndex + 1).trim();
        return value.isEmpty() ? null : value;
    }
}