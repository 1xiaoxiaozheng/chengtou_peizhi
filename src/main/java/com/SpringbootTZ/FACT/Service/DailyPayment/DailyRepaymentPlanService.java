package com.SpringbootTZ.FACT.Service.DailyPayment;

import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.interestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 负责按日还款计划的计算逻辑
 * 重要公式：
 * 日利率 = 年利率 ÷360、当日利息 = 贷款余额 × 日利率
 */
@Service
public class DailyRepaymentPlanService {

    private static final Logger log = LoggerFactory.getLogger(DailyRepaymentPlanService.class);

    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final interestMapper interestMapper;
    private final DateUtil dateUtil;

    @Autowired
    public DailyRepaymentPlanService(DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
            interestMapper interestMapper,
            DateUtil dateUtil) {
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.interestMapper = interestMapper;
        this.dateUtil = dateUtil;
    }

    /**
     * 计算指定日期范围内的贷款余额和付息，并通过UpdateDataToDaily更新到数据库
     * 
     * @param loanSerialNo      贷款流水号
     * @param startDate         开始日期
     * @param endDate           结束日期
     * @param interestRate      利率（已废弃，现在动态获取）
     * @param updateDataToDaily 更新服务，用于更新数据库
     */
    public void calculateAndUpdateDailyData(String loanSerialNo, String startDate, String endDate,
            double interestRate, UpdateDataToDaily updateDataToDaily) {
        try {
            // 获取主表的formmain_id
            String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);

            if (formmain_id == null) {
                log.error("未找到流水号对应的主表记录: " + loanSerialNo);
                return;
            }

            // 获取贷款开始时间（新数据字典：field0059 开始日期）
            Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(formmain_id);
            String loanStartTime = mainTableInfo.get("field0059") != null ? mainTableInfo.get("field0059").toString()
                    : "";

            // 获取明细表中指定日期范围内的记录
            List<Map<String, Object>> detailTableRecords = dailyRepaymentPlanMapper
                    .getDetailTableByFormmainId(formmain_id);

            if (detailTableRecords == null || detailTableRecords.isEmpty()) {
                log.error("未找到明细表记录，formmain_id: " + formmain_id);
                return;
            }

            // 获取首次下柜日期（用于判断是否应该计息）
            String firstDisbursementDate = dailyRepaymentPlanMapper.getFirstDisbursementDate(formmain_id);

            // 解析开始和结束日期
            LocalDate startLocalDate = dateUtil.parseDate(startDate);
            LocalDate endLocalDate = dateUtil.parseDate(endDate);

            if (startLocalDate == null || endLocalDate == null) {
                log.error("日期解析失败，startDate: " + startDate + ", endDate: " + endDate);
                return;
            }

            // 遍历明细表记录，只处理指定日期范围内的记录
            boolean exitAfterNext = false; // 还本日需多处理一天（次日付息）再批量置0
            for (Map<String, Object> record : detailTableRecords) {
                try {
                    // 获取该条记录的时间（新数据字典：field0026 时间）
                    Object timeObj = record.get("field0026");
                    if (timeObj == null) {
                        continue; // 跳过没有时间的记录
                    }

                    String timeStr = timeObj.toString();
                    LocalDate recordDate = dateUtil.parseDate(timeStr);

                    if (recordDate == null) {
                        continue; // 跳过无法解析日期的记录
                    }

                    // 只处理指定日期范围内的记录
                    if (recordDate.isBefore(startLocalDate) || recordDate.isAfter(endLocalDate)) {
                        continue; // 跳过不在指定范围内的记录
                    }

                    // 获取这个日期前一天的日期
                    LocalDate yesterday = recordDate.minusDays(1);

                    // 获取上期的贷款余额
                    String lastLoanBalance = dailyRepaymentPlanMapper.getLoanBalanceByFormmainIdAndTime(formmain_id,
                            yesterday.toString());
                    if (lastLoanBalance == null || lastLoanBalance.trim().isEmpty()) {
                        lastLoanBalance = "0"; // 如果为空，默认为0
                    }

                    // 获取本期下柜资金（新数据字典：field0027 下柜资金）
                    Object currentPrincipalObj = record.get("field0027");
                    String currentPrincipal = (currentPrincipalObj != null) ? currentPrincipalObj.toString() : "0";

                    // 获取上期还本（新数据字典：field0029 还本）
                    String lastPayment = dailyRepaymentPlanMapper.getRepaymentByFormmainIdAndTime(formmain_id,
                            yesterday.toString());
                    if (lastPayment == null || lastPayment.trim().isEmpty()) {
                        lastPayment = "0"; // 如果为空，默认为0
                    }

                    // 全部用 BigDecimal 计算（金融场景）
                    BigDecimal lastBalance = parseBigDecimal(lastLoanBalance);
                    BigDecimal currentPrincipalBD = parseBigDecimal(currentPrincipal);
                    BigDecimal lastPaymentBD = parseBigDecimal(lastPayment);

                    // 计算本期贷款余额
                    BigDecimal principal = calculatePrincipal(lastBalance, currentPrincipalBD, lastPaymentBD);

                    // 计算本期付息：前一日余额 × 前一日年利率/360，18 位精度，写入时再舍入 9 位
                    BigDecimal rateForDate = getRateForDate(loanSerialNo, yesterday);
                    BigDecimal dailyInterest = calculateInterest(lastBalance, rateForDate, timeStr,
                            firstDisbursementDate);
                    String formattedInterest = dailyInterest.setScale(9, RoundingMode.HALF_UP).toPlainString();

                    // 通过更新服务更新数据库（新数据字典：field0028是贷款余额，field0030是付息）
                    updateDataToDaily.updateDailyData(formmain_id, timeStr,
                            principal.toPlainString(),
                            formattedInterest);

                    // 提前退出优化：迄今日 下柜合计=还本合计 且 后续无下柜，则后续日期均为0可跳过（必须看还本日，不能只用主表全表合计）
                    // 注意：还本日次日（recordDate+1）仍需付息（付的是还本日产生的利息），需多处理一天再批量置0
                    if (isLoanFullyRepaidBySumsUpToDate(detailTableRecords, recordDate)
                            && !hasFutureDisbursement(detailTableRecords, recordDate)) {
                        if (exitAfterNext) {
                            LocalDate zeroFromDate = recordDate.plusDays(1);
                            if (!zeroFromDate.isAfter(endLocalDate)) {
                                String nextDateStr = zeroFromDate.toString();
                                updateDataToDaily.batchUpdateZeroFromDate(formmain_id, nextDateStr);
                                long skippedDays = ChronoUnit.DAYS.between(zeroFromDate, endLocalDate) + 1;
                                log.info("贷款已还清且无后续下柜（迄今日下柜合计=还本合计），从 {} 起批量置0并提前退出，跳过 {} 天计算，formmain_id: {}",
                                        nextDateStr, skippedDays, formmain_id);
                            }
                            break;
                        } else {
                            exitAfterNext = true;
                            continue; // 多处理一天（还本日次日）以正确写入付息后再批量置0
                        }
                    }
                    exitAfterNext = false;

                } catch (Exception e) {
                    log.error("处理单条记录时发生错误，formmain_id: {},error: {}", formmain_id, e);
                }
            }

        } catch (Exception e) {
            log.error("计算指定日期范围数据时发生错误，loanSerialNo: {}, startDate: {}, endDate: {}",
                    loanSerialNo, startDate, endDate, e);
        }
    }

    /**
     * 计算指定日期范围内的贷款余额和付息，并通过UpdateDataToDaily更新到数据库（重载方法，用于全量计算）
     * 
     * @param loanSerialNo      贷款流水号
     * @param updateDataToDaily 更新服务，用于更新数据库
     */
    public void calculateAndUpdateDailyData(String loanSerialNo, UpdateDataToDaily updateDataToDaily) {
        try {
            // 获取主表的formmain_id
            String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);

            if (formmain_id == null) {
                log.error("未找到流水号对应的主表记录: " + loanSerialNo);
                return;
            }

            // 获取明细表中的id = formmain_id的所有记录
            List<Map<String, Object>> detailTableRecords = dailyRepaymentPlanMapper
                    .getDetailTableByFormmainId(formmain_id);

            if (detailTableRecords == null || detailTableRecords.isEmpty()) {
                log.error("未找到明细表记录，formmain_id: " + formmain_id);
                return;
            }

            // 获取首次下柜日期（用于判断是否应该计息）
            String firstDisbursementDate = dailyRepaymentPlanMapper.getFirstDisbursementDate(formmain_id);

            log.info("开始遍历明细表记录，loanSerialNo: {}, formmain_id: {}, 记录总数: {}",
                    loanSerialNo, formmain_id, detailTableRecords.size());

            // 遍历每条的记录
            int processedCount = 0;
            boolean exitAfterNext = false; // 还本日需多处理一天（次日付息）再批量置0
            int totalCount = detailTableRecords.size();
            for (Map<String, Object> record : detailTableRecords) {
                processedCount++;
                // 将time变量声明在try块外部，以便在catch块中访问
                String time = null;
                try {
                    // 获取该条记录的时间（新数据字典：field0026 时间）
                    Object timeObj = record.get("field0026");
                    if (timeObj == null) {
                        log.error("记录中field0026字段为空，跳过该记录，进度: {}/{}", processedCount, totalCount);
                        continue;
                    }
                    time = timeObj.toString();

                    // 每处理100条记录输出一次进度日志
                    if (processedCount % 100 == 0 || processedCount == 1) {
                        log.info("正在处理明细表记录，loanSerialNo: {}, 进度: {}/{}, 当前日期: {}",
                                loanSerialNo, processedCount, totalCount, time);
                    }

                    // 获取这个日期前一天的日期
                    LocalDate recordDate = dateUtil.parseDate(time);
                    if (recordDate == null) {
                        log.error("记录中field0026字段日期解析失败，跳过该记录，time: {}", time);
                        continue;
                    }
                    LocalDate yesterday = recordDate.minusDays(1);

                    // 获取上期的贷款余额
                    log.debug("开始查询上期贷款余额，formmain_id: {}, yesterday: {}", formmain_id, yesterday);
                    String lastLoanBalance = dailyRepaymentPlanMapper.getLoanBalanceByFormmainIdAndTime(formmain_id,
                            yesterday.toString());
                    if (lastLoanBalance == null || lastLoanBalance.trim().isEmpty()) {
                        lastLoanBalance = "0"; // 如果为空，默认为0
                    }
                    log.debug("查询到上期贷款余额: {}, formmain_id: {}, yesterday: {}", lastLoanBalance, formmain_id, yesterday);

                    // 获取本期下柜资金（新数据字典：field0027 下柜资金）
                    Object currentPrincipalObj = record.get("field0027");
                    String currentPrincipal = (currentPrincipalObj != null) ? currentPrincipalObj.toString() : "0";

                    // 获取上期还本（新数据字典：field0029 还本）
                    log.debug("开始查询上期还本，formmain_id: {}, yesterday: {}", formmain_id, yesterday);
                    String lastPayment = dailyRepaymentPlanMapper.getRepaymentByFormmainIdAndTime(formmain_id,
                            yesterday.toString());
                    if (lastPayment == null || lastPayment.trim().isEmpty()) {
                        lastPayment = "0"; // 如果为空，默认为0
                    }
                    log.debug("查询到上期还本: {}, formmain_id: {}, yesterday: {}", lastPayment, formmain_id, yesterday);

                    // 全部用 BigDecimal 计算（金融场景）
                    BigDecimal lastBalance = parseBigDecimal(lastLoanBalance);
                    BigDecimal currentPrincipalBD = parseBigDecimal(currentPrincipal);
                    BigDecimal lastPaymentBD = parseBigDecimal(lastPayment);

                    // 计算本期贷款余额
                    BigDecimal principal = calculatePrincipal(lastBalance, currentPrincipalBD, lastPaymentBD);

                    // 计算本期付息：前一日余额 × 前一日年利率/360，18 位精度，写入时再舍入 9 位
                    log.debug("开始获取利率，loanSerialNo: {}, yesterday: {}", loanSerialNo, yesterday);
                    BigDecimal rateForDate = getRateForDate(loanSerialNo, yesterday);
                    log.debug("获取到利率: {}, loanSerialNo: {}, yesterday: {}", rateForDate.toPlainString(), loanSerialNo,
                            yesterday);
                    log.debug("开始计算利息，principalForInterest: {}, rateForDate: {}, time: {}",
                            lastBalance.toPlainString(), rateForDate.toPlainString(), time);
                    BigDecimal dailyInterest = calculateInterest(lastBalance, rateForDate, time, firstDisbursementDate);
                    log.debug("计算到利息: {}, time: {}", dailyInterest.toPlainString(), time);

                    // 写入时舍入 9 位小数
                    String formattedInterest = dailyInterest.setScale(9, RoundingMode.HALF_UP).toPlainString();

                    // 通过更新服务更新数据库（新数据字典：field0028是贷款余额，field0030是付息）
                    updateDataToDaily.updateDailyData(formmain_id, time, principal.toPlainString(), formattedInterest);

                    // 提前退出优化：迄今日 下柜合计=还本合计 且 后续无下柜，则后续日期均为0可跳过（必须看还本日，不能只用主表全表合计）
                    // 注意：还本日次日（recordDate+1）仍需付息（付的是还本日产生的利息），需多处理一天再批量置0
                    if (isLoanFullyRepaidBySumsUpToDate(detailTableRecords, recordDate)
                            && !hasFutureDisbursement(detailTableRecords, recordDate)) {
                        if (exitAfterNext) {
                            String nextDateStr = recordDate.plusDays(1).toString();
                            updateDataToDaily.batchUpdateZeroFromDate(formmain_id, nextDateStr);
                            int skippedCount = totalCount - processedCount;
                            log.info(
                                    "贷款已还清且无后续下柜（迄今日下柜合计=还本合计），从 {} 起批量置0并提前退出，跳过 {} 天计算，loanSerialNo: {}, formmain_id: {}",
                                    nextDateStr, skippedCount, loanSerialNo, formmain_id);
                            break;
                        } else {
                            exitAfterNext = true;
                            continue; // 多处理一天（还本日次日）以正确写入付息后再批量置0
                        }
                    }
                    exitAfterNext = false;

                } catch (Exception e) {
                    String timeStr = (time != null) ? time : "未知";
                    log.error("处理单条记录时发生错误，loanSerialNo: {}, 进度: {}/{}, time: {}",
                            loanSerialNo, processedCount, totalCount, timeStr, e);
                }
            }

            log.info("完成遍历明细表记录，loanSerialNo: {}, formmain_id: {}, 共处理: {} 条记录",
                    loanSerialNo, formmain_id, processedCount);
        } catch (Exception e) {
            log.error("计算并更新每日数据时发生错误，loanSerialNo: {}", loanSerialNo, e);
        }
    }

    /**
     * 解析利率字符串，转换为 BigDecimal（金融场景使用，避免 double 精度问题）
     * 支持格式：
     * 1. 小数格式（如：.470000 或 0.47）- 直接使用
     * 2. 百分比格式（如：4.7% 或 4.7）- 转换为小数
     */
    public BigDecimal parseInterestRateToBigDecimal(String interestRateStr) {
        if (interestRateStr == null || interestRateStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            String cleanRateStr = interestRateStr.trim();
            boolean isPercentage = cleanRateStr.contains("%");
            cleanRateStr = cleanRateStr.replace("%", "").trim();
            BigDecimal rate = new BigDecimal(cleanRateStr);
            if (isPercentage || (rate.compareTo(BigDecimal.ONE) > 0 && rate.compareTo(BigDecimal.valueOf(100)) <= 0)) {
                rate = rate.divide(BigDecimal.valueOf(100), 18, RoundingMode.HALF_UP);
            }
            return rate;
        } catch (Exception e) {
            log.error("解析利率失败: {}, 错误: {}", interestRateStr, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析利率字符串，转换为double（保留用于 InterestPeriod 等非计息路径）
     */
    public double parseInterestRate(String interestRateStr) {
        return parseInterestRateToBigDecimal(interestRateStr).doubleValue();
    }

    /**
     * 将字符串解析为 BigDecimal，用于金额/余额（金融场景）
     * null、空串或解析失败返回 BigDecimal.ZERO
     */
    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 计算整个表的数据，修改贷款余额和付息
     * 
     * @param loanSerialNo      贷款流水号
     * @param updateDataToDaily 更新服务，用于更新数据库
     */
    public void calculateAllData(String loanSerialNo, UpdateDataToDaily updateDataToDaily) {
        try {
            // 1. 根据流水号获取主表的贷款银行，贷款开始时间和贷款结束时间
            String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);

            if (formmain_id == null) {
                log.error("未找到流水号对应的主表记录: " + loanSerialNo);
                return;
            }

            Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(formmain_id);
            if (mainTableInfo == null) {
                log.error("未找到主表信息，formmain_id: " + formmain_id);
                return;
            }

            // 新数据字典：field0046 贷款银行，field0059 开始日期，field0060 结束日期，field0042 最新利率
            String loanBank = mainTableInfo.get("field0046") != null ? mainTableInfo.get("field0046").toString() : "";
            String loanStartTime = mainTableInfo.get("field0059") != null ? mainTableInfo.get("field0059").toString()
                    : "";
            String loanEndTime = mainTableInfo.get("field0060") != null ? mainTableInfo.get("field0060").toString()
                    : "";

            log.info("贷款信息 - 银行: {}, 开始时间: {}, 结束时间: {}", loanBank, loanStartTime, loanEndTime);

            // 2. 查询利率变更表，根据单据编号/流水号获取所有利率变更记录
            List<Map<String, Object>> interestChangeRecords = interestMapper
                    .getInterestChangesBySerialNumber(loanSerialNo);

            // log.info("查询利率变更记录 - 银行: " + loanBank + ", 开始时间: " + loanStartTime + ", 结束时间:
            // " + loanEndTime);
            // log.info("找到利率变更记录数: " + (interestChangeRecords != null ?
            // interestChangeRecords.size() : 0));
            if (interestChangeRecords != null && !interestChangeRecords.isEmpty()) {
                for (Map<String, Object> record : interestChangeRecords) {
                    log.info("利率变更记录: " + record);
                }
            }

            if (interestChangeRecords == null || interestChangeRecords.isEmpty()) {
                log.info("未找到利率变更记录，使用主表默认利率计算");
                // 如果没有利率变更记录，直接全量计算（会动态获取利率）
                calculateAndUpdateDailyData(loanSerialNo, updateDataToDaily);
                return;
            }

            // 3. 根据利率变更记录划分日期段，每个日期段使用对应的利率
            List<InterestPeriod> interestPeriods = buildInterestPeriods(interestChangeRecords, loanStartTime,
                    loanEndTime, updateDataToDaily);

            // log.info("共找到 " + interestPeriods.size() + " 个利率期间");
            for (InterestPeriod period : interestPeriods) {
                log.info("利率期间: " + period.getStartDate() + " 到 " + period.getEndDate() + ", 利率: "
                        + period.getInterestRate());
            }

            // 4. 按时间顺序计算每个利率期间的数据
            for (InterestPeriod period : interestPeriods) {
                try {
                    log.info("开始计算利率期间: " + period.getStartDate() + " 到 " + period.getEndDate() + ", 利率: "
                            + period.getInterestRate());

                    // 调用计算方法计算当前利率期间的数据
                    calculateAndUpdateDailyData(loanSerialNo, period.getStartDate(), period.getEndDate(),
                            period.getInterestRate(), updateDataToDaily);

                    log.info("完成利率期间计算: " + period.getStartDate() + " 到 " + period.getEndDate());

                } catch (Exception e) {
                    log.error("计算利率期间 {} 到 {} 时发生错误", period.getStartDate(), period.getEndDate(), e);
                }
            }

            log.info("所有利率期间计算完成，流水号: " + loanSerialNo);

        } catch (Exception e) {
            log.error("更新整个表数据时发生错误，loanSerialNo: {}", loanSerialNo, e);
        }
    }

    /**
     * 构建利率期间列表
     * 
     * @param interestChangeRecords 利率变更记录
     * @param loanStartTime         贷款开始时间
     * @param loanEndTime           贷款结束时间
     * @return 利率期间列表
     */

    private List<InterestPeriod> buildInterestPeriods(List<Map<String, Object>> interestChangeRecords,
            String loanStartTime, String loanEndTime, UpdateDataToDaily updateDataToDaily) {
        List<InterestPeriod> periods = new ArrayList<>();

        try {
            LocalDate loanStart = dateUtil.parseDate(loanStartTime);
            LocalDate loanEnd = dateUtil.parseDate(loanEndTime);

            if (loanStart == null || loanEnd == null) {
                return periods;
            }

            // 按变更时间排序
            interestChangeRecords.sort((a, b) -> {
                String dateA = a.get("change_date") != null ? a.get("change_date").toString() : "";
                String dateB = b.get("change_date") != null ? b.get("change_date").toString() : "";
                return dateA.compareTo(dateB);
            });

            LocalDate currentStart = loanStart;
            double currentRate = 0.0; // 初始利率

            // 如果没有利率变更记录，使用默认利率
            if (interestChangeRecords.isEmpty()) {
                InterestPeriod period = new InterestPeriod();
                period.setStartDate(loanStart.toString());
                period.setEndDate(loanEnd.toString());
                period.setInterestRate(currentRate);
                periods.add(period);
                return periods;
            }

            // 找到贷款开始时间之前的最新利率作为初始利率
            // log.info("开始查找初始利率，贷款开始时间: " + loanStart);
            for (Map<String, Object> record : interestChangeRecords) {
                String changeDateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                String interestRateStr = record.get("interest_rate") != null
                        ? record.get("interest_rate").toString()
                        : "0";
                LocalDate changeDate = dateUtil.parseDate(changeDateStr);
                // log.info(
                // "检查利率记录 - 变更日期: " + changeDateStr + ", 利率: " + interestRateStr + ", 解析后日期: "
                // + changeDate);
                if (changeDate != null && !changeDate.isAfter(loanStart)) {
                    currentRate = parseInterestRate(interestRateStr);
                    log.info("找到初始利率: {} (原始值: {})", currentRate, interestRateStr);
                }
            }

            // 如果没有找到贷款开始时间之前的利率，使用第一个利率变更记录的利率作为初始利率
            if (currentRate == 0.0 && !interestChangeRecords.isEmpty()) {
                Map<String, Object> firstRecord = interestChangeRecords.get(0);
                String firstRateStr = firstRecord.get("interest_rate") != null
                        ? firstRecord.get("interest_rate").toString()
                        : "0";
                currentRate = parseInterestRate(firstRateStr);
                log.info("使用第一个利率作为初始利率: {} (原始值: {})", currentRate, firstRateStr);
            }

            for (Map<String, Object> record : interestChangeRecords) {
                String changeDateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                String interestRateStr = record.get("interest_rate") != null ? record.get("interest_rate").toString()
                        : "0";

                LocalDate changeDate = dateUtil.parseDate(changeDateStr);
                if (changeDate == null) {
                    continue;
                }

                // 如果变更日期在贷款期间内
                if (!changeDate.isBefore(loanStart) && !changeDate.isAfter(loanEnd)) {
                    // 创建当前利率期间（从currentStart到变更日期前一天）
                    if (currentStart.isBefore(changeDate)) {
                        InterestPeriod period = new InterestPeriod();
                        period.setStartDate(currentStart.toString());
                        period.setEndDate(changeDate.minusDays(1).toString());
                        period.setInterestRate(currentRate);
                        periods.add(period);
                        log.info(
                                "创建利率期间: " + currentStart + " 到 " + changeDate.minusDays(1) + ", 利率: " + currentRate);
                    }

                    // 更新当前利率和开始日期
                    double rate = parseInterestRate(interestRateStr);
                    log.debug("利率变更：{} (原始值: {})", rate, interestRateStr);
                    currentRate = rate;
                    currentStart = changeDate;
                }
            }

            // 添加最后一个期间（从最后一个变更日期到贷款结束）
            if (!currentStart.isAfter(loanEnd)) {
                InterestPeriod lastPeriod = new InterestPeriod();
                lastPeriod.setStartDate(currentStart.toString());
                lastPeriod.setEndDate(loanEnd.toString());
                lastPeriod.setInterestRate(currentRate);
                periods.add(lastPeriod);
            }

        } catch (Exception e) {
            log.error("构建利率期间时发生错误，loanStartTime: {}, loanEndTime: {}", loanStartTime, loanEndTime, e);
        }

        return periods;
    }

    /**
     * 利率期间内部类
     */
    private static class InterestPeriod {
        private String startDate;
        private String endDate;
        private double interestRate;

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public double getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(double interestRate) {
            this.interestRate = interestRate;
        }
    }

    /**
     * 判断截至指定日期贷款是否已还清（不依赖贷款余额，适用于初始导入时贷款余额为0）
     * 条件：迄今日 下柜合计 = 迄今日 还本合计，必须看还本日是什么时候，不能只用主表全表合计
     * 否则会在下柜日就误判退出，导致还本日之前的利息全被置0
     *
     * @param detailTableRecords 全部明细记录
     * @param upToDate           截止日期（含），只累计此日期及之前的记录
     * @return true 表示迄今日已还清，可提前退出
     */
    private boolean isLoanFullyRepaidBySumsUpToDate(List<Map<String, Object>> detailTableRecords, LocalDate upToDate) {
        if (detailTableRecords == null || detailTableRecords.isEmpty()) {
            return false;
        }
        BigDecimal sumDisbursement = BigDecimal.ZERO;
        BigDecimal sumRepayment = BigDecimal.ZERO;
        for (Map<String, Object> r : detailTableRecords) {
            Object timeObj = r.get("field0026");
            if (timeObj == null)
                continue;
            LocalDate d = dateUtil.parseDate(timeObj.toString());
            if (d == null || d.isAfter(upToDate))
                continue;

            Object dispObj = r.get("field0027");
            Object repayObj = r.get("field0029");
            BigDecimal disp = parseBigDecimal(dispObj != null ? dispObj.toString() : null);
            BigDecimal repay = parseBigDecimal(repayObj != null ? repayObj.toString() : null);
            sumDisbursement = sumDisbursement.add(disp);
            sumRepayment = sumRepayment.add(repay);
        }
        BigDecimal diff = sumDisbursement.subtract(sumRepayment).abs();
        return diff.compareTo(new BigDecimal("0.000001")) < 0;
    }

    /**
     * 检查指定日期之后是否还有下柜记录
     * 贷款期限内可能先还清后又追加下柜（如一年后再次下柜100万），此时不能提前退出
     *
     * @param detailTableRecords 全部明细记录
     * @param afterDate          只检查此日期之后的记录
     * @return true 表示后续还有下柜，不能提前退出
     */
    private boolean hasFutureDisbursement(List<Map<String, Object>> detailTableRecords, LocalDate afterDate) {
        if (detailTableRecords == null || detailTableRecords.isEmpty()) {
            return false;
        }
        for (Map<String, Object> r : detailTableRecords) {
            Object timeObj = r.get("field0026");
            if (timeObj == null) {
                continue;
            }
            LocalDate d = dateUtil.parseDate(timeObj.toString());
            if (d == null || !d.isAfter(afterDate)) {
                continue;
            }
            Object dispObj = r.get("field0027");
            double disp = 0;
            if (dispObj != null && !dispObj.toString().trim().isEmpty()) {
                try {
                    disp = Double.parseDouble(dispObj.toString().trim());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            if (disp > 0.000001) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算付息——金融场景使用 BigDecimal，公式：付息 = 贷款余额 × (年利率÷360) × 1 天
     * 内部使用 18 位精度除法，不在本方法内舍入；舍入到 9 位由调用方在写入库时统一做。
     * 特殊情况：
     * 1. 尚未下柜则利息为 0
     * 2. 首次下柜当天不计息，从次日开始计息
     *
     * @param principal             前一日贷款余额
     * @param rate                  前一日对应年利率（小数）
     * @param endDate               计息结束日期（当天）
     * @param firstDisbursementDate 首次下柜日期
     * @return 当日付息（未舍入），不计息时返回 ZERO
     */
    private BigDecimal calculateInterest(BigDecimal principal, BigDecimal rate, String endDate,
            String firstDisbursementDate) {
        try {
            LocalDate end = dateUtil.parseDate(endDate);
            if (end == null) {
                log.error("日期解析失败，endDate: {}", endDate);
                return BigDecimal.ZERO;
            }
            if (firstDisbursementDate == null || firstDisbursementDate.trim().isEmpty()) {
                log.debug("尚未下柜，付息为0 - 日期: {}", endDate);
                return BigDecimal.ZERO;
            }
            LocalDate firstDisbursement = dateUtil.parseDate(firstDisbursementDate);
            if (firstDisbursement == null) {
                log.warn("首次下柜日期解析失败: {}", firstDisbursementDate);
                return BigDecimal.ZERO;
            }
            if (end.equals(firstDisbursement) || end.isBefore(firstDisbursement)) {
                log.debug("未到计息日，付息为0 - 日期: {}, 首次下柜: {}", endDate, firstDisbursementDate);
                return BigDecimal.ZERO;
            }
            if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0
                    || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            // 付息 = 贷款余额 × (年利率/360) × 1 天，18 位精度
            BigDecimal dailyInterest = principal
                    .multiply(rate)
                    .divide(BigDecimal.valueOf(360), 18, RoundingMode.HALF_UP);
            log.debug("利息计算 - 贷款余额: {}, 年利率: {}, 付息: {}, 日期: {}",
                    principal.toPlainString(), rate.toPlainString(), dailyInterest.toPlainString(), endDate);
            return dailyInterest;
        } catch (Exception e) {
            log.error("计算付息时发生错误，principal: {}, rate: {}, endDate: {}", principal, rate, endDate, e);
            return BigDecimal.ZERO;
        }
    }

    /***
     *
     */
    /**
     * 获取指定日期的利率（考虑利率变更），返回 BigDecimal（金融场景）
     * 查询该日期之前（包含该日期）的最新利率变更记录，与按季付息的逻辑保持一致
     *
     * @param loanSerialNo 贷款流水号
     * @param date         日期
     * @return 该日期对应的利率（小数格式，如 0.47）
     */
    private BigDecimal getRateForDate(String loanSerialNo, LocalDate date) {
        try {
            List<Map<String, Object>> allRecords = interestMapper.getInterestChangesBySerialNumber(loanSerialNo);
            BigDecimal rate = BigDecimal.ZERO;
            for (Map<String, Object> record : allRecords) {
                String changeDateStr = record.get("change_date") != null ? record.get("change_date").toString() : "";
                LocalDate changeDate = dateUtil.parseDate(changeDateStr);
                if (changeDate != null && !changeDate.isAfter(date)) {
                    String interestRateStr = record.get("interest_rate") != null
                            ? record.get("interest_rate").toString()
                            : "0";
                    rate = parseInterestRateToBigDecimal(interestRateStr);
                }
            }
            if (rate.compareTo(BigDecimal.ZERO) == 0) {
                String defaultRate = interestMapper.getInterest(loanSerialNo);
                if (defaultRate != null && !defaultRate.trim().isEmpty()) {
                    rate = parseInterestRateToBigDecimal(defaultRate);
                }
            }
            return rate;
        } catch (Exception e) {
            log.error("获取指定日期利率失败，loanSerialNo: {}, date: {}", loanSerialNo, date, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 计算当日贷款余额（BigDecimal，金融场景）
     * 公式：当前日贷款余额 = 上期贷款余额 + 当日下柜 - 上期还本
     */
    private BigDecimal calculatePrincipal(BigDecimal lastPrincipal, BigDecimal currentPrincipal,
            BigDecimal lastPayment) {
        if (lastPrincipal == null)
            lastPrincipal = BigDecimal.ZERO;
        if (currentPrincipal == null)
            currentPrincipal = BigDecimal.ZERO;
        if (lastPayment == null)
            lastPayment = BigDecimal.ZERO;
        return lastPrincipal.add(currentPrincipal).subtract(lastPayment);
    }

    /**
     * 计算每日贷款余额——double 重载（保留给 InterestPeriod 等非计息路径兼容）
     */
    private double calculatePrincipal(double lastPrincipal, double currentPrincipal, double lastPayment) {
        return lastPrincipal + currentPrincipal - lastPayment;
    }

}