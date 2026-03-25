package com.SpringbootTZ.FACT.Service.installmentPayment;

import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentNotifyMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import com.SpringbootTZ.FACT.Mapper.interestMapper;
import com.SpringbootTZ.FACT.Mapper.seeyonMapper;
import com.SpringbootTZ.FACT.Service.DailyPayment.DailyNotifyService;
import com.SpringbootTZ.FACT.Service.SumDataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对分期的任务表进行处理，出现下柜和还本的时候会重新计算内容更新到表里
 * 
 * 定时任务每5分钟去查询一次中间表，然后把对中间表进行处理
 * 获取中间表的记录id，这个是明细表的id
 * 然后先判断一下这个主表 formmain_0039的
 * field0013 DECIMAL 20 还本模式 下拉 NUMERIC(19,0)
 * field0014 DECIMAL 20 付息模式 下拉 NUMERIC(19,0）
 *
 * 因为触发器我做了判断，只会记录主表付息模式非分期付息的记录，分期付息的利息计算公式是不固定的我们就不管了
 *
 * 然后你定时5分钟去处理中间表有任务的记录，记得分类，目前我只写了"按季付息"的公式，所以你就在现有的日期都只处理按季付息的，其他的等我完善逻辑再做。
 * formson_0043
 * field0027 DECIMAL 20 下柜资金 文本 NUMERIC(20,6)
 * field0029 DECIMAL 20 计划还本 文本 NUMERIC(20,2)
 * field0030 DECIMAL 20 模拟付息 文本 NUMERIC(20,10)
 */
@Service
public class TotalNotifyService {

    private static final Logger log = LoggerFactory.getLogger(TotalNotifyService.class);

    private final InstallmentNotifyMapper installmentNotifyMapper;
    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final DailyNotifyService dailyNotifyService;
    private final InterestPaymentService interestPaymentService;
    private final QuarterlyInterestService quarterlyInterestService;
    private final MonthlyInterestService monthlyInterestService;
    private final AnnualInterestService annualInterestService;
    private final SemiAnnualInterestService semiAnnualInterestService;
    private final seeyonMapper seeyonMapper;
    private final interestMapper interestMapper;
    private final SumDataUtil sumDataUtil;
    private final InterestCalculationUtil interestCalculationUtil;

    @Autowired
    public TotalNotifyService(InstallmentNotifyMapper installmentNotifyMapper,
                             InstallmentPaymentMapper installmentPaymentMapper,
                             DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
                             DailyNotifyService dailyNotifyService,
                             InterestPaymentService interestPaymentService,
                             QuarterlyInterestService quarterlyInterestService,
                             MonthlyInterestService monthlyInterestService,
                             AnnualInterestService annualInterestService,
                             SemiAnnualInterestService semiAnnualInterestService,
                             seeyonMapper seeyonMapper,
                             interestMapper interestMapper,
                             SumDataUtil sumDataUtil,
                             InterestCalculationUtil interestCalculationUtil) {
        this.installmentNotifyMapper = installmentNotifyMapper;
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.dailyNotifyService = dailyNotifyService;
        this.interestPaymentService = interestPaymentService;
        this.quarterlyInterestService = quarterlyInterestService;
        this.monthlyInterestService = monthlyInterestService;
        this.annualInterestService = annualInterestService;
        this.semiAnnualInterestService = semiAnnualInterestService;
        this.seeyonMapper = seeyonMapper;
        this.interestMapper = interestMapper;
        this.sumDataUtil = sumDataUtil;
        this.interestCalculationUtil = interestCalculationUtil;
    }

    /** 批量更新时每批最大ID数量，避免SQL Server的IN子句参数限制(2100) */
    private static final int BATCH_UPDATE_SIZE = 500;

    /**
     * 处理分期还款计划表的中间表记录
     * 优化：按主表ID合并任务，同一主表的多条任务（如批量导入计划还本）只执行一次按日表+分期表计算，
     * 避免重复计算导致处理时间过长。
     */
    public synchronized void processMiddleTableRecords() {
        try {
            // 联表查询待处理记录，获取主表ID用于合并
            List<Map<String, Object>> records = installmentNotifyMapper.getPendingRecordsWithMainTableId();

            if (records == null || records.isEmpty()) {
                processOrphanRecords();
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
                    // 执行分期表计算（每主表一次）
                    processInstallmentTableByMainTableId(mainTableId);
                    batchUpdateProcessStatus(ids);
                    log.info("主表处理成功，已标记 {} 条任务完成", ids.size());
                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && (errorMessage.contains("未找到主表信息")
                            || errorMessage.contains("未找到明细表ID对应的主表ID"))) {
                        log.warn("主表已被删除，批量标记为已处理，mainTableId: {}, 共 {} 条", mainTableId, ids.size());
                        batchUpdateProcessStatus(ids);
                    } else {
                        log.error("主表处理失败，mainTableId: {}, 共 {} 条任务", mainTableId, ids.size(), e);
                        String failReason = errorMessage != null ? errorMessage : "处理失败: " + e.getClass().getSimpleName();
                        if (failReason.length() > 1000) {
                            failReason = failReason.substring(0, 1000);
                        }
                        batchUpdateProcessStatusFailed(ids, failReason);
                        batchIncrementRetryCount(ids);
                    }
                }
            }

            log.info("分期还款中间表扫描完成，共处理 {} 个主表", mainTableToIds.size());

        } catch (Exception e) {
            log.error("扫描分期还款中间表时发生错误", e);
        }
    }

    /**
     * 处理无法联表的主表记录（明细可能已删除），逐条标记为已处理避免堆积
     */
    private void processOrphanRecords() {
        List<Map<String, Object>> orphans = installmentNotifyMapper.getStatusNot();
        if (orphans == null || orphans.isEmpty()) {
            log.debug("未找到待处理的记录");
            return;
        }
        for (Map<String, Object> r : orphans) {
            Integer id = (Integer) r.get("id");
            String targetTableId = (String) r.get("target_table_id");
            if (id == null) continue;
            try {
                String mainTableId = installmentPaymentMapper.getMainTableIdByDetailTableId(targetTableId);
                if (mainTableId == null || mainTableId.trim().isEmpty()) {
                    log.info("明细已删除，标记为已处理，id: {}, target_table_id: {}", id, targetTableId);
                    installmentNotifyMapper.updateProcessStatus(id);
                }
            } catch (Exception e) {
                log.debug("无法解析主表，跳过孤儿记录 id: {}", id);
            }
        }
    }

    private void batchUpdateProcessStatus(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            installmentNotifyMapper.batchUpdateProcessStatus(ids.subList(i, end));
        }
    }

    private void batchUpdateProcessStatusFailed(List<Integer> ids, String failReason) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            installmentNotifyMapper.batchUpdateProcessStatusFailed(ids.subList(i, end), failReason);
        }
    }

    private void batchIncrementRetryCount(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i += BATCH_UPDATE_SIZE) {
            int end = Math.min(i + BATCH_UPDATE_SIZE, ids.size());
            installmentNotifyMapper.batchIncrementRetryCount(ids.subList(i, end));
        }
    }

    /**
     * 根据主表ID处理分期表（用于合并任务后的单次计算）
     * 
     * @param mainTableId 主表ID（formmain_0039）
     */
    private void processInstallmentTableByMainTableId(String mainTableId) {
        try {
            log.info("开始处理分期表主表，mainTableId: {}", mainTableId);

            // 1. 获取主表信息，检查"是否添加明细表"字段和付息模式
            Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableById(mainTableId);
            if (mainTableInfo == null) {
                throw new RuntimeException("未找到主表信息，main_table_id: " + mainTableId);
            }

            // field0067 是否属于IRR=是：不添加日期、不计算付息，将是否添加明细表置为"是"后直接跳过（最优先）
            Object field0067Obj = mainTableInfo.get("field0067");
            if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                if ("是".equals(irrShowValue)) {
                    log.info("是否属于IRR=是，跳过分期表日期添加与付息计算，将是否添加明细表置为\"是\"，mainTableId: {}", mainTableId);
                    installmentPaymentMapper.updateField0043(mainTableId, "是");
                    return;
                }
            }

            // field0043 是否添加明细表
            String isAddDetail = mainTableInfo.get("field0043") != null
                    ? mainTableInfo.get("field0043").toString()
                    : null;

            // 3. 获取付息模式（枚举ID）
            Object interestModeIdObj = mainTableInfo.get("field0014"); // 付息模式
            if (interestModeIdObj == null) {
                throw new RuntimeException("付息模式为空，main_table_id: " + mainTableId);
            }

            // 通过枚举ID获取中文显示名
            String interestMode = seeyonMapper.getEnumValue1(interestModeIdObj.toString());
            if (interestMode == null) {
                throw new RuntimeException("获取付息模式枚举值失败，main_table_id: " + mainTableId
                        + ", interestModeId: " + interestModeIdObj);
            }


            log.info("获取到付息模式: {}, 主表ID: {}", interestMode, mainTableId);

            // 4. 根据付息模式选择对应的计算服务
            // 目前支持"按季付息"、"按月付息"、"按年付息"和"按半年付息"
            if (!"按季付息".equals(interestMode) && !"按月付息".equals(interestMode) && !"按年付息".equals(interestMode)
                    && !"按半年付息".equals(interestMode)) {
                log.info("当前付息模式为 {}，暂不支持，跳过处理，主表ID: {}", interestMode, mainTableId);
                return;
            }

            // 5. 如果"是否添加明细表"字段为null，先调用InterestPaymentService加载日期
            // InterestPaymentService会根据付息模式生成对应的日期（按季付息就是季度日期：3、6、9、12月）
            if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                log.info("检测到'是否添加明细表'字段为null，开始加载日期，主表ID: {}, 付息模式: {}", mainTableId, interestMode);
                // InterestPaymentService.monitorMainTableAndAddDetailRecords() 会根据付息模式生成对应的日期
                // 按季付息会调用 generateQuarterlyPaymentDatesFixed() 生成季度日期
                interestPaymentService.monitorMainTableAndAddDetailRecords();
                log.info("日期加载完成，主表ID: {}", mainTableId);
            }

            // 6. 获取所有有日期的明细表记录（这些日期是由InterestPaymentService根据付息模式生成的）
            // 按季付息就是季度日期，不是每一天（获取所有的明细表日期）
            List<Map<String, Object>> detailRecords = installmentPaymentMapper
                    .getDetailRecordsWithDates(mainTableId);
            if (detailRecords == null || detailRecords.isEmpty()) {
                log.warn("未找到有日期的明细表记录，主表ID: {}", mainTableId);
                return;
            }

            log.info("找到 {} 条有日期的明细表记录，开始重新计算付息，主表ID: {}, 付息模式: {}", detailRecords.size(),
                    mainTableId, interestMode);


            // 7. 遍历每条记录，根据付息模式调用对应的计算方法
            for (Map<String, Object> detailRecord : detailRecords) {
                String dateStr = detailRecord.get("date_str") != null ? detailRecord.get("date_str").toString() : null;
                if (dateStr == null || dateStr.trim().isEmpty()) {
                    log.warn("明细表记录日期为空，跳过，主表ID: {}", mainTableId);
                    continue;
                }

                try {
                    log.debug("开始计算付息，主表ID: {}, 日期: {}, 付息模式: {}", mainTableId, dateStr, interestMode);

                    // 根据付息模式调用对应的计算方法
                    if ("按季付息".equals(interestMode)) {
                        quarterlyInterestService.calculateQuarterlyInterest(mainTableId, dateStr);
                    } else if ("按月付息".equals(interestMode)) {
                        monthlyInterestService.calculateMonthlyInterest(mainTableId, dateStr);
                    } else if ("按年付息".equals(interestMode)) {
                        annualInterestService.calculateAnnualInterest(mainTableId, dateStr);
                    } else if ("按半年付息".equals(interestMode)) {
                        semiAnnualInterestService.calculateSemiAnnualInterest(mainTableId, dateStr);
                    }

                    log.debug("完成计算付息，主表ID: {}, 日期: {}, 付息模式: {}", mainTableId, dateStr, interestMode);
                } catch (Exception e) {
                    log.error("计算付息失败，主表ID: {}, 日期: {}, 付息模式: {}", mainTableId, dateStr, interestMode, e);
                    // 继续处理下一条记录，不中断整个流程
                }
            }

            // 8. 更新主表的贷款余额合计（field0035）
            // 自动找到「今天或之后的第一个还款日」，并把那一天对应的贷款余额，显示在主表的贷款余额合计字段里
            updateMainTableLoanBalanceSummary(mainTableId);

            // 9. 更新主表的最新利率（field0012）和利率生效时间（field0042）
            updateMainTableInterestRate(mainTableId);

            // 10. 更新已发生的实际付息（field0033）
            // 如果今天的日期是默认还款日，那么今天的模拟付息要更新到实际付息（field0033）
            // 因为实施会把用户的还本也写入计划还本，所以历史已发生的数据还本（field0032）会一直等于计划还本（field0029）
            // 这样已发生的模拟付息（field0030）就是真正的付息（用户说默认到期就付息）
            updateActualInterestForDefaultPaymentDays(mainTableId);

            // 11. 更新分期还款计划表的所有合计字段
            // 注意：合计字段包含“实际付息合计（field0039）”，必须在实际付息写入 field0033 之后再汇总
            sumDataUtil.updateInstallmentRepaymentPlanSummary(mainTableId);

            log.info("分期表主表处理完成，mainTableId: {}, 付息模式: {}, 处理了 {} 条日期记录", mainTableId, interestMode, detailRecords.size());

        } catch (Exception e) {
            log.error("处理分期表主表时发生错误，mainTableId: {}", mainTableId, e);
            throw e;
        }
    }

    /**
     * 更新主表的贷款余额合计（field0035）
     * 自动找到「今天或之后的第一个还款日」，并把那一天对应的贷款余额，显示在主表的贷款余额合计字段里。
     * 如果没有未来的还款日，就显示 0。
     * 
     * @param formmain_id 主表ID
     */
    private void updateMainTableLoanBalanceSummary(String formmain_id) {
        try {
            log.info("开始更新主表贷款余额合计，formmain_id: {}", formmain_id);

            // 1. 获取当前日期（格式：yyyy-MM-dd）
            String currentDate = LocalDate.now().toString();
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

            // 1. 获取主表信息，获取单据编号（流水号）
            Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableInfoById(formmain_id);
            if (mainTableInfo == null) {
                log.error("未找到主表信息，formmain_id: {}", formmain_id);
                return;
            }

            String serialNumber = mainTableInfo.get("field0001") != null ? mainTableInfo.get("field0001").toString()
                    : null;
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("主表单据编号为空，无法更新利率，formmain_id: {}", formmain_id);
                return;
            }

            // 2. 从利率表查询最新的利率和生效时间
            Map<String, Object> interestInfo = interestMapper.getInterestBySerialNumber(serialNumber);
            if (interestInfo == null || interestInfo.isEmpty()) {
                log.warn("未找到流水号对应的利率信息，serialNumber: {}", serialNumber);
                return;
            }

            String interestRate = interestInfo.get("利率") != null ? interestInfo.get("利率").toString() : null;
            Object effectiveDateObj = interestInfo.get("生效时间");

            if (interestRate == null || interestRate.trim().isEmpty()) {
                log.warn("利率为空，无法更新，serialNumber: {}", serialNumber);
                return;
            }


            // 3. 格式化生效时间
            String effectiveDate = null;
            if (effectiveDateObj != null) {
                effectiveDate = effectiveDateObj.toString();
                // 如果是日期时间格式，可能需要格式化处理
                if (effectiveDate.contains(" ")) {
                    effectiveDate = effectiveDate.split(" ")[0]; // 只取日期部分
                }
            }

            // 4. 更新主表的最新利率和利率生效时间
            installmentPaymentMapper.updateMainTableInterestRate(formmain_id, interestRate, effectiveDate);
            log.info("主表最新利率更新完成，formmain_id: {}, 利率: {}, 生效时间: {}", formmain_id, interestRate,
                    effectiveDate);

        } catch (Exception e) {
            log.error("更新主表最新利率失败，formmain_id: {}", formmain_id, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 更新默认还款日的实际付息
     * 如果某条记录的日期是默认还款日（field0047 = "是"）且日期<=今天（已发生），
     * 则将模拟付息（field0030）的值更新到实际付息（field0033）
     * 
     * @param formmain_id 主表ID
     */
    private void updateActualInterestForDefaultPaymentDays(String formmain_id) {
        try {
            log.info("开始更新默认还款日的实际付息，formmain_id: {}", formmain_id);

            // 1. 获取当前日期（格式：yyyy-MM-dd）
            String currentDate = LocalDate.now().toString();
            log.debug("当前日期: {}", currentDate);

            // 2. 获取所有明细记录（包括所有字段）
            List<Map<String, Object>> allDetailRecords = installmentPaymentMapper.getAllPaymentDates(formmain_id);
            if (allDetailRecords == null || allDetailRecords.isEmpty()) {
                log.debug("未找到明细记录，formmain_id: {}", formmain_id);
                return;
            }

            log.debug("找到 {} 条明细记录，开始检查默认还款日，formmain_id: {}", allDetailRecords.size(), formmain_id);

            int updateCount = 0;

            // 3. 遍历每条记录
            for (Map<String, Object> record : allDetailRecords) {
                try {
                    // 获取日期
                    Object dateObj = record.get("field0026");
                    if (dateObj == null) {
                        continue;
                    }

                    String dateStr = dateObj.toString();
                    if (dateStr == null || dateStr.trim().isEmpty()) {
                        continue;
                    }

                    // 解析日期（使用工具类方法，处理各种日期格式）
                    LocalDate recordDate = interestCalculationUtil.parseDate(dateStr);
                    if (recordDate == null) {
                        log.debug("日期解析失败，跳过，dateStr: {}, formmain_id: {}", dateStr, formmain_id);
                        continue;
                    }

                    // 检查日期是否<=今天（已发生）
                    LocalDate today = LocalDate.now();

                    if (recordDate.isAfter(today)) {
                        // 未来日期，跳过
                        continue;
                    }

                    // 获取模拟付息（field0030）
                    Object simulatedInterestObj = record.get("field0030");
                    BigDecimal simulatedInterestValue = null;
                    if (simulatedInterestObj != null) {
                        String raw = simulatedInterestObj.toString();
                        if (raw != null && !raw.trim().isEmpty() && !"null".equals(raw)) {
                            try {
                                simulatedInterestValue = new BigDecimal(raw.trim());
                            } catch (NumberFormatException e) {
                                log.warn("模拟付息无法解析为数值，跳过，raw: {}, formmain_id: {}", raw, formmain_id);
                            }
                        }
                    }

                    if (simulatedInterestValue == null) {
                        log.debug("模拟付息为空，跳过更新，日期: {}, formmain_id: {}", recordDate, formmain_id);
                        continue;
                    }


                    // 使用解析后的日期对象转换为标准格式（yyyy-MM-dd）
                    String standardDateStr = recordDate.toString();

                    // 更新实际付息（field0033）为模拟付息（field0030）的值
                    // 规则：只要该日期 <= 今天（已发生）且该行存在模拟付息值，就视为“已发生的付息日/到期日”并写入实际付息。
                    // 不再依赖 field0047（是否默认还款日），避免因为标记未维护导致实际付息不落库。
                    // 使用 BigDecimal 传入，避免 nvarchar 与 numeric 类型转换错误。
                    installmentPaymentMapper.updateActualInterest(formmain_id, standardDateStr, simulatedInterestValue);
                    updateCount++;


                    log.debug("已更新实际付息，日期: {}, 模拟付息: {}, formmain_id: {}", standardDateStr, simulatedInterestValue,
                            formmain_id);

                } catch (Exception e) {

                    log.error("处理单条明细记录时发生错误，formmain_id: {}", formmain_id, e);
                    // 继续处理下一条记录，不中断整个流程
                }
            }

            log.info("默认还款日实际付息更新完成，共更新 {} 条记录，formmain_id: {}", updateCount, formmain_id);

        } catch (Exception e) {
            log.error("更新默认还款日实际付息失败，formmain_id: {}", formmain_id, e);
            // 不抛出异常，避免影响主流程
        }
    }
}
