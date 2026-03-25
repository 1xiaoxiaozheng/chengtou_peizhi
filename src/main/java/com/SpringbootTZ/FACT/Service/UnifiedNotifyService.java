package com.SpringbootTZ.FACT.Service;

import com.SpringbootTZ.FACT.Mapper.InstallmentNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import com.SpringbootTZ.FACT.Mapper.InterestNotifyMapper;
import com.SpringbootTZ.FACT.Service.DailyPayment.DailyNotifyService;
import com.SpringbootTZ.FACT.Service.installmentPayment.TotalNotifyService;
import com.SpringbootTZ.FACT.Service.InterestRate.InterestNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一管理所有中间表处理任务的调度服务
 * 执行顺序：
 * 1. 利率变更通知任务（InterestNotifyService）- 利率变更会影响按日和分期表
 * 2. 按日还款计划表任务（DailyNotifyService）- 按日表先算才是最新值
 * 3. 分期还款计划表任务（TotalNotifyService）- 依赖按日表的最新值
 * 
 * 设计原则：
 * - 三个任务独立执行，互不影响（每个任务都有独立的异常处理）
 * - 即使某个任务失败，也不影响其他任务的执行
 * - 使用 fixedDelay 确保串行执行，避免并发冲突
 */
@Service
public class UnifiedNotifyService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedNotifyService.class);

    private final InterestNotifyService interestNotifyService;
    private final DailyNotifyService dailyNotifyService;
    private final TotalNotifyService totalNotifyService;
    private final InstallmentNotifyMapper installmentNotifyMapper;
    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final InterestNotifyMapper interestNotifyMapper;

    @Autowired
    public UnifiedNotifyService(InterestNotifyService interestNotifyService,
                                DailyNotifyService dailyNotifyService,
                                TotalNotifyService totalNotifyService,
                                InstallmentNotifyMapper installmentNotifyMapper,
                                InstallmentPaymentMapper installmentPaymentMapper,
                                InterestNotifyMapper interestNotifyMapper) {
        this.interestNotifyService = interestNotifyService;
        this.dailyNotifyService = dailyNotifyService;
        this.totalNotifyService = totalNotifyService;
        this.installmentNotifyMapper = installmentNotifyMapper;
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.interestNotifyMapper = interestNotifyMapper;
    }

    /**
     * 统一定时任务：每30秒扫描一次所有中间表
     * 执行顺序：
     * 1. 利率变更通知任务（InterestNotifyService）
     * 2. 按日还款计划表任务（DailyNotifyService）
     * 3. 分期还款计划表任务（TotalNotifyService）
     * 
     * 使用 fixedDelay 确保串行执行：执行完当前批次的任务后，等待30秒再进行下一次扫描
     */
    @Scheduled(fixedDelay = 30000) // 每30秒执行一次 (30 * 1000 = 30000毫秒)，从上一次执行完成开始计时
    public synchronized void processAllMiddleTableRecords() {
        try {
            log.debug("开始统一扫描所有中间表");

            // 1. 先执行利率变更通知任务（利率变更会影响按日和分期表）
            try {
                log.debug("开始执行利率变更通知任务");
                interestNotifyService.processInterestChangeNotify();
                log.debug("利率变更通知任务执行完成");
            } catch (Exception e) {
                log.error("执行利率变更通知任务时发生错误", e);
                // 继续执行后续任务，不中断整个流程
            }

            // 2. 再执行按日还款计划表的任务（按日表先算才是最新值）
            try {
                log.debug("开始执行按日还款计划表任务");
                dailyNotifyService.processMiddleTableRecords();
                log.debug("按日还款计划表任务执行完成");
            } catch (Exception e) {
                log.error("执行按日还款计划表任务时发生错误", e);
                // 继续执行分期任务，不中断整个流程
            }

            // 3. 最后执行分期还款计划表的任务（依赖按日表的最新值）
            Set<String> processedInstallmentTableIds = new HashSet<>();
            try {
                log.debug("开始执行分期还款计划表任务");

                // 获取所有待处理的记录（联表含主表ID），用于后续同步到利率表
                List<Map<String, Object>> installmentRecords = installmentNotifyMapper.getPendingRecordsWithMainTableId();
                if (installmentRecords != null && !installmentRecords.isEmpty()) {
                    for (Map<String, Object> record : installmentRecords) {
                        Object mid = record.get("formmain_id");
                        if (mid != null && !mid.toString().trim().isEmpty()) {
                            processedInstallmentTableIds.add(mid.toString().trim());
                        }
                    }
                }

                totalNotifyService.processMiddleTableRecords();
                log.debug("分期还款计划表任务执行完成");
            } catch (Exception e) {
                log.error("执行分期还款计划表任务时发生错误", e);
                // 不中断流程，错误已记录
            }

            // 4. 同步分期表数据到利率表（当利率中间表没有内容时，确保利率表与分期表保持一致）
            if (!processedInstallmentTableIds.isEmpty()) {
                try {
                    log.debug("开始同步分期表数据到利率表，共 {} 个分期表需要同步", processedInstallmentTableIds.size());
                    syncInstallmentToInterestTable(processedInstallmentTableIds);
                    log.debug("同步分期表数据到利率表完成");
                } catch (Exception e) {
                    log.error("同步分期表数据到利率表时发生错误", e);
                    // 不中断流程，错误已记录
                }
            }

            log.debug("统一扫描所有中间表完成");

        } catch (Exception e) {
            log.error("统一扫描所有中间表时发生错误", e);
        }
    }

    /**
     * 同步分期表数据到利率表
     * 当分期表有新的下柜或还本时，即使利率中间表没有内容，也需要同步到最新的利率表
     * 
     * @param installmentTableIds 分期表主表ID集合
     */
    private void syncInstallmentToInterestTable(Set<String> installmentTableIds) {
        for (String installmentTableId : installmentTableIds) {
            try {
                // 1. 获取分期表的单据编号（流水号）
                String billNo = installmentPaymentMapper.getSerialNumberByFormmainId(installmentTableId);
                if (billNo == null || billNo.trim().isEmpty()) {
                    log.warn("未找到分期表的单据编号，跳过同步，installment_table_id: {}", installmentTableId);
                    continue;
                }

                // 2. 根据bill_no查询最新的利率表主表ID（按生效时间降序，取第一条）
                Long interestTableId = interestNotifyMapper.getLatestInterestTableIdByBillNo(billNo);
                if (interestTableId == null) {
                    log.debug("未找到对应的利率表记录，跳过同步，bill_no: {}, installment_table_id: {}", billNo, installmentTableId);
                    continue;
                }

                log.info("开始同步分期表到利率表，installment_table_id: {}, interest_table_id: {}, bill_no: {}",
                        installmentTableId, interestTableId, billNo);

                // 3. 调用同步方法，将分期表数据同步到利率表
                interestNotifyService.syncInstallmentDataToInterestTable(installmentTableId, interestTableId);

                log.info("同步完成，installment_table_id: {}, interest_table_id: {}, bill_no: {}",
                        installmentTableId, interestTableId, billNo);

            } catch (Exception e) {
                log.error("同步分期表到利率表失败，installment_table_id: {}", installmentTableId, e);
                // 继续处理下一个，不中断整个流程
            }
        }
    }
}
