package com.SpringbootTZ.FACT.Service.installmentPayment;

import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * 按月付息计息服务
 * 核心规则：每月20号（默认还款日）计算付息并显示，其他日期付息列留空
 * 最后一日不管是不是付息日都要计算写入
 */
@Service
public class MonthlyInterestService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyInterestService.class);

    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final InterestCalculationUtil interestCalculationUtil;
    private final DailyInterestAccumulationUtil dailyInterestAccumulationUtil;

    @Autowired
    public MonthlyInterestService(InstallmentPaymentMapper installmentPaymentMapper,
                                  InterestCalculationUtil interestCalculationUtil,
                                  DailyInterestAccumulationUtil dailyInterestAccumulationUtil) {
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.interestCalculationUtil = interestCalculationUtil;
        this.dailyInterestAccumulationUtil = dailyInterestAccumulationUtil;
    }

    /**
     * 按月付息计息方法
     * 接收明细表ID（对应主表的formmain_id），计算该计息日的利息
     * 核心规则：每月20号（默认还款日）计算付息并显示，其他日期付息列留空
     * 最后一日不管是不是付息日都要计算写入
     * 
     * @param formmain_id        主表ID
     * @param currentPaymentDate 当前计息日（格式：yyyy-MM-dd）
     */
    public void calculateMonthlyInterest(String formmain_id, String currentPaymentDate) {
        try {
            log.debug("开始计算按月付息，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);

            // 1. 获取主表信息（包括单据编号、贷款开始日期、结束日期、最新利率、默认还款日）
            Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableInfoById(formmain_id);
            if (mainTableInfo == null) {
                log.error("未找到主表信息，formmain_id: {}", formmain_id);
                return;
            }

            String serialNumber = mainTableInfo.get("field0001") != null ? mainTableInfo.get("field0001").toString()
                    : null;
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.error("主表单据编号为空，formmain_id: {}", formmain_id);
                return;
            }

            // 2. 获取贷款结束日期和默认还款日
            String loanEndDateStr = mainTableInfo.get("field0010") != null ? mainTableInfo.get("field0010").toString()
                    : null;
            LocalDate loanEndDate = null;
            if (loanEndDateStr != null && !loanEndDateStr.trim().isEmpty()) {
                loanEndDate = interestCalculationUtil.parseDate(loanEndDateStr);
            }

            // 获取默认还款日（field0040）
            Integer defaultPaymentDay = null;
            Object defaultPaymentDayObj = mainTableInfo.get("field0040");
            if (defaultPaymentDayObj != null) {
                try {
                    defaultPaymentDay = Integer.parseInt(defaultPaymentDayObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("解析默认还款日失败，使用默认值20，formmain_id: {}", formmain_id);
                }
            }
            // 如果没有设置默认还款日，使用20号
            if (defaultPaymentDay == null) {
                defaultPaymentDay = 20;
            }

            // 3. 解析当前计息日
            LocalDate currentDate = interestCalculationUtil.parseDate(currentPaymentDate);
            if (currentDate == null) {
                log.error("日期解析失败，currentPaymentDate: {}", currentPaymentDate);
                return;
            }

            // 4. 先计算并更新贷款余额（所有日期都需要计算贷款余额）
            // 公式：当前日期贷款余额 = 上一个日期贷款余额 + 当前日期下柜资金 - 上一个日期的还本金额
            calculateAndUpdateLoanBalance(formmain_id, currentPaymentDate);

            // 5. 获取计算后的贷款余额
            String currentLoanBalanceStr = installmentPaymentMapper.getLoanBalanceByFormmainIdAndTime(formmain_id,
                    currentPaymentDate);
            if (currentLoanBalanceStr == null || currentLoanBalanceStr.trim().isEmpty()) {
                currentLoanBalanceStr = "0";
            }

            // 6. 判断当前日期是否是月度付息日（每月默认还款日）或贷款结束日期
            boolean isMonthlyDate = isMonthlyPaymentDate(currentDate, defaultPaymentDay);
            boolean isLoanEndDate = loanEndDate != null && currentDate.equals(loanEndDate);
            log.debug("判断付息条件，formmain_id: {}, currentPaymentDate: {}, 日期: {}, 是否为月度付息日: {}, 是否为贷款结束日期: {}",
                    formmain_id, currentPaymentDate, currentDate.getDayOfMonth(), isMonthlyDate, isLoanEndDate);

            // 7. 如果不是月度付息日且不是贷款结束日期，只更新贷款余额，付息字段留空
            if (!isMonthlyDate && !isLoanEndDate) {
                log.debug("非月度付息日且非贷款结束日期，只更新贷款余额，付息字段留空，formmain_id: {}, currentPaymentDate: {}, 贷款余额: {}",
                        formmain_id, currentPaymentDate, currentLoanBalanceStr);

                installmentPaymentMapper.updateQuarterlyData(formmain_id, currentPaymentDate, currentLoanBalanceStr,
                        null);
                log.debug("非付息日处理完成，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);
                return;
            }

            // 8. 是月度付息日或贷款结束日期，开始计算付息
            if (isMonthlyDate) {
                log.info("是月度付息日，开始计算付息，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);
            } else if (isLoanEndDate) {
                log.info("是贷款结束日期，开始计算付息，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);
            }

            // 9. 获取首次下柜日期（必须有下柜才会有利息）
            String firstDisbursementDate = installmentPaymentMapper.getFirstDisbursementDate(formmain_id);
            if (firstDisbursementDate == null || firstDisbursementDate.trim().isEmpty()) {
                log.debug("尚未下柜，付息为空，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);
                installmentPaymentMapper.updateQuarterlyData(formmain_id, currentPaymentDate, currentLoanBalanceStr,
                        null);
                return;
            }

            LocalDate firstDisbursement = interestCalculationUtil.parseDate(firstDisbursementDate);
            if (firstDisbursement == null) {
                log.error("首次下柜日期解析失败，firstDisbursementDate: {}", firstDisbursementDate);
                installmentPaymentMapper.updateQuarterlyData(formmain_id, currentPaymentDate, currentLoanBalanceStr,
                        null);
                return;
            }

            // 10. 如果当前日期早于或等于首次下柜日期，付息为空
            if (!currentDate.isAfter(firstDisbursement)) {
                log.debug("当前日期早于或等于首次下柜日期，付息为空，formmain_id: {}, currentPaymentDate: {}",
                        formmain_id, currentPaymentDate);
                installmentPaymentMapper.updateQuarterlyData(formmain_id, currentPaymentDate, currentLoanBalanceStr,
                        null);
                return;
            }

            // 11. 获取上一个月度付息日（用于计算付息区间）
            LocalDate previousMonthlyPaymentDate = getPreviousMonthlyPaymentDate(formmain_id, currentDate,
                    defaultPaymentDay);

            // 12. 计算付息区间（使用新的从按日表累加的方式）
            // 第一个区间：从首次下柜日期（包含）到当前还款日（包含）
            // 后续区间：从上次还款日的后一天（包含）到当前还款日（包含）
            // 例如：3月20日付息，如果上次还款日是2月20日，计算的是2月21日到3月20日的付息（包含2月21日和3月20日）
            LocalDate interestStartDate;
            if (previousMonthlyPaymentDate != null) {
                // 后续区间：从上次还款日的后一天开始（包含）
                interestStartDate = previousMonthlyPaymentDate.plusDays(1);
            } else {
                // 第一个区间：从首次下柜日期开始（包含）
                interestStartDate = firstDisbursement;
            }

            // 付息结束日 = 当前还款日（包含）
            LocalDate interestEndDate = currentDate;

            // 13. 分期表自算区间付息（不依赖按日表）
            double totalInterest = dailyInterestAccumulationUtil.accumulateInterestFromInstallmentTable(
                    serialNumber, formmain_id, interestStartDate, interestEndDate, firstDisbursement);

            // 若需与按日表完全一致可改用从按日表累加
            // double totalInterest = dailyInterestAccumulationUtil.accumulateInterestFromDailyTable(
            //         serialNumber, interestStartDate, interestEndDate);

            // 原逻辑保留（注释掉，以后可能需要）
            // double totalInterest =
            // interestCalculationUtil.calculateInterestWithPrincipalChanges(serialNumber,
            // formmain_id, interestStartDate, interestEndDate);

            // 14. 格式化利息为9位小数（四舍五入）
            String formattedInterest = BigDecimal.valueOf(totalInterest)
                    .setScale(9, RoundingMode.HALF_UP)
                    .toPlainString();

            // 15. 更新数据库（贷款余额已经在步骤4中更新，这里更新付息）
            installmentPaymentMapper.updateQuarterlyData(formmain_id, currentPaymentDate, currentLoanBalanceStr,
                    formattedInterest);

            log.info("完成按月付息计算，formmain_id: {}, currentPaymentDate: {}, 贷款余额: {}, 模拟付息: {}",
                    formmain_id, currentPaymentDate, currentLoanBalanceStr, formattedInterest);

        } catch (Exception e) {
            log.error("计算按月付息时发生错误，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate, e);
        }
    }

    /**
     * 计算并更新贷款余额
     * 公式：当前日期贷款余额 = 上一个日期贷款余额 + 当前日期下柜资金 - 上一个日期的还本金额
     * 
     * @param formmain_id        主表ID
     * @param currentPaymentDate 当前计息日（格式：yyyy-MM-dd）
     */
    private void calculateAndUpdateLoanBalance(String formmain_id, String currentPaymentDate) {
        try {
            log.debug("开始计算贷款余额，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate);

            // 1. 获取上一个计息日
            Map<String, Object> previousRecord = installmentPaymentMapper.getPreviousPaymentDate(formmain_id,
                    currentPaymentDate);

            // 2. 获取上一个日期的贷款余额
            double previousLoanBalance = 0.0;
            String previousDateStr = null;
            if (previousRecord != null && previousRecord.get("field0026") != null) {
                previousDateStr = previousRecord.get("field0026").toString();
                String previousLoanBalanceStr = installmentPaymentMapper.getLoanBalanceByFormmainIdAndTime(formmain_id,
                        previousDateStr);
                if (previousLoanBalanceStr != null && !previousLoanBalanceStr.trim().isEmpty()) {
                    try {
                        previousLoanBalance = Double.parseDouble(previousLoanBalanceStr);
                    } catch (NumberFormatException e) {
                        log.warn("解析上一个日期贷款余额失败，formmain_id: {}, previousDate: {}, value: {}",
                                formmain_id, previousDateStr, previousLoanBalanceStr);
                    }
                }
            }

            // 3. 获取当前日期的下柜资金
            String currentDisbursementStr = installmentPaymentMapper.getDisbursementByFormmainIdAndTime(formmain_id,
                    currentPaymentDate);
            log.debug("获取当前日期下柜资金，formmain_id: {}, currentDate: {}, 下柜资金字符串: {}",
                    formmain_id, currentPaymentDate, currentDisbursementStr);
            double currentDisbursement = 0.0;
            if (currentDisbursementStr != null && !currentDisbursementStr.trim().isEmpty()) {
                try {
                    currentDisbursement = Double.parseDouble(currentDisbursementStr);
                    log.debug("解析当前日期下柜资金成功，formmain_id: {}, currentDate: {}, 下柜资金: {}",
                            formmain_id, currentPaymentDate, currentDisbursement);
                } catch (NumberFormatException e) {
                    log.warn("解析当前日期下柜资金失败，formmain_id: {}, currentDate: {}, value: {}",
                            formmain_id, currentPaymentDate, currentDisbursementStr);
                }
            } else {
                log.debug("当前日期下柜资金为空或null，formmain_id: {}, currentDate: {}",
                        formmain_id, currentPaymentDate);
            }

            
            // 4. 获取上一个日期的还本金额
            double previousRepayment = 0.0;
            if (previousDateStr != null) {
                String previousRepaymentStr = installmentPaymentMapper.getRepaymentByFormmainIdAndTime(formmain_id,
                        previousDateStr);
                if (previousRepaymentStr != null && !previousRepaymentStr.trim().isEmpty()) {
                    try {
                        previousRepayment = Double.parseDouble(previousRepaymentStr);
                    } catch (NumberFormatException e) {
                        log.warn("解析上一个日期还本金额失败，formmain_id: {}, previousDate: {}, value: {}",
                                formmain_id, previousDateStr, previousRepaymentStr);
                    }
                }
            }

            // 5. 计算当前日期贷款余额
            // 公式：当前日期贷款余额 = 上一个日期贷款余额 + 当前日期下柜资金 - 上一个日期的还本金额
            double currentLoanBalance = previousLoanBalance + currentDisbursement - previousRepayment;

            // 确保贷款余额不为负数
            if (currentLoanBalance < 0) {
                log.warn("计算出的贷款余额为负数，设置为0，formmain_id: {}, currentDate: {}, 计算结果: {}",
                        formmain_id, currentPaymentDate, currentLoanBalance);
                currentLoanBalance = 0.0;
            }

            // 6. 格式化贷款余额（保留6位小数）
            String formattedLoanBalance = BigDecimal.valueOf(currentLoanBalance)
                    .setScale(6, RoundingMode.HALF_UP)
                    .toPlainString();

            // 7. 更新数据库
            log.debug("准备更新贷款余额到数据库，formmain_id: {}, currentDate: {}, 贷款余额: {}",
                    formmain_id, currentPaymentDate, formattedLoanBalance);
            installmentPaymentMapper.updateLoanBalanceOnly(formmain_id, currentPaymentDate, formattedLoanBalance);
            log.debug("贷款余额已更新到数据库，formmain_id: {}, currentDate: {}, 贷款余额: {}",
                    formmain_id, currentPaymentDate, formattedLoanBalance);

            log.debug(
                    "完成计算贷款余额，formmain_id: {}, currentDate: {}, 上一个日期贷款余额: {}, 当前日期下柜资金: {}, 上一个日期计划还本: {}, 当前日期贷款余额: {}",
                    formmain_id, currentPaymentDate, previousLoanBalance, currentDisbursement, previousRepayment,
                    formattedLoanBalance);

        } catch (Exception e) {
            log.error("计算贷款余额时发生错误，formmain_id: {}, currentPaymentDate: {}", formmain_id, currentPaymentDate, e);
            // 不抛出异常，避免影响付息计算流程
        }
    }

    /**
     * 判断是否是月度付息日（每月默认还款日）
     * 
     * @param date              日期
     * @param defaultPaymentDay 默认还款日（1-31）
     * @return true表示是月度付息日，false表示不是
     */
    private boolean isMonthlyPaymentDate(LocalDate date, int defaultPaymentDay) {
        if (date == null) {
            return false;
        }
        int day = date.getDayOfMonth();
        // 月度付息日：每月的默认还款日
        return day == defaultPaymentDay;
    }

    /**
     * 获取上一个月度付息日（每月默认还款日）
     * 从数据库中查找上一个月度付息日的记录
     * 
     * @param formmain_id       主表ID
     * @param currentDate       当前日期
     * @param defaultPaymentDay 默认还款日（1-31）
     * @return 上一个月度付息日，如果没有则返回null
     */
    private LocalDate getPreviousMonthlyPaymentDate(String formmain_id, LocalDate currentDate,
            int defaultPaymentDay) {
        try {
            // 获取所有历史记录
            java.util.List<Map<String, Object>> allRecords = installmentPaymentMapper.getAllPaymentDates(formmain_id);
            if (allRecords == null || allRecords.isEmpty()) {
                return null;
            }

            // 从后往前查找上一个月度付息日
            LocalDate previousMonthlyDate = null;
            for (int i = allRecords.size() - 1; i >= 0; i--) {
                Map<String, Object> record = allRecords.get(i);
                if (record.get("field0026") != null) {
                    String dateStr = record.get("field0026").toString();
                    LocalDate recordDate = interestCalculationUtil.parseDate(dateStr);
                    if (recordDate != null && recordDate.isBefore(currentDate)) {
                        if (isMonthlyPaymentDate(recordDate, defaultPaymentDay)) {
                            previousMonthlyDate = recordDate;
                            break;
                        }
                    }
                }
            }

            return previousMonthlyDate;
        } catch (Exception e) {
            log.error("获取上一个月度付息日失败，formmain_id: {}, currentDate: {}", formmain_id, currentDate, e);
            return null;
        }
    }
}
