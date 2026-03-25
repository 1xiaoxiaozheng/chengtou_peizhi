package com.SpringbootTZ.FACT.Service;

import com.SpringbootTZ.FACT.Mapper.interestMapper;
import com.SpringbootTZ.FACT.Service.DailyPayment.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 模拟付息计算服务
 * 用于前端模拟下柜和还本，计算对应的贷款余额和付息
 */
@Service
public class SimulatedPaymentService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentService.class);

    private final interestMapper interestMapper;
    private final DateUtil dateUtil;

    @Autowired
    public SimulatedPaymentService(interestMapper interestMapper, DateUtil dateUtil) {
        this.interestMapper = interestMapper;
        this.dateUtil = dateUtil;
    }

    /**
     * 计算模拟付息
     * 
     * @param projectSerialNumber 项目编号
     * @param drawdownConfig      下柜配置列表
     * @param repayment           还本配置列表
     * @param timeTableList       时间表列表
     * @return 计算结果列表
     */
    public List<Map<String, Object>> calculateSimulatedInterest(
            String projectSerialNumber,
            List<Map<String, Object>> drawdownConfig,
            List<Map<String, Object>> repayment,
            List<Map<String, Object>> timeTableList) {

        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            // 1. 解析并排序时间表
            List<TimePoint> timePoints = parseAndSortTimeTable(timeTableList);
            if (timePoints.isEmpty()) {
                log.warn("时间表为空，无法计算");
                return resultList;
            }

            // 2. 解析下柜配置，按日期分组
            Map<String, Double> drawdownMap = parseDrawdownConfig(drawdownConfig);

            // 3. 解析还本配置，按日期分组
            Map<String, Double> repaymentMap = parseRepaymentConfig(repayment);

            // 4. 获取首次下柜日期（从下柜配置中最早日期）
            String firstDisbursementDate = getFirstDisbursementDate(drawdownConfig);

            // 5. 按时间顺序计算每个时间点的贷款余额和付息
            double lastLoanBalance = 0.0;
            double lastRepayment = 0.0; // 上期的还本金额
            double lastCumulativeInterest = 0.0;

            for (int i = 0; i < timePoints.size(); i++) {
                TimePoint currentPoint = timePoints.get(i);
                String currentDate = currentPoint.getDate();

                // 计算当前日期的贷款余额
                // 规则：本日贷款余额 = 今日下柜金额 + 上期贷款余额 - 上期的还本
                // 说明：如果今天有还本，今天的余额不减，明天才减
                double currentDrawdown = drawdownMap.getOrDefault(currentDate, 0.0);
                double currentRepayment = repaymentMap.getOrDefault(currentDate, 0.0);

                // 当前日期的贷款余额 = 上期余额 + 当前日期的下柜 - 上期的还本
                double currentLoanBalance = lastLoanBalance + currentDrawdown - lastRepayment;

                // 计算从上一个时间点到当前时间点的累计付息
                double periodInterest = 0.0;
                if (i > 0) {
                    String lastDate = timePoints.get(i - 1).getDate();
                    periodInterest = calculatePeriodInterest(
                            projectSerialNumber,
                            lastDate,
                            currentDate,
                            drawdownMap,
                            repaymentMap,
                            firstDisbursementDate);
                }

                // 当前累计付息
                double currentCumulativeInterest = lastCumulativeInterest + periodInterest;

                // 该日的付息增量 = 当前累计付息 - 上一日的累计付息
                double dailyInterestIncrement = currentCumulativeInterest - lastCumulativeInterest;

                // 构建返回结果
                Map<String, Object> result = new HashMap<>();
                result.put("rowId", currentPoint.getRowId());
                result.put("loanBalance", roundTo2Decimals(currentLoanBalance));
                result.put("simulatedInterest", roundTo2Decimals(dailyInterestIncrement));

                resultList.add(result);

                // 更新上期余额、上期还本和累计付息
                lastLoanBalance = currentLoanBalance;
                lastRepayment = currentRepayment; // 记录当前日期的还本，作为下一期的上期还本
                lastCumulativeInterest = currentCumulativeInterest;

                log.debug("计算完成 - 日期: {}, 贷款余额: {}, 付息增量: {}",
                        currentDate, currentLoanBalance, dailyInterestIncrement);
            }

            log.info("模拟付息计算完成，项目编号: {}, 计算了 {} 个时间点",
                    projectSerialNumber, resultList.size());

        } catch (Exception e) {
            log.error("计算模拟付息失败，项目编号: {}", projectSerialNumber, e);
            throw new RuntimeException("计算模拟付息失败: " + e.getMessage(), e);
        }

        return resultList;
    }

    /**
     * 解析并排序时间表
     */
    private List<TimePoint> parseAndSortTimeTable(List<Map<String, Object>> timeTableList) {
        List<TimePoint> timePoints = new ArrayList<>();
        for (Map<String, Object> item : timeTableList) {
            String rowId = (String) item.get("rowId");
            String time = (String) item.get("time");
            if (rowId != null && time != null) {
                timePoints.add(new TimePoint(rowId, time));
            }
        }
        // 按日期排序
        timePoints.sort(Comparator.comparing(TimePoint::getDate));
        return timePoints;
    }

    /**
     * 解析下柜配置，按日期分组
     */
    private Map<String, Double> parseDrawdownConfig(List<Map<String, Object>> drawdownConfig) {
        Map<String, Double> drawdownMap = new HashMap<>();
        for (Map<String, Object> item : drawdownConfig) {
            String date = (String) item.get("drawdownDate");
            Object amountObj = item.get("drawdownAmount");
            if (date != null && amountObj != null) {
                try {
                    double amount = Double.parseDouble(amountObj.toString());
                    // 如果同一天有多个下柜，累加
                    drawdownMap.put(date, drawdownMap.getOrDefault(date, 0.0) + amount);
                } catch (NumberFormatException e) {
                    log.warn("下柜金额格式错误: {}", amountObj);
                }
            }
        }
        return drawdownMap;
    }

    /**
     * 解析还本配置，按日期分组
     */
    private Map<String, Double> parseRepaymentConfig(List<Map<String, Object>> repayment) {
        Map<String, Double> repaymentMap = new HashMap<>();
        for (Map<String, Object> item : repayment) {
            String date = (String) item.get("repaymentDate");
            Object amountObj = item.get("repaymentAmount");
            if (date != null && amountObj != null) {
                try {
                    double amount = Double.parseDouble(amountObj.toString());
                    // 如果同一天有多个还本，累加
                    repaymentMap.put(date, repaymentMap.getOrDefault(date, 0.0) + amount);
                } catch (NumberFormatException e) {
                    log.warn("还本金额格式错误: {}", amountObj);
                }
            }
        }
        return repaymentMap;
    }

    /**
     * 获取首次下柜日期
     */
    private String getFirstDisbursementDate(List<Map<String, Object>> drawdownConfig) {
        String firstDate = null;
        for (Map<String, Object> item : drawdownConfig) {
            String date = (String) item.get("drawdownDate");
            if (date != null && (firstDate == null || date.compareTo(firstDate) < 0)) {
                firstDate = date;
            }
        }
        return firstDate;
    }

    /**
     * 计算从开始日期到结束日期的累计付息（逐日累加）
     * 
     * @param projectSerialNumber   项目编号
     * @param startDate             开始日期（不包含）
     * @param endDate               结束日期（包含）
     * @param drawdownMap           下柜配置
     * @param repaymentMap          还本配置
     * @param firstDisbursementDate 首次下柜日期
     * @return 累计付息
     */
    private double calculatePeriodInterest(
            String projectSerialNumber,
            String startDate,
            String endDate,
            Map<String, Double> drawdownMap,
            Map<String, Double> repaymentMap,
            String firstDisbursementDate) {

        try {
            LocalDate start = dateUtil.parseDate(startDate);
            LocalDate end = dateUtil.parseDate(endDate);

            if (start == null || end == null || !end.isAfter(start)) {
                return 0.0;
            }

            double totalInterest = 0.0;
            double currentLoanBalance = calculateLoanBalanceAtDate(startDate, drawdownMap, repaymentMap);

            // 获取 startDate 的还本金额（作为上期还本）
            String startDateStr = startDate;
            double previousRepayment = repaymentMap.getOrDefault(startDateStr, 0.0);

            // 从 startDate 的下一天开始，到 endDate（包含）
            LocalDate currentDate = start.plusDays(1);
            while (!currentDate.isAfter(end)) {
                String currentDateStr = currentDate.toString();

                // 获取前一天的贷款余额
                double previousLoanBalance = currentLoanBalance;

                // 计算当前日期的贷款余额
                // 规则：本日贷款余额 = 今日下柜金额 + 上期贷款余额 - 上期的还本
                double currentDrawdown = drawdownMap.getOrDefault(currentDateStr, 0.0);
                double currentRepayment = repaymentMap.getOrDefault(currentDateStr, 0.0);
                currentLoanBalance = previousLoanBalance + currentDrawdown - previousRepayment;

                // 更新上期还本（当前日期的还本作为下一期的上期还本）
                previousRepayment = currentRepayment;

                // 获取前一天的利率
                LocalDate previousDate = currentDate.minusDays(1);
                double previousRate = getRateForDate(projectSerialNumber, previousDate);

                // 计算当天的付息 = 前一天的贷款余额 × 前一天的日利率
                // 需要考虑首次下柜日期（首次下柜当天不计息）
                double dailyInterest = 0.0;
                if (shouldCalculateInterest(currentDateStr, firstDisbursementDate)) {
                    double dailyRate = previousRate / 360.0;
                    dailyInterest = previousLoanBalance * dailyRate;
                }

                totalInterest += dailyInterest;

                // 移动到下一天
                currentDate = currentDate.plusDays(1);
            }

            return totalInterest;

        } catch (Exception e) {
            log.error("计算期间付息失败，startDate: {}, endDate: {}", startDate, endDate, e);
            return 0.0;
        }
    }

    /**
     * 计算指定日期的贷款余额
     * 注意：还本在当天不减，在第二天才减
     */
    private double calculateLoanBalanceAtDate(
            String targetDate,
            Map<String, Double> drawdownMap,
            Map<String, Double> repaymentMap) {

        double balance = 0.0;

        // 累加所有 <= targetDate 的下柜
        for (Map.Entry<String, Double> entry : drawdownMap.entrySet()) {
            if (entry.getKey().compareTo(targetDate) <= 0) {
                balance += entry.getValue();
            }
        }

        // 累减所有 < targetDate 的还本（当天还本不减，第二天才减）
        for (Map.Entry<String, Double> entry : repaymentMap.entrySet()) {
            if (entry.getKey().compareTo(targetDate) < 0) {
                balance -= entry.getValue();
            }
        }

        return balance;
    }

    /**
     * 判断是否应该计息
     * 首次下柜当天不计息，从第二天开始计息
     */
    private boolean shouldCalculateInterest(String currentDate, String firstDisbursementDate) {
        if (firstDisbursementDate == null || firstDisbursementDate.trim().isEmpty()) {
            return false; // 没有下柜，不计息
        }

        LocalDate current = dateUtil.parseDate(currentDate);
        LocalDate firstDisbursement = dateUtil.parseDate(firstDisbursementDate);

        if (current == null || firstDisbursement == null) {
            return false;
        }

        // 如果当前日期早于首次下柜日期，不计息
        if (current.isBefore(firstDisbursement)) {
            return false;
        }

        // 如果当前日期是首次下柜当天，不计息
        if (current.equals(firstDisbursement)) {
            return false;
        }

        // 从首次下柜的第二天开始计息
        return true;
    }

    /**
     * 获取指定日期的利率（考虑利率变更）
     */
    private double getRateForDate(String projectSerialNumber, LocalDate date) {
        try {
            // 查询该日期之前（包含该日期）的最新利率变更记录
            List<Map<String, Object>> allRecords = interestMapper.getInterestChangesBySerialNumber(projectSerialNumber);
            double rate = 0.0;
            for (Map<String, Object> record : allRecords) {
                String changeDateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                LocalDate changeDate = dateUtil.parseDate(changeDateStr);
                // 如果变更日期不晚于当前日期（包含等于），则使用该利率（取最新的）
                if (changeDate != null && !changeDate.isAfter(date)) {
                    String interestRateStr = record.get("interest_rate") != null
                            ? record.get("interest_rate").toString()
                            : "0";
                    rate = parseInterestRate(interestRateStr);
                }
            }
            // 如果没有找到利率变更记录，使用主表的默认利率
            if (rate == 0.0) {
                String defaultRate = interestMapper.getInterest(projectSerialNumber);
                if (defaultRate != null && !defaultRate.trim().isEmpty()) {
                    rate = parseInterestRate(defaultRate);
                }
            }
            return rate;
        } catch (Exception e) {
            log.error("获取指定日期利率失败，projectSerialNumber: {}, date: {}", projectSerialNumber, date, e);
            return 0.0;
        }
    }

    /**
     * 解析利率字符串，转换为double
     */
    private double parseInterestRate(String interestRateStr) {
        if (interestRateStr == null || interestRateStr.trim().isEmpty()) {
            return 0.0;
        }

        try {
            String cleanRateStr = interestRateStr.trim();
            boolean isPercentage = cleanRateStr.contains("%");
            cleanRateStr = cleanRateStr.replace("%", "").trim();

            double rate = Double.parseDouble(cleanRateStr);

            // 如果包含百分号，或者数值大于1（可能是百分比格式），转换为小数
            if (isPercentage || (rate > 1 && rate <= 100)) {
                rate = rate / 100.0;
            }

            return rate;
        } catch (Exception e) {
            log.error("解析利率失败: " + interestRateStr + ", 错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 保留2位小数
     */
    private double roundTo2Decimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 时间点内部类
     */
    private static class TimePoint {
        private String rowId;
        private String date;

        public TimePoint(String rowId, String date) {
            this.rowId = rowId;
            this.date = date;
        }

        public String getRowId() {
            return rowId;
        }

        public String getDate() {
            return date;
        }
    }
}
