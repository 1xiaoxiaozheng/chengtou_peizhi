package com.SpringbootTZ.FACT.Service.InterestRate;

import com.SpringbootTZ.FACT.Entity.InterestChangeNotify;
import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import com.SpringbootTZ.FACT.Mapper.InterestNotifyMapper;
import com.SpringbootTZ.FACT.Service.DailyPayment.DailyRepaymentPlanService;
import com.SpringbootTZ.FACT.Service.DailyPayment.UpdateDataToDaily;
import com.SpringbootTZ.FACT.Service.SumDataUtil;
import com.SpringbootTZ.FACT.Service.installmentPayment.InterestPaymentService;
import com.SpringbootTZ.FACT.Service.installmentPayment.QuarterlyInterestService;
import com.SpringbootTZ.FACT.Service.installmentPayment.MonthlyInterestService;
import com.SpringbootTZ.FACT.Service.installmentPayment.AnnualInterestService;
import com.SpringbootTZ.FACT.Service.installmentPayment.SemiAnnualInterestService;
import com.SpringbootTZ.FACT.Mapper.seeyonMapper;
import com.SpringbootTZ.FACT.Service.ClientNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理利率变更通知中间表
 * 当利率调整记录表（formmain_0032）新增记录时，触发器会插入到 interest_change_notify 中间表
 * 本服务扫描中间表，触发按日表和分期表的重新计算，并同步数据到利率表的明细表
 */
@Service
public class InterestNotifyService {

    private static final Logger log = LoggerFactory.getLogger(InterestNotifyService.class);

    private final InterestNotifyMapper interestNotifyMapper;
    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final DailyRepaymentPlanService dailyRepaymentPlanService;
    private final UpdateDataToDaily updateDataToDaily;
    private final SumDataUtil sumDataUtil;
    private final InterestPaymentService interestPaymentService;
    private final QuarterlyInterestService quarterlyInterestService;
    private final MonthlyInterestService monthlyInterestService;
    private final AnnualInterestService annualInterestService;
    private final SemiAnnualInterestService semiAnnualInterestService;
    private final seeyonMapper seeyonMapper;

    @Autowired
    public InterestNotifyService(InterestNotifyMapper interestNotifyMapper,
                                 DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
                                 InstallmentPaymentMapper installmentPaymentMapper,
                                 DailyRepaymentPlanService dailyRepaymentPlanService,
                                 UpdateDataToDaily updateDataToDaily,
                                 SumDataUtil sumDataUtil,
                                 InterestPaymentService interestPaymentService,
                                 QuarterlyInterestService quarterlyInterestService,
                                 MonthlyInterestService monthlyInterestService,
                                 AnnualInterestService annualInterestService,
                                 SemiAnnualInterestService semiAnnualInterestService,
                                 seeyonMapper seeyonMapper) {
        this.interestNotifyMapper = interestNotifyMapper;
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.dailyRepaymentPlanService = dailyRepaymentPlanService;
        this.updateDataToDaily = updateDataToDaily;
        this.sumDataUtil = sumDataUtil;
        this.interestPaymentService = interestPaymentService;
        this.quarterlyInterestService = quarterlyInterestService;
        this.monthlyInterestService = monthlyInterestService;
        this.annualInterestService = annualInterestService;
        this.semiAnnualInterestService = semiAnnualInterestService;
        this.seeyonMapper = seeyonMapper;
    }

    /**
     * 处理利率变更通知中间表记录
     * 定时扫描 interest_change_notify 表，处理状态为0的记录
     * 流程：
     * 1. 根据bill_no获取formmain_0029（按日表）和formmain_0039（分期表）的id
     * 2. 先触发按日表的重新计算（因为利率有新增内容）
     * 3. 按日表执行完后触发分期表的重新计算
     * 4. 分期表执行完后，把分期表（formson_0043）的内容同步到利率表的明细表（formson_0033）
     */
    public synchronized void processInterestChangeNotify() {
        try {
            // 查询状态为0的记录（待处理）
            List<InterestChangeNotify> records = interestNotifyMapper.getStatusNot();

            if (records == null || records.isEmpty()) {
                log.debug("未找到待处理的利率变更通知记录");
                return;
            }

            log.info("找到 {} 条待处理的利率变更通知记录", records.size());

            // 遍历处理每条记录
            for (InterestChangeNotify record : records) {
                Long id = record.getId();
                String billNo = record.getBillNo();
                Long sourceId = record.getSourceId(); // 利率表的主表ID（formmain_0032）

                if (id == null || billNo == null || billNo.trim().isEmpty()) {
                    log.warn("记录数据不完整，跳过处理，id: {}, bill_no: {}", id, billNo);
                    continue;
                }

                log.info("开始处理利率变更通知记录，id: {}, bill_no: {}, source_id: {}", id, billNo, sourceId);

                try {
                    // 处理单条记录
                    processSingleRecord(id, billNo, sourceId);

                    // 处理成功，更新状态为1
                    interestNotifyMapper.updateProcessStatus(id);
                    log.info("利率变更通知记录处理成功，id: {}", id);

                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    // 检查是否是"未找到主表信息"的情况（表被删除了）
                    if (errorMessage != null && (errorMessage.contains("未找到主表信息")
                            || errorMessage.contains("未找到按日表记录")
                            || errorMessage.contains("未找到分期表记录"))) {
                        log.warn("主表已被删除，无法处理，标记为已处理，id: {}, bill_no: {}, 错误: {}",
                                id, billNo, errorMessage);
                        // 标记为已处理（状态为1），不记录为失败
                        interestNotifyMapper.updateProcessStatus(id);
                        log.info("记录已标记为已处理，id: {}", id);
                    } else {
                        log.error("处理利率变更通知记录失败，id: {}, bill_no: {}", id, billNo, e);

                        // 处理失败，更新状态为2
                        interestNotifyMapper.updateProcessStatusFailed(id);
                    }
                }
            }

            log.info("利率变更通知中间表扫描完成，共处理 {} 条记录", records.size());

        } catch (Exception e) {
            log.error("扫描利率变更通知中间表时发生错误", e);
        }
    }

    /**
     * 处理单条利率变更通知记录
     * 
     * @param id       记录ID
     * @param billNo   单据编号（流水号）
     * @param sourceId 利率表的主表ID（formmain_0032）
     */
    private void processSingleRecord(Long id, String billNo, Long sourceId) {
        try {
            log.info("开始处理单条利率变更通知记录，id: {}, bill_no: {}, source_id: {}", id, billNo, sourceId);

            // 1. 根据bill_no获取formmain_0029（按日表）的id
            String dailyTableId = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(billNo);
            if (dailyTableId == null || dailyTableId.trim().isEmpty()) {
                log.warn("未找到按日表记录，bill_no: {}", billNo);
            } else {
                log.info("找到按日表ID: {}, bill_no: {}", dailyTableId, billNo);

                // 2. 先触发按日表的完整处理流程（因为利率有新增内容）
                log.info("开始触发按日表完整处理流程，bill_no: {}, daily_table_id: {}", billNo, dailyTableId);
                try {
                    processDailyTableRecord(dailyTableId, billNo);
                    log.info("按日表处理完成，bill_no: {}", billNo);
                } catch (Exception e) {
                    log.error("按日表处理失败，bill_no: {}", billNo, e);
                    throw e;
                }
            }

            // 3. 根据bill_no获取formmain_0039（分期表）的id
            String installmentTableId = installmentPaymentMapper.getMainTableIdBySerialNumber(billNo);
            if (installmentTableId == null || installmentTableId.trim().isEmpty()) {
                log.warn("未找到分期表记录，bill_no: {}", billNo);
            } else {
                log.info("找到分期表ID: {}, bill_no: {}", installmentTableId, billNo);

                // 4. 触发分期表的完整处理流程
                log.info("开始触发分期表完整处理流程，bill_no: {}, installment_table_id: {}", billNo, installmentTableId);
                try {
                    processInstallmentTableRecord(installmentTableId);
                    log.info("分期表处理完成，bill_no: {}", billNo);
                } catch (Exception e) {
                    log.error("分期表处理失败，bill_no: {}", billNo, e);
                    throw e;
                }

                // 5. 分期表执行完后，把分期表（formson_0043）的内容同步到利率表的明细表（formson_0033）
                log.info("开始同步分期表数据到利率表明细表，installment_table_id: {}, source_id: {}",
                        installmentTableId, sourceId);
                syncInstallmentDataToInterestTable(installmentTableId, sourceId);
                log.info("同步完成，installment_table_id: {}, source_id: {}", installmentTableId, sourceId);
            }

            log.info("单条利率变更通知记录处理完成，id: {}, bill_no: {}", id, billNo);

        } catch (Exception e) {
            log.error("处理单条利率变更通知记录时发生错误，id: {}, bill_no: {}", id, billNo, e);
            throw e; // 重新抛出异常，让调用方知道处理失败
        }
    }

    /**
     * 处理按日表的完整流程（参考DailyNotifyService.processSingleRecord）
     * 
     * @param dailyTableId 按日表主表ID（formmain_0029）
     * @param billNo       单据编号（流水号）
     */
    private void processDailyTableRecord(String dailyTableId, String billNo) {
        try {
            log.info("开始处理按日表记录，daily_table_id: {}, bill_no: {}", dailyTableId, billNo);

            // 1. 获取主表信息，检查"是否添加明细表"字段
            Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(dailyTableId);
            if (mainTableInfo == null) {
                throw new RuntimeException("未找到主表信息，daily_table_id: " + dailyTableId);
            }

            // field0067 是否属于IRR=是：不添加按日表日期、不进行付息计算，将是否添加明细表日期置为已添加后直接跳过（避免定时任务重复扫描）
            Object field0067Obj = mainTableInfo.get("field0067");
            if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                if ("是".equals(irrShowValue)) {
                    dailyRepaymentPlanMapper.updateIsAddDetailFlag(dailyTableId);
                    log.info("是否属于IRR=是，跳过按日表日期添加与付息计算，已将是否添加明细表日期置为已添加，daily_table_id: {}", dailyTableId);
                    return;
                }
            }

            // field0061 是否添加明细表日期
            String isAddDetail = mainTableInfo.get("field0061") != null
                    ? mainTableInfo.get("field0061").toString()
                    : null;

            // 2. 如果"是否添加明细表"字段为null，先调用fillDate加载日期
            if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                log.info("检测到'是否添加明细表'字段为null，开始加载日期，主表ID: {}", dailyTableId);
                updateDataToDaily.fillDate(billNo);
                log.info("日期加载完成，主表ID: {}", dailyTableId);
            }

            // 3. 更新最新的利率和利率生效日期
            log.info("开始更新利率，bill_no: {}", billNo);
            updateDataToDaily.updateInterestAndLoanSerialNo(billNo);
            log.info("利率更新完成，bill_no: {}", billNo);

            // 4. 计算利息和贷款余额
            log.info("开始计算利息和贷款余额，bill_no: {}", billNo);
            dailyRepaymentPlanService.calculateAndUpdateDailyData(billNo, updateDataToDaily);
            log.info("计算完成，bill_no: {}", billNo);

            // 5. 更新合计行的贷款余额
            log.info("开始更新合计行贷款余额，bill_no: {}", billNo);
            updateDataToDaily.updateSummaryRowLoanBalance(billNo);
            log.info("合计行贷款余额更新完成，bill_no: {}", billNo);

            // 6. 更新按日还款计划表的所有合计字段（field0034、field0036、field0037）
            log.info("开始更新按日还款计划表的所有合计字段，daily_table_id: {}", dailyTableId);
            sumDataUtil.updateDailyRepaymentPlanSummary(dailyTableId);
            log.info("按日还款计划表的所有合计字段更新完成，daily_table_id: {}", dailyTableId);

            log.info("按日表记录处理完成，daily_table_id: {}, bill_no: {}", dailyTableId, billNo);

        } catch (Exception e) {
            log.error("处理按日表记录时发生错误，daily_table_id: {}, bill_no: {}", dailyTableId, billNo, e);
            throw e;
        }
    }

    /**
     * 处理分期表的完整流程（参考TotalNotifyService.processSingleRecord）
     * 
     * @param installmentTableId 分期表主表ID（formmain_0039）
     */
    private void processInstallmentTableRecord(String installmentTableId) {
        try {
            log.info("开始处理分期表记录，installment_table_id: {}", installmentTableId);

            // 1. 获取主表信息，检查"是否添加明细表"字段和付息模式
            Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableById(installmentTableId);
            if (mainTableInfo == null) {
                throw new RuntimeException("未找到主表信息，installment_table_id: " + installmentTableId);
            }

            // field0067 是否属于IRR=是：不添加日期、不计算付息，将是否添加明细表置为"是"后直接跳过（最优先）
            Object field0067Obj = mainTableInfo.get("field0067");
            if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                if ("是".equals(irrShowValue)) {
                    log.info("是否属于IRR=是，跳过分期表日期添加与付息计算，将是否添加明细表置为\"是\"，installment_table_id: {}", installmentTableId);
                    installmentPaymentMapper.updateField0043(installmentTableId, "是");
                    return;
                }
            }

            // field0043 是否添加明细表
            String isAddDetail = mainTableInfo.get("field0043") != null
                    ? mainTableInfo.get("field0043").toString()
                    : null;

            // 2. 获取付息模式（枚举ID）
            Object interestModeIdObj = mainTableInfo.get("field0014"); // 付息模式
            if (interestModeIdObj == null) {
                throw new RuntimeException("付息模式为空，installment_table_id: " + installmentTableId);
            }

            // 通过枚举ID获取中文显示名
            String interestMode = seeyonMapper.getEnumValue1(interestModeIdObj.toString());
            if (interestMode == null) {
                throw new RuntimeException("获取付息模式枚举值失败，installment_table_id: " + installmentTableId
                        + ", interestModeId: " + interestModeIdObj);
            }

            log.info("获取到付息模式: {}, 主表ID: {}", interestMode, installmentTableId);

            // 3. 根据付息模式选择对应的计算服务
            if (!"按季付息".equals(interestMode) && !"按月付息".equals(interestMode) && !"按年付息".equals(interestMode)
                    && !"按半年付息".equals(interestMode)) {
                log.info("当前付息模式为 {}，暂不支持，跳过处理，主表ID: {}", interestMode, installmentTableId);
                return;
            }

            // 4. 如果"是否添加明细表"字段为null，先调用InterestPaymentService加载日期
            if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                log.info("检测到'是否添加明细表'字段为null，开始加载日期，主表ID: {}, 付息模式: {}", installmentTableId, interestMode);
                interestPaymentService.monitorMainTableAndAddDetailRecords();
                log.info("日期加载完成，主表ID: {}", installmentTableId);
            }

            // 5. 获取所有有日期的明细表记录
            List<Map<String, Object>> detailRecords = installmentPaymentMapper
                    .getDetailRecordsWithDates(installmentTableId);
            if (detailRecords == null || detailRecords.isEmpty()) {
                log.warn("未找到有日期的明细表记录，主表ID: {}", installmentTableId);
                return;
            }

            log.info("找到 {} 条有日期的明细表记录，开始重新计算付息，主表ID: {}, 付息模式: {}", detailRecords.size(),
                    installmentTableId, interestMode);

            // 6. 遍历每条记录，根据付息模式调用对应的计算方法
            for (Map<String, Object> detailRecord : detailRecords) {
                String dateStr = detailRecord.get("date_str") != null ? detailRecord.get("date_str").toString() : null;
                if (dateStr == null || dateStr.trim().isEmpty()) {
                    log.warn("明细表记录日期为空，跳过，主表ID: {}", installmentTableId);
                    continue;
                }

                try {
                    log.debug("开始计算付息，主表ID: {}, 日期: {}, 付息模式: {}", installmentTableId, dateStr, interestMode);

                    // 根据付息模式调用对应的计算方法
                    if ("按季付息".equals(interestMode)) {
                        quarterlyInterestService.calculateQuarterlyInterest(installmentTableId, dateStr);
                    } else if ("按月付息".equals(interestMode)) {
                        monthlyInterestService.calculateMonthlyInterest(installmentTableId, dateStr);
                    } else if ("按年付息".equals(interestMode)) {
                        annualInterestService.calculateAnnualInterest(installmentTableId, dateStr);
                    } else if ("按半年付息".equals(interestMode)) {
                        semiAnnualInterestService.calculateSemiAnnualInterest(installmentTableId, dateStr);
                    }

                    log.debug("完成计算付息，主表ID: {}, 日期: {}, 付息模式: {}", installmentTableId, dateStr, interestMode);
                } catch (Exception e) {
                    log.error("计算付息失败，主表ID: {}, 日期: {}, 付息模式: {}", installmentTableId, dateStr, interestMode, e);
                    // 继续处理下一条记录，不中断整个流程
                }
            }

            // 7. 更新主表的贷款余额合计（field0035）
            updateMainTableLoanBalanceSummary(installmentTableId);

            // 8. 更新主表的最新利率（field0012）和利率生效时间（field0042）
            updateMainTableInterestRate(installmentTableId);

            // 9. 更新分期还款计划表的所有合计字段（field0034、field0036、field0037）
            sumDataUtil.updateInstallmentRepaymentPlanSummary(installmentTableId);

            log.info("分期表记录处理完成，installment_table_id: {}, 付息模式: {}, 处理了 {} 条日期记录",
                    installmentTableId, interestMode, detailRecords.size());

        } catch (Exception e) {
            log.error("处理分期表记录时发生错误，installment_table_id: {}", installmentTableId, e);
            throw e;
        }
    }

    /**
     * 更新主表的贷款余额合计（field0035）
     * 自动找到「今天或之后的第一个还款日」，并把那一天对应的贷款余额，显示在主表的贷款余额合计字段里。
     * 
     * @param formmain_id 主表ID
     */
    private void updateMainTableLoanBalanceSummary(String formmain_id) {
        try {
            log.info("开始更新主表贷款余额合计，formmain_id: {}", formmain_id);

            // 1. 获取当前日期（格式：yyyy-MM-dd）
            String currentDate = java.time.LocalDate.now().toString();
            log.debug("当前日期: {}", currentDate);

            // 2. 查询今天或之后的第一个还款日的贷款余额
            String loanBalance = installmentPaymentMapper.getLoanBalanceForFirstFutureDate(formmain_id, currentDate);

            // 3. 如果没有找到未来的还款日，设置为0
            if (loanBalance == null || loanBalance.trim().isEmpty() || "null".equals(loanBalance)) {
                loanBalance = "0";
                log.info("未找到今天或之后的还款日，主表贷款余额合计设置为0，formmain_id: {}", formmain_id);
            } else {
                log.info("找到今天或之后的第一个还款日，贷款余额: {}，formmain_id: {}", loanBalance, formmain_id);
            }

            // 4. 更新主表的贷款余额合计（field0035）
            installmentPaymentMapper.updateMainTableLoanBalanceSummary(formmain_id, loanBalance);
            log.info("主表贷款余额合计更新完成，loanBalance: {}，formmain_id: {}", loanBalance, formmain_id);

        } catch (Exception e) {
            log.error("更新主表贷款余额合计失败，formmain_id: {}", formmain_id, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 更新主表的最新利率（field0012）和利率生效时间（field0042）
     * 从利率表中获取最新的利率和生效时间，更新到主表
     * 
     * @param formmain_id 主表ID
     */
    private void updateMainTableInterestRate(String formmain_id) {
        try {
            log.info("开始更新主表最新利率，formmain_id: {}", formmain_id);

            // 1. 获取主表的单据编号（流水号）
            String serialNumber = installmentPaymentMapper.getSerialNumberByFormmainId(formmain_id);
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("未找到单据编号，跳过更新利率，formmain_id: {}", formmain_id);
                return;
            }

            // 2. 从利率变更表获取最新利率和生效时间
            // 这里需要调用interestMapper的方法，但可能需要先注入interestMapper
            // 暂时先记录日志，后续可以根据实际需求实现
            log.info("需要从利率变更表获取最新利率，serialNumber: {}, formmain_id: {}", serialNumber, formmain_id);

            // TODO: 实现从利率变更表获取最新利率和生效时间的逻辑
            // 然后更新到主表的field0012和field0042

        } catch (Exception e) {
            log.error("更新主表最新利率失败，formmain_id: {}", formmain_id, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 同步分期表（formson_0043）的内容到利率表的明细表（formson_0033）
     * 
     * @param installmentTableId 分期表主表ID（formmain_0039）
     * @param interestTableId    利率表主表ID（formmain_0032）
     */
    public void syncInstallmentDataToInterestTable(String installmentTableId, Long interestTableId) {
        try {
            log.info("开始同步分期表数据到利率表明细表，installment_table_id: {}, interest_table_id: {}",
                    installmentTableId, interestTableId);

            // 1. 获取分期表的所有明细记录（formson_0043），包含所有字段
            List<Map<String, Object>> installmentRecords = installmentPaymentMapper
                    .getDetailTableByFormmainId(installmentTableId);

            if (installmentRecords == null || installmentRecords.isEmpty()) {
                log.warn("分期表没有明细记录，跳过同步，installment_table_id: {}", installmentTableId);
                return;
            }

            // 过滤出有日期的记录
            List<Map<String, Object>> validRecords = new ArrayList<>();
            for (Map<String, Object> record : installmentRecords) {
                if (record.get("field0026") != null) {
                    validRecords.add(record);
                }
            }

            if (validRecords.isEmpty()) {
                log.warn("分期表没有有效日期的明细记录，跳过同步，installment_table_id: {}", installmentTableId);
                return;
            }

            log.info("找到 {} 条分期明细记录，开始同步到利率表明细表", validRecords.size());

            // 2. 先删除利率表已有的明细记录
            int deletedCount = interestNotifyMapper.deleteInterestDetailRecords(interestTableId);
            log.info("已删除利率表明细表中的 {} 条旧记录，interest_table_id: {}", deletedCount, interestTableId);

            // 3. 为每条记录生成新的ID，并准备插入数据
            String startIdStr = ClientNumber.generateNumber();
            Long startId = Long.parseLong(startIdStr);

            List<Map<String, Object>> recordsToInsert = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> record : validRecords) {
                Map<String, Object> newRecord = new java.util.HashMap<>();

                // 生成新的ID
                newRecord.put("id", startId + index);

                // 复制字段（formson_0043 -> formson_0033）
                // field0025: 序号
                newRecord.put("field0025", record.get("field0025"));
                // field0026: 时间
                newRecord.put("field0026", record.get("field0026"));
                // field0027: 下柜资金
                newRecord.put("field0027", record.get("field0027"));
                // field0028: 贷款余额
                newRecord.put("field0028", record.get("field0028"));
                // field0029: 还本
                newRecord.put("field0029", record.get("field0029"));
                // field0030: 付息
                newRecord.put("field0030", record.get("field0030"));
                // field0032: 计划还本（formson_0043可能没有，设为null）
                newRecord.put("field0032", record.get("field0032"));
                // field0033: 模拟付息
                newRecord.put("field0033", record.get("field0033"));
                // sort: 排序号（使用field0025或序号）
                Object sortValue = record.get("sort");
                if (sortValue == null) {
                    sortValue = record.get("field0025");
                }
                newRecord.put("sort", sortValue);

                recordsToInsert.add(newRecord);
                index++;
            }

            // 4. 批量插入到利率表明细表（formson_0033）
            if (!recordsToInsert.isEmpty()) {
                // 分批插入，每批100条（SQL Server限制）
                int batchSize = 100;
                int totalSize = recordsToInsert.size();
                int insertedCount = 0;

                for (int i = 0; i < totalSize; i += batchSize) {
                    int endIndex = Math.min(i + batchSize, totalSize);
                    List<Map<String, Object>> batch = recordsToInsert.subList(i, endIndex);

                    int count = interestNotifyMapper.batchInsertInterestDetailRecords(batch, interestTableId);
                    insertedCount += count;
                    log.debug("已插入第 {} 批，共 {} 条记录，interest_table_id: {}",
                            (i / batchSize + 1), count, interestTableId);
                }

                log.info("同步完成，共同步 {} 条记录到利率表明细表，interest_table_id: {}",
                        insertedCount, interestTableId);
            } else {
                log.warn("没有可插入的记录，跳过同步");
            }

            // 5. 同步完明细表后，更新利率表主表的合计字段（field0034、field0035、field0036、field0037）
            log.info("开始更新利率表主表的合计字段，interest_table_id: {}", interestTableId);
            sumDataUtil.updateInterestTableSummary(interestTableId.toString());
            log.info("利率表主表的合计字段更新完成，interest_table_id: {}", interestTableId);

        } catch (Exception e) {
            log.error("同步分期表数据到利率表明细表失败，installment_table_id: {}, interest_table_id: {}",
                    installmentTableId, interestTableId, e);
            throw e;
        }
    }
}
