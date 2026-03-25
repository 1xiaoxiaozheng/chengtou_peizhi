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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从按日表累加付息工具类
 * 用于按季付息和按月付息，直接从按日表的付息列累加
 * 例如：按季付息3月20日的付息 = 从按日表累加去年12月21日到3月20日的付息
 */
@Component
public class DailyInterestAccumulationUtil {

    private static final Logger log = LoggerFactory.getLogger(DailyInterestAccumulationUtil.class);

    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final interestMapper interestMapper;

    @Autowired
    public DailyInterestAccumulationUtil(DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
                                         InstallmentPaymentMapper installmentPaymentMapper,
                                         interestMapper interestMapper) {
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.interestMapper = interestMapper;
    }

    /**
     * 从按日表累加指定日期范围内的付息
     * 
     * @param serialNumber 流水号（单据编号）
     * @param startDate    起始日期（包含）
     * @param endDate      结束日期（包含）
     * @return 累加后的付息总和，如果无法计算则返回0.0
     */
    public double accumulateInterestFromDailyTable(String serialNumber, LocalDate startDate, LocalDate endDate) {
        try {
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("流水号为空，无法从按日表累加付息");
                return 0.0;
            }

            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                log.warn("日期范围无效，无法从按日表累加付息，startDate: {}, endDate: {}", startDate, endDate);
                return 0.0;
            }

            // 1. 通过流水号获取按日表的主表ID
            String dailyFormmainId = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(serialNumber);
            if (dailyFormmainId == null || dailyFormmainId.trim().isEmpty()) {
                log.warn("未找到按日表主表ID，流水号: {}", serialNumber);
                return 0.0;
            }

            // 2. 查询按日表中指定日期范围内的付息总和
            String totalInterestStr = dailyRepaymentPlanMapper.getTotalInterestByDateRange(
                    dailyFormmainId,
                    startDate.toString(),
                    endDate.toString());

            if (totalInterestStr == null || totalInterestStr.trim().isEmpty()) {
                log.debug("按日表中未找到付息数据，流水号: {}, startDate: {}, endDate: {}",
                        serialNumber, startDate, endDate);
                return 0.0;
            }

            // 3. 解析并返回付息总和
            double totalInterest = Double.parseDouble(totalInterestStr);

            // 保留9位小数（四舍五入）
            BigDecimal totalInterestDecimal = BigDecimal.valueOf(totalInterest);
            totalInterestDecimal = totalInterestDecimal.setScale(9, RoundingMode.HALF_UP);
            totalInterest = totalInterestDecimal.doubleValue();

            log.info("从按日表累加付息完成，流水号: {}, startDate: {}, endDate: {}, 总付息: {}",
                    serialNumber, startDate, endDate, totalInterest);

            return totalInterest;

        } catch (Exception e) {
            log.error("从按日表累加付息时发生错误，流水号: {}, startDate: {}, endDate: {}",
                    serialNumber, startDate, endDate, e);
            return 0.0;
        }
    }

    /**
     * 分期表自算区间付息（不依赖按日表）。
     *
     * 规则与“模拟付息”一致：
     * - 当日贷款余额 = 上期贷款余额 + 当日下柜金额 - 上期还本金额（当日还本当日不扣，次日作为“上期还本”扣）
     * - 单日付息 = 前一日贷款余额 × 前一日年利率/360
     * - 仅从“首次下柜日期的次日”开始计息（首次下柜当天不计息；早于首次下柜不计息）
     *
     * @param serialNumber     流水号（单据编号）
     * @param installmentMainId 分期表主表ID（formmain_0039）
     * @param startDate        起始日期（包含）
     * @param endDate          结束日期（包含）
     * @param firstDisbursementDate 首次下柜日期
     */
    public double accumulateInterestFromInstallmentTable(String serialNumber,
                                                         String installmentMainId,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         LocalDate firstDisbursementDate) {
        try {
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("流水号为空，无法计算分期区间付息");
                return 0.0;
            }
            if (installmentMainId == null || installmentMainId.trim().isEmpty()) {
                log.warn("分期主表ID为空，无法计算分期区间付息，serialNumber: {}", serialNumber);
                return 0.0;
            }
            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                log.warn("日期范围无效，无法计算分期区间付息，startDate: {}, endDate: {}", startDate, endDate);
                return 0.0;
            }
            if (firstDisbursementDate == null) {
                // 没有下柜，不计息
                return 0.0;
            }

            LocalDate baseDate = startDate.minusDays(1);

            // 1) 拉取区间内（baseDate..endDate）分期明细，构建下柜/还本 map（缺省为0）
            Map<LocalDate, BigDecimal> drawdownMap = new HashMap<>();
            Map<LocalDate, BigDecimal> repaymentMap = new HashMap<>();

            List<Map<String, Object>> records = installmentPaymentMapper.getPaymentDatesByDateRange(
                    installmentMainId, baseDate.toString(), endDate.toString());
            if (records != null && !records.isEmpty()) {
                for (Map<String, Object> r : records) {
                    LocalDate d = parseToLocalDate(r.get("field0026"));
                    if (d == null) continue;

                    BigDecimal drawdown = parseToBigDecimal(r.get("field0027"));
                    if (drawdown != null && drawdown.compareTo(BigDecimal.ZERO) != 0) {
                        drawdownMap.merge(d, drawdown, BigDecimal::add);
                    }

                    BigDecimal repay = parseToBigDecimal(r.get("field0029"));
                    if (repay != null && repay.compareTo(BigDecimal.ZERO) != 0) {
                        repaymentMap.merge(d, repay, BigDecimal::add);
                    }
                }
            }

            // 2) 初始化 baseDate 的余额与“上期还本”
            BigDecimal prevBalance = parseToBigDecimal(
                    installmentPaymentMapper.getLoanBalanceByFormmainIdAndTime(installmentMainId, baseDate.toString()));
            if (prevBalance == null) prevBalance = BigDecimal.ZERO;

            BigDecimal prevRepayment = parseToBigDecimal(
                    installmentPaymentMapper.getRepaymentByFormmainIdAndTime(installmentMainId, baseDate.toString()));
            if (prevRepayment == null) prevRepayment = BigDecimal.ZERO;

            RateProvider rateProvider = new RateProvider(serialNumber, interestMapper);

            BigDecimal totalInterest = BigDecimal.ZERO;
            LocalDate current = baseDate.plusDays(1);
            while (!current.isAfter(endDate)) {
                LocalDate prevDay = current.minusDays(1);

                // 3) 计息：当日付息 = 前一日余额 × 前一日年利率/360
                //    与按日表一致：先按日舍入 9 位再累加，否则“区间高精度累加再舍入”会导致总额与按日表差几分钱
                if (shouldCalculateInterest(current, firstDisbursementDate)) {
                    BigDecimal prevRate = rateProvider.getRateFor(prevDay);
                    if (prevRate.compareTo(BigDecimal.ZERO) > 0 && prevBalance.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal dailyInterest = prevBalance
                                .multiply(prevRate)
                                .divide(BigDecimal.valueOf(360), 18, RoundingMode.HALF_UP);
                        totalInterest = totalInterest.add(dailyInterest.setScale(9, RoundingMode.HALF_UP));
                    }
                }

                // 4) 推演余额：当日余额 = 上期余额 + 当日下柜 - 上期还本
                BigDecimal drawdownToday = drawdownMap.getOrDefault(current, BigDecimal.ZERO);
                BigDecimal repaymentToday = repaymentMap.getOrDefault(current, BigDecimal.ZERO);

                BigDecimal currentBalance = prevBalance.add(drawdownToday).subtract(prevRepayment);
                if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
                    currentBalance = BigDecimal.ZERO;
                }

                prevBalance = currentBalance;
                prevRepayment = repaymentToday;
                current = current.plusDays(1);
            }

            // 5) 区间和已是“按日舍入再累加”，再统一 round 9 位后返回（与按日表口径一致）
            totalInterest = totalInterest.setScale(9, RoundingMode.HALF_UP);
            double result = totalInterest.doubleValue();

            log.debug("分期表自算区间付息完成，serialNumber: {}, startDate: {}, endDate: {}, totalInterest: {}",
                    serialNumber, startDate, endDate, totalInterest.toPlainString());
            return result;

        } catch (Exception e) {
            log.error("分期表自算区间付息失败，serialNumber: {}, startDate: {}, endDate: {}",
                    serialNumber, startDate, endDate, e);
            return 0.0;
        }
    }

    private boolean shouldCalculateInterest(LocalDate currentDate, LocalDate firstDisbursementDate) {
        // 首次下柜当天不计息；从次日开始计息
        return currentDate != null && firstDisbursementDate != null && currentDate.isAfter(firstDisbursementDate);
    }

    private LocalDate parseToLocalDate(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            if (s.contains(" ")) s = s.split(" ")[0];
            if (s.contains("T")) s = s.split("T")[0];
            if (s.contains(".")) s = s.split("\\.")[0];
            s = s.replace("/", "-");
            if (s.length() > 10) s = s.substring(0, 10);
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseToBigDecimal(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 利率提供器：一次性加载利率变更记录，按日期取“<= 指定日期”的最新利率。
     * 兜底：若无变更记录，则使用主表默认利率（interestMapper.getInterest）。
     */
    private static class RateProvider {
        private final List<Map<String, Object>> changeRecords;
        private final BigDecimal defaultRate;

        RateProvider(String serialNumber, interestMapper interestMapper) {
            this.changeRecords = interestMapper.getInterestChangesBySerialNumber(serialNumber);
            this.changeRecords.sort((a, b) -> {
                String da = a.get("change_date") != null ? a.get("change_date").toString() : "";
                String db = b.get("change_date") != null ? b.get("change_date").toString() : "";
                return da.compareTo(db);
            });
            this.defaultRate = parseRateSafe(interestMapper.getInterest(serialNumber));
        }

        BigDecimal getRateFor(LocalDate date) {
            if (date == null) return BigDecimal.ZERO;
            BigDecimal rate = BigDecimal.ZERO;
            for (Map<String, Object> r : changeRecords) {
                String changeDateStr = r.get("change_date") != null ? r.get("change_date").toString() : "";
                LocalDate changeDate = parseDateSafe(changeDateStr);
                if (changeDate == null) continue;
                if (!changeDate.isAfter(date)) {
                    String rateStr = r.get("interest_rate") != null ? r.get("interest_rate").toString() : null;
                    BigDecimal parsed = parseRateSafe(rateStr);
                    if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                        rate = parsed;
                    }
                } else {
                    break;
                }
            }
            if (rate.compareTo(BigDecimal.ZERO) == 0) {
                rate = defaultRate != null ? defaultRate : BigDecimal.ZERO;
            }
            return rate;
        }

        private static LocalDate parseDateSafe(String s) {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            try {
                if (s.contains(" ")) s = s.split(" ")[0];
                if (s.contains("T")) s = s.split("T")[0];
                if (s.contains(".")) s = s.split("\\.")[0];
                s = s.replace("/", "-");
                if (s.length() > 10) s = s.substring(0, 10);
                return LocalDate.parse(s);
            } catch (Exception e) {
                return null;
            }
        }

        private static BigDecimal parseRateSafe(String rateStr) {
            if (rateStr == null) return BigDecimal.ZERO;
            String s = rateStr.trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) return BigDecimal.ZERO;
            try {
                boolean isPercentage = s.contains("%");
                s = s.replace("%", "").trim();
                BigDecimal v = new BigDecimal(s);
                if (isPercentage || (v.compareTo(BigDecimal.ONE) > 0 && v.compareTo(BigDecimal.valueOf(100)) <= 0)) {
                    v = v.divide(BigDecimal.valueOf(100), 18, RoundingMode.HALF_UP);
                }
                return v;
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
    }
}
