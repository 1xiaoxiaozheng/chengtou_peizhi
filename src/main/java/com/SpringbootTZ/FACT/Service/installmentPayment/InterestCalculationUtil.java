package com.SpringbootTZ.FACT.Service.installmentPayment;

import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import com.SpringbootTZ.FACT.Mapper.interestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 利息计算工具类
 * 提供公共的利息计算方法，供按季付息、按月付息等服务复用
 */
@Component
public class InterestCalculationUtil {

    private static final Logger log = LoggerFactory.getLogger(InterestCalculationUtil.class);

    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final interestMapper interestMapper;
    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;

    @Autowired
    public InterestCalculationUtil(InstallmentPaymentMapper installmentPaymentMapper,
                                   interestMapper interestMapper,
                                   DailyRepaymentPlanMapper dailyRepaymentPlanMapper) {
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.interestMapper = interestMapper;
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
    }

    /**
     * 解析日期字符串
     * 支持多种日期格式：yyyy-MM-dd、yyyy-MM-dd HH:mm:ss、yyyy/MM/dd 等
     *
     * @param dateStr 日期字符串
     * @return LocalDate 对象，解析失败返回 null
     */
    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            String cleanDateStr = dateStr.trim();
            if (cleanDateStr.contains(" ")) {
                cleanDateStr = cleanDateStr.split(" ")[0];
            }
            if (cleanDateStr.contains("T")) {
                cleanDateStr = cleanDateStr.split("T")[0];
            }
            if (cleanDateStr.contains(".")) {
                cleanDateStr = cleanDateStr.split("\\.")[0];
            }
            cleanDateStr = cleanDateStr.replace("/", "-");
            if (cleanDateStr.length() > 10) {
                cleanDateStr = cleanDateStr.substring(0, 10);
            }
            return LocalDate.parse(cleanDateStr);
        } catch (Exception e) {
            log.error("解析日期失败: " + dateStr + ", 错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析利率字符串，转换为double
     * 支持格式：
     * 1. 小数格式（如：.470000 或 0.47）- 直接使用
     * 2. 百分比格式（如：4.7% 或 4.7）- 转换为小数
     *
     * @param interestRateStr 利率字符串
     * @return 利率值（double），解析失败返回 0.0
     */
    public double parseInterestRate(String interestRateStr) {
        if (interestRateStr == null || interestRateStr.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String cleanRateStr = interestRateStr.trim();
            double rate = Double.parseDouble(cleanRateStr);
            // 利率已经是小数格式（如：.470000），直接使用
            return rate;
        } catch (Exception e) {
            log.error("解析利率失败: " + interestRateStr + ", 错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取期初默认利率
     * 查询指定日期之前（包含该日期）的最新利率变更记录
     * 如果没有利率变更记录，使用主表的默认利率
     *
     * @param serialNumber 单据编号/流水号
     * @param date         日期
     * @return 利率值（double）
     */
    public double getInitialRate(String serialNumber, LocalDate date) {
        try {
            // 查询该日期之前的最新利率
            List<Map<String, Object>> allRecords = interestMapper.getInterestChangesBySerialNumber(serialNumber);
            double rate = 0.0;
            for (Map<String, Object> record : allRecords) {
                String changeDateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                LocalDate changeDate = parseDate(changeDateStr);
                if (changeDate != null && !changeDate.isAfter(date)) {
                    String interestRateStr = record.get("interest_rate") != null
                            ? record.get("interest_rate").toString()
                            : "0";
                    rate = parseInterestRate(interestRateStr);
                }
            }
            // 如果没有找到，使用主表的默认利率
            if (rate == 0.0) {
                String defaultRate = interestMapper.getInterest(serialNumber);
                if (defaultRate != null && !defaultRate.trim().isEmpty()) {
                    rate = parseInterestRate(defaultRate);
                }
            }
            return rate;
        } catch (Exception e) {
            log.error("获取期初利率失败，serialNumber: {}, date: {}", serialNumber, date, e);
            return 0.0;
        }
    }

    /**
     * 获取指定日期的贷款余额
     * 先从按季付息表查询，如果找不到，则从按日表查询
     *
     * @param formmain_id 主表ID（按季付息表的主表ID）
     * @param date        日期
     * @return 贷款余额
     */
    public double getLoanBalanceAtDate(String formmain_id, LocalDate date) {
        try {
            // 1. 先从按季付息表查询
            String loanBalanceStr = installmentPaymentMapper.getLoanBalanceByFormmainIdAndTime(formmain_id,
                    date.toString());
            if (loanBalanceStr != null && !loanBalanceStr.trim().isEmpty()) {
                return Double.parseDouble(loanBalanceStr);
            }

            // 2. 如果按季付息表中找不到，从按日表查询
            // 2.1 通过formmain_id获取流水号
            String serialNumber = installmentPaymentMapper.getSerialNumberByFormmainId(formmain_id);
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("未找到流水号，formmain_id: {}", formmain_id);
                return 0.0;
            }

            // 2.2 通过流水号获取按日表的主表ID
            String dailyFormmainId = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(serialNumber);
            if (dailyFormmainId == null || dailyFormmainId.trim().isEmpty()) {
                log.debug("未找到按日表主表ID，流水号: {}, formmain_id: {}", serialNumber, formmain_id);
                return 0.0;
            }

            // 2.3 从按日表查询贷款余额
            String dailyLoanBalanceStr = dailyRepaymentPlanMapper.getLoanBalanceByFormmainIdAndTime(dailyFormmainId,
                    date.toString());
            if (dailyLoanBalanceStr != null && !dailyLoanBalanceStr.trim().isEmpty()) {
                double balance = Double.parseDouble(dailyLoanBalanceStr);
                log.debug("从按日表获取贷款余额，formmain_id: {}, date: {}, balance: {}", formmain_id, date, balance);
                return balance;
            }

            // 3. 如果都找不到，返回0.0
            log.debug("按季付息表和按日表都未找到贷款余额，formmain_id: {}, date: {}", formmain_id, date);
            return 0.0;
        } catch (Exception e) {
            log.warn("获取指定日期贷款余额失败，formmain_id: {}, date: {}", formmain_id, date, e);
            return 0.0;
        }
    }

    /**
     * 按本金变化和利率调整拆分区间计算付息
     * 付息计算区间：起始日期 → 结束日期前一天（算头不算尾）
     * 期间有本金变化（还本/下柜）或利率调整，按变化日期拆分区间，各区间利息累加 = 总付息
     *
     * @param serialNumber 单据编号
     * @param formmain_id  主表ID
     * @param startDate    计息起始日
     * @param endDate      计息结束日（算头不算尾，不包含此日期）
     * @return 总利息
     */
    public double calculateInterestWithPrincipalChanges(String serialNumber, String formmain_id,
            LocalDate startDate, LocalDate endDate) {
        try {
            if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
                log.warn("计息区间无效，startDate: {}, endDate: {}", startDate, endDate);
                return 0.0;
            }

            // 获取计息区间内的所有本金变化记录（下柜和还本）
            List<Map<String, Object>> principalChangeRecords = getPrincipalChangeRecords(formmain_id, startDate,
                    endDate);

            // 获取计息区间内的所有利率调整记录
            List<Map<String, Object>> interestChangeRecords = interestMapper
                    .getInterestChangesBySerialNumberAndDateRange(serialNumber,
                            startDate.toString(),
                            endDate.toString());

            // 合并所有变化日期（本金变化和利率调整），按日期排序
            // 同时保存利率变更日期对应的新利率
            List<LocalDate> changeDates = new ArrayList<>();
            Map<LocalDate, Double> interestRateMap = new HashMap<>();

            for (Map<String, Object> record : principalChangeRecords) {
                String dateStr = record.get("field0026") != null ? record.get("field0026").toString() : "";
                LocalDate date = parseDate(dateStr);
                if (date != null && !date.isBefore(startDate) && !date.isAfter(endDate)) {
                    if (!changeDates.contains(date)) {
                        changeDates.add(date);
                    }
                }
            }
            for (Map<String, Object> record : interestChangeRecords) {
                String dateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                LocalDate date = parseDate(dateStr);
                if (date != null && !date.isBefore(startDate) && !date.isAfter(endDate)) {
                    if (!changeDates.contains(date)) {
                        changeDates.add(date);
                    }
                    // 保存利率变更日期对应的新利率
                    String interestRateStr = record.get("interest_rate") != null
                            ? record.get("interest_rate").toString()
                            : "0";
                    double newRate = parseInterestRate(interestRateStr);
                    interestRateMap.put(date, newRate);
                }
            }
            // 按日期排序
            changeDates.sort(LocalDate::compareTo);

            // 获取期初本金和利率
            double currentPrincipal = getLoanBalanceAtDate(formmain_id, startDate);
            double currentRate = getInitialRate(serialNumber, startDate);

            LocalDate currentStart = startDate;
            double totalInterest = 0.0;

            // 按变化日期拆分区间，逐段计息
            for (LocalDate changeDate : changeDates) {
                // 区间结束日 = 变化日期的前一天
                LocalDate intervalEnd = changeDate.minusDays(1);

                if (!intervalEnd.isBefore(currentStart)) {
                    // 计算当前区间的天数（算头不算尾：起始日算入，到期日不算入）
                    // long days = ChronoUnit.DAYS.between(currentStart, intervalEnd) + 1;
                    long days = ChronoUnit.DAYS.between(currentStart, intervalEnd);
                    if (days > 0) {
                        // 单区间利息 = 区间本金 × 日利率 × 区间天数
                        double intervalInterest = currentPrincipal * (currentRate / 360.0) * days;
                        totalInterest += intervalInterest;
                        log.info("区间计息 - 起点: {}, 终点: {}, 天数: {}, 本金: {}, 利率: {}, 利息: {}",
                                currentStart, intervalEnd, days, currentPrincipal, currentRate, intervalInterest);
                    }
                }

                // 更新下一区段的起点、本金和利率
                currentStart = changeDate;
                currentPrincipal = getLoanBalanceAtDate(formmain_id, changeDate);
                // 如果是利率变更日期，直接使用变更记录中的新利率；否则使用getInitialRate查询
                if (interestRateMap.containsKey(changeDate)) {
                    currentRate = interestRateMap.get(changeDate);
                    log.info("利率变更日期: {}, 使用新利率: {}", changeDate, currentRate);
                } else {
                    currentRate = getInitialRate(serialNumber, changeDate);
                }
            }

            // 计算最后一个区间：最后一次变化 → 付息结束日
            // if (!endDate.isBefore(currentStart)) {
            // // 算头不算尾：起始日算入，所以需要 +1；到期日（endDate）不算入
            // long days = ChronoUnit.DAYS.between(currentStart, endDate) + 1;
            // if (days > 0) {
            // // 最后区间利息 = 区间本金 × 日利率 × 区间天数
            // double lastIntervalInterest = currentPrincipal * (currentRate / 360.0) *
            // days;
            // totalInterest += lastIntervalInterest;
            // log.info("最后区间计息 - 起点: {}, 终点: {}, 天数: {}, 本金: {}, 利率: {}, 利息: {}",
            // currentStart, endDate, days, currentPrincipal, currentRate,
            // lastIntervalInterest);
            // }
            // }
            // 原错误代码整段删掉，替换成
            if (!endDate.isBefore(currentStart)) {
                LocalDate lastIntervalEnd = endDate.minusDays(1);
                long days = ChronoUnit.DAYS.between(currentStart, lastIntervalEnd) + 1;
                if (days > 0) {
                    double lastIntervalInterest = currentPrincipal * (currentRate / 360.0) * days;
                    totalInterest += lastIntervalInterest;
                    log.info("最后区间计息 - 起点: {}, 终点: {}, 天数: {}, 本金: {}, 利率: {}, 利息: {}",
                            currentStart, lastIntervalEnd, days, currentPrincipal, currentRate, lastIntervalInterest);
                }
            }

            // 保留9位小数（四舍五入）
            BigDecimal totalInterestDecimal = BigDecimal.valueOf(totalInterest);
            totalInterestDecimal = totalInterestDecimal.setScale(9, RoundingMode.HALF_UP);
            totalInterest = totalInterestDecimal.doubleValue();

            log.info("按本金变化和利率调整计算付息完成，总利息: {}，formmain_id: {}, startDate: {}, endDate: {}",
                    totalInterest, formmain_id, startDate, endDate);

            return totalInterest;

        } catch (Exception e) {
            log.error("按本金变化和利率调整计算付息时发生错误，formmain_id: {}", formmain_id, e);
            return 0.0;
        }
    }

    /**
     * 获取计息区间内的所有本金变化记录（下柜和还本）
     *
     * @param formmain_id 主表ID
     * @param startDate   起始日期
     * @param endDate     结束日期
     * @return 本金变化记录列表（按日期排序）
     */
    public List<Map<String, Object>> getPrincipalChangeRecords(String formmain_id, LocalDate startDate,
            LocalDate endDate) {
        try {
            // 获取区间内所有记录（包括下柜和还本的日期）
            List<Map<String, Object>> allRecords = installmentPaymentMapper.getPaymentDatesByDateRange(formmain_id,
                    startDate.toString(), endDate.toString());

            if (allRecords == null || allRecords.isEmpty()) {
                return new ArrayList<>();
            }

            // 过滤出有本金变化的记录（有下柜或还本）
            List<Map<String, Object>> changeRecords = new ArrayList<>();
            for (Map<String, Object> record : allRecords) {
                String dateStr = record.get("field0026") != null ? record.get("field0026").toString() : "";
                LocalDate recordDate = parseDate(dateStr);
                if (recordDate == null) {
                    continue;
                }

                // 检查是否有下柜或还本
                String disbursementStr = record.get("field0027") != null ? record.get("field0027").toString() : "";
                String repaymentStr = record.get("field0029") != null ? record.get("field0029").toString() : "";

                double disbursement = 0.0;
                double repayment = 0.0;
                try {
                    if (disbursementStr != null && !disbursementStr.trim().isEmpty()) {
                        disbursement = Double.parseDouble(disbursementStr);
                    }
                    if (repaymentStr != null && !repaymentStr.trim().isEmpty()) {
                        repayment = Double.parseDouble(repaymentStr);
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }

                // 如果有下柜或还本，且日期在区间内，则加入列表
                if ((disbursement > 0 || repayment > 0) &&
                        !recordDate.isBefore(startDate) &&
                        !recordDate.isAfter(endDate)) {
                    changeRecords.add(record);
                }
            }

            // 按日期排序
            changeRecords.sort((r1, r2) -> {
                String date1Str = r1.get("field0026") != null ? r1.get("field0026").toString() : "";
                String date2Str = r2.get("field0026") != null ? r2.get("field0026").toString() : "";
                LocalDate date1 = parseDate(date1Str);
                LocalDate date2 = parseDate(date2Str);
                if (date1 == null || date2 == null) {
                    return 0;
                }
                return date1.compareTo(date2);
            });

            return changeRecords;
        } catch (Exception e) {
            log.error("获取本金变化记录失败，formmain_id: {}, startDate: {}, endDate: {}",
                    formmain_id, startDate, endDate, e);
            return new ArrayList<>();
        }
    }
}