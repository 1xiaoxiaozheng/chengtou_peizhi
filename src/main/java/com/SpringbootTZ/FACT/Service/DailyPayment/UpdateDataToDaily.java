package com.SpringbootTZ.FACT.Service.DailyPayment;

import com.SpringbootTZ.FACT.Config.LoanSerialNoLockManager;
import com.SpringbootTZ.FACT.Mapper.DailyRepaymentPlanMapper;
import com.SpringbootTZ.FACT.Mapper.seeyonMapper;
import com.SpringbootTZ.FACT.Service.ClientNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 负责所有数据库更新操作的服务类
 */
@Service
public class UpdateDataToDaily {

    private final DailyRepaymentPlanMapper dailyRepaymentPlanMapper;
    private final com.SpringbootTZ.FACT.Mapper.interestMapper interestMapper;
    private final LoanSerialNoLockManager lockManager;
    private final DailyRepaymentPlanService dailyRepaymentPlanService;
    private final DateUtil dateUtil;
    private final seeyonMapper seeyonMapper;

    private static final Logger log = LoggerFactory.getLogger(UpdateDataToDaily.class);

    @Autowired
    public UpdateDataToDaily(DailyRepaymentPlanMapper dailyRepaymentPlanMapper,
                            com.SpringbootTZ.FACT.Mapper.interestMapper interestMapper,
                            LoanSerialNoLockManager lockManager,
                            DailyRepaymentPlanService dailyRepaymentPlanService,
                            DateUtil dateUtil,
                            seeyonMapper seeyonMapper) {
        this.dailyRepaymentPlanMapper = dailyRepaymentPlanMapper;
        this.interestMapper = interestMapper;
        this.lockManager = lockManager;
        this.dailyRepaymentPlanService = dailyRepaymentPlanService;
        this.dateUtil = dateUtil;
        this.seeyonMapper = seeyonMapper;
    }

    /**
     * 更新主表的贷款余额合计（field0035）
     * 自动找到「今天或之后的第一个还款日」，并把那一天对应的贷款余额，显示在主表的贷款余额合计字段里。
     * 如果没有未来的还款日，就显示 0。
     *
     * @param loanSerialNo 贷款流水号
     */
    public void updateSummaryRowLoanBalance(String loanSerialNo) {
        try {
            log.info("开始更新主表贷款余额合计，loanSerialNo: {}", loanSerialNo);

            // 1. 根据流水号获取主表ID
            String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);
            if (formmain_id == null) {
                log.error("未找到流水号对应的主表记录: {}", loanSerialNo);
                return;
            }

            // 2. 获取当前日期（格式：yyyy-MM-dd）
            String currentDate = LocalDate.now().toString();
            log.debug("当前日期: {}", currentDate);

            // 3. 查询今天或之后的第一个还款日的贷款余额
            String loanBalance = dailyRepaymentPlanMapper.getLoanBalanceForFirstFutureDate(formmain_id, currentDate);

            // 4. 如果没有找到未来的还款日，设置为0
            if (loanBalance == null || loanBalance.trim().isEmpty() || "null".equals(loanBalance)) {
                loanBalance = "0";
                log.info("未找到今天或之后的还款日，主表贷款余额合计设置为0，formmain_id: {}", formmain_id);
            } else {
                log.info("找到今天或之后的第一个还款日，贷款余额: {}，formmain_id: {}", loanBalance, formmain_id);
            }

            // 5. 更新主表的贷款余额合计（field0035）
            dailyRepaymentPlanMapper.updateMainTableLoanBalanceSummary(formmain_id, loanBalance);
            log.info("主表贷款余额合计更新完成，loanBalance: {}，formmain_id: {}", loanBalance, formmain_id);

        } catch (Exception e) {
            log.error("更新主表贷款余额合计失败，loanSerialNo: {}", loanSerialNo, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 修复已有记录的sort和field0025（序号）
     * 兜底逻辑：处理业务关系提前插入日期记录的情况
     * 按日期排序，从1开始重新分配sort和field0025，确保连续且一致
     *
     * @param formmain_id 主表ID
     */
    public void fixExistingRecordsSortAndField0025(String formmain_id) {
        try {
            log.info("开始修复已有记录的sort和field0025，formmain_id: {}", formmain_id);

            // 1. 查询所有有日期的记录，按日期排序
            List<Map<String, Object>> existingRecords = dailyRepaymentPlanMapper
                    .getExistingDateRecordsOrdered(formmain_id);

            if (existingRecords == null || existingRecords.isEmpty()) {
                log.debug("没有需要修复的记录，formmain_id: {}", formmain_id);
                return;
            }

            log.info("找到 {} 条需要修复的记录，formmain_id: {}", existingRecords.size(), formmain_id);

            // 2. 从1开始，重新分配sort和field0025
            int sort = 1;
            for (Map<String, Object> record : existingRecords) {
                String recordId = record.get("id").toString();
                String dateStr = record.get("date_str") != null ? record.get("date_str").toString() : "";

                // 更新sort和field0025（序号等于排序号）
                dailyRepaymentPlanMapper.updateRecordSortAndField0025(recordId, sort);

                log.debug("已修复记录 - id: {}, date: {}, sort: {}, field0025: {}", recordId, dateStr, sort, sort);

                sort++;
            }

            log.info("完成修复，共修复 {} 条记录，sort范围: 1 - {}，formmain_id: {}", existingRecords.size(),
                    existingRecords.size(), formmain_id);

        } catch (Exception e) {
            log.error("修复已有记录的sort和field0025失败，formmain_id: {}", formmain_id, e);
            throw e; // 重新抛出异常，让调用方知道处理失败
        }
    }

    // 接收流水号，将该表的日期填充完整
    public void fillDate(String loanSerialNo) {
        // 获取formmain_id
        String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);

        if (formmain_id == null) {
            log.error("未找到流水号对应的主表记录: " + loanSerialNo);
            return;
        }

        // 是否属于IRR=是：不添加按日表日期，直接返回
        Map<String, Object> mainTableInfoForIrr = dailyRepaymentPlanMapper.getMainTableById(formmain_id);
        if (mainTableInfoForIrr != null) {
            Object field0067Obj = mainTableInfoForIrr.get("field0067");
            if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                if ("是".equals(irrShowValue)) {
                    log.info("是否属于IRR=是，跳过按日表日期填充，loanSerialNo: {}", loanSerialNo);
                    return;
                }
            }
        }

        // 即使已有明细，也需要补齐“缺失的日期行”
        // 典型场景：业务关系/导入会提前插入少量明细行（如下柜日、还款日），但并未覆盖“开始-结束”的每日行
        // 若直接跳过填充，会导致按日计息无法逐日计算（只剩零散日期，甚至触发提前退出置0）
        List<Map<String, Object>> existingRecords = dailyRepaymentPlanMapper.getDetailTableByFormmainId(formmain_id);
        if (existingRecords != null && !existingRecords.isEmpty()) {
            log.info("明细表记录已存在，将执行补齐缺失日期流程，formmain_id: {}, loanSerialNo: {}, existingCount: {}",
                    formmain_id, loanSerialNo, existingRecords.size());
        }

        // 获取开始时间和结束时间（新数据字典：field0059 开始日期，field0060 结束日期）
        Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(formmain_id);
        if (mainTableInfo == null || mainTableInfo.get("field0059") == null || mainTableInfo.get("field0060") == null) {
            log.error("主表信息不完整，无法填充日期，formmain_id: {}", formmain_id);
            return;
        }

        String startTimeStr = mainTableInfo.get("field0059").toString();
        String endTimeStr = mainTableInfo.get("field0060").toString();

        // 确保日期格式为 yyyy-MM-dd
        LocalDate startDate = dateUtil.parseDate(startTimeStr);
        LocalDate endDate = dateUtil.parseDate(endTimeStr);

        if (startDate == null || endDate == null) {
            log.error("日期解析失败，formmain_id: {}, startTimeStr: {}, endTimeStr: {}",
                    formmain_id, startTimeStr, endTimeStr);
            return;
        }

        // 格式化为 yyyy-MM-dd 格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        startTimeStr = startDate.format(formatter);
        endTimeStr = endDate.format(formatter);

        // 生成从开始时间到结束时间的每一天日期列表
        List<String> dateList = dateUtil.generateDateList(startTimeStr, endTimeStr);

        if (!dateList.isEmpty()) {
            try {
                // 1. 先删除field0026为空的记录（清理空行）
                try {
                    dailyRepaymentPlanMapper.deleteEmptyDateRecords(formmain_id);
                    log.info("已清理ID为 {} 的field0026为空记录", formmain_id);
                } catch (Exception deleteException) {
                    log.error("删除field0026为空记录失败，formmain_id: {}", formmain_id, deleteException);
                }

                // 2. 查询已存在的日期，过滤掉重复的日期（补齐缺失日期）
                List<String> existingDates = dailyRepaymentPlanMapper.getExistingDates(formmain_id);
                if (existingDates != null && !existingDates.isEmpty()) {
                    java.util.Set<String> existingDateSet = new java.util.HashSet<>(existingDates);
                    int beforeFilter = dateList.size();
                    dateList = dateList.stream()
                            .filter(date -> !existingDateSet.contains(date))
                            .collect(java.util.stream.Collectors.toList());
                    log.info("日期去重完成，原始 {} 条，已存在 {} 条，待补齐 {} 条，formmain_id: {}",
                            beforeFilter, existingDates.size(), dateList.size(), formmain_id);
                } else {
                    log.info("未发现已存在日期记录，将插入完整日期范围 {} 条，formmain_id: {}", dateList.size(), formmain_id);
                }

                if (dateList.isEmpty()) {
                    log.info("所有日期已存在，无需补齐，formmain_id: {}", formmain_id);
                    // 更新主表的是否添加明细表字段为"已添加"，避免重复扫描
                    dailyRepaymentPlanMapper.updateIsAddDetailFlag(formmain_id);
                    return;
                }

                // 3. 查询当前最大的sort值，从最大值+1开始（先插入，插入后再统一修复sort，保证按日期有序）
                Integer maxSort = dailyRepaymentPlanMapper.getMaxSort(formmain_id);
                if (maxSort == null) {
                    maxSort = 0;
                }
                Integer startSort = maxSort + 1;
                log.info("当前最大sort值: {}，将从 {} 开始插入缺失日期，formmain_id: {}", maxSort, startSort, formmain_id);

                // 4. 生成起始ID（使用ClientNumber生成）
                String startIdStr = ClientNumber.generateNumber();
                Long startId = Long.parseLong(startIdStr);

                // 5. 批量插入，每次100条（SQL Server插入条数限制）
                int batchSize = 100;
                int totalSize = dateList.size();
                int insertedCount = 0;

                log.info("准备补齐插入 {} 条明细记录，将分 {} 批插入（每批 {} 条），formmain_id: {}",
                        totalSize, (totalSize + batchSize - 1) / batchSize, batchSize, formmain_id);

                for (int i = 0; i < totalSize; i += batchSize) {
                    int endIndex = Math.min(i + batchSize, totalSize);
                    List<String> batchList = dateList.subList(i, endIndex);

                    Long currentBatchStartId = startId + i;
                    Integer currentBatchStartSort = startSort + i;

                    dailyRepaymentPlanMapper.addDetail(formmain_id, batchList, currentBatchStartId, currentBatchStartSort);
                    insertedCount += batchList.size();

                    log.debug("已补齐插入第 {} 批，共 {} 条，临时sort范围: {} - {}，累计 {} / {} 条，formmain_id: {}",
                            (i / batchSize + 1), batchList.size(),
                            currentBatchStartSort, currentBatchStartSort + batchList.size() - 1,
                            insertedCount, totalSize, formmain_id);
                }

                log.info("缺失日期插入完成，共插入 {} 条，formmain_id: {}", insertedCount, formmain_id);

                // 6. 插入后统一修复sort和field0025，确保按日期连续有序（否则补齐的“早期日期”可能被追加到末尾）
                try {
                    fixExistingRecordsSortAndField0025(formmain_id);
                    log.info("已完成补齐后的sort和field0025修复，formmain_id: {}", formmain_id);
                } catch (Exception fixException) {
                    log.error("补齐后修复sort和field0025失败，formmain_id: {}", formmain_id, fixException);
                }

                // 7. 更新主表的是否添加明细表字段为"已添加"
                dailyRepaymentPlanMapper.updateIsAddDetailFlag(formmain_id);

                // 8. 删除该formmain_id下所有字段都为null的明细表记录
                try {
                    dailyRepaymentPlanMapper.deleteEmptyDetailRecords(formmain_id);
                    log.info("已清理ID为 " + formmain_id + " 的空明细表记录");
                } catch (Exception deleteException) {
                    log.error("删除空明细表记录失败，formmain_id: {}", formmain_id, deleteException);
                }
            } catch (Exception e) {
                log.error("插入明细表记录失败，formmain_id: {}, loanSerialNo: {}", formmain_id, loanSerialNo, e);
            }
        }
    }

    /**
     * 更新明细表的贷款余额和付息
     * 使用范围条件避免对 field0026 做 CAST，便于走索引、减少锁竞争与死锁。
     * 遇死锁时自动重试最多 3 次。
     *
     * @param formmain_id 主表ID
     * @param dateStr     日期字符串（如 yyyy-MM-dd 或带时间，仅取日期部分）
     * @param loanBalance 贷款余额
     * @param interest    付息
     */
    @Retryable(
            value = DeadlockLoserDataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public void updateDailyData(String formmain_id, String dateStr, String loanBalance, String interest) {
        dailyRepaymentPlanMapper.updateDailyData(formmain_id, dateStr, loanBalance, interest);
    }

    /**
     * 批量将指定日期及之后的所有记录的贷款余额和付息设置为0
     * 用于提前退出优化：当某日贷款余额为0且无下柜时，后续日期无需逐日计算
     * 
     * @param formmain_id 主表ID
     * @param fromDate    起始日期（包含），格式 yyyy-MM-dd
     */
    public void batchUpdateZeroFromDate(String formmain_id, String fromDate) {
        dailyRepaymentPlanMapper.batchUpdateZeroFromDate(formmain_id, fromDate);
    }

    /**
     * 更新主表利率
     * 
     * @param loanSerialNo 贷款流水号
     * @param interestRate 利率
     */
    public void updateRateBySerialNumber(String loanSerialNo, String interestRate) {
        dailyRepaymentPlanMapper.updateRateBySerialNumber(interestRate, loanSerialNo);
    }

    /**
     * 接收流水号，修改还款计划表里面的利率，根据流水号定位到对应的记录，并update到数据库中
     * 
     * @param loanSerialNo 单据编号/流水号
     */
    public void updateInterestAndLoanSerialNo(String loanSerialNo) {
        try {
            // 根据单据编号/流水号查询利率表，获取最新利率
            // 利率表：formmain_0032，根据 field0001（单据编号）查询 field0042（利率）
            String interest = interestMapper.getInterest(loanSerialNo);

            if (interest == null || interest.trim().isEmpty()) {
                log.warn("未找到流水号对应的利率，loanSerialNo: {}", loanSerialNo);
                return;
            }

            // 利率格式是小数格式（如：.470000），直接使用
            // 修改主表的利率字段
            updateRateBySerialNumber(loanSerialNo, interest);
            log.info("已更新利率，loanSerialNo: {}, interest: {}", loanSerialNo, interest);

        } catch (Exception e) {
            log.error("更新利率失败，loanSerialNo: {}", loanSerialNo, e);
            throw e;
        }
    }

    /**
     * 每5分钟监听一次主表，并获取"是否添加明细表"字段，如果是null则需要给主表对应的明细表新增贷款期限（开始日期-结束日期）的行记录，并update到数据库中，接着修改该字段为"已添加"
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次 (5 * 60 * 1000 = 300000毫秒)
    public void monitorMainTableAndAddDetailRecords() {
        try {
            // 获取主表的所有id和是否添加明细表字段
            List<Map<String, Object>> mainTableRecords = dailyRepaymentPlanMapper.getAllIdAndIsAddDetail();

            for (Map<String, Object> record : mainTableRecords) {
                String id = record.get("id").toString();
                // 新数据字典：field0061 是否添加明细表日期
                String isAddDetail = record.get("field0061") != null ? record.get("field0061").toString() : null;

                // 检查是否添加明细表字段是否为null
                if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                    // 获取主表的开始时间和结束时间
                    Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(id);

                    // 新数据字典：field0059 开始日期，field0060 结束日期
                    if (mainTableInfo != null) {
                        // field0067 是否属于IRR=是：不添加按日表日期，直接标记已添加并跳过
                        Object field0067Obj = mainTableInfo.get("field0067");
                        if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                            String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                            if ("是".equals(irrShowValue)) {
                                dailyRepaymentPlanMapper.updateIsAddDetailFlag(id);
                                log.info("是否属于IRR=是，跳过按日表日期添加，id: {}", id);
                                continue;
                            }
                        }
                    }

                    // 新数据字典：field0059 开始日期，field0060 结束日期
                    if (mainTableInfo != null && mainTableInfo.get("field0059") != null
                            && mainTableInfo.get("field0060") != null) {
                        // 解析开始时间和结束时间（格式：yyyy-MM-dd）
                        String startTimeStr = mainTableInfo.get("field0059").toString();
                        String endTimeStr = mainTableInfo.get("field0060").toString();

                        // 确保日期格式为 yyyy-MM-dd（parseDate 会处理 DATETIME 格式，提取日期部分）
                        LocalDate startDate = dateUtil.parseDate(startTimeStr);
                        LocalDate endDate = dateUtil.parseDate(endTimeStr);

                        if (startDate == null || endDate == null) {
                            log.error("日期解析失败，id: {}, startTimeStr: {}, endTimeStr: {}",
                                    id, startTimeStr, endTimeStr);
                            continue;
                        }

                        // 格式化为 yyyy-MM-dd 格式
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        startTimeStr = startDate.format(formatter);
                        endTimeStr = endDate.format(formatter);

                        log.debug("解析日期 - id: {}, 开始日期: {}, 结束日期: {}", id, startTimeStr, endTimeStr);

                        // 生成从开始时间到结束时间的每一天日期列表（格式：yyyy-MM-dd）
                        List<String> dateList = dateUtil.generateDateList(startTimeStr, endTimeStr);

                        if (!dateList.isEmpty()) {
                            try {
                                // 1. 先删除field0026为空的记录（清理空行）
                                try {
                                    dailyRepaymentPlanMapper.deleteEmptyDateRecords(id);
                                    log.info("已清理ID为 {} 的field0026为空记录", id);
                                } catch (Exception deleteException) {
                                    log.error("删除field0026为空记录失败，id: {}", id, deleteException);
                                }

                                // 2. 查询已存在的日期，过滤掉重复的日期
                                List<String> existingDates = dailyRepaymentPlanMapper.getExistingDates(id);
                                if (existingDates != null && !existingDates.isEmpty()) {
                                    log.info("发现已有 {} 条日期记录，开始修复sort和field0025，id: {}", existingDates.size(), id);

                                    // 2.1 修复已有记录的sort和field0025（兜底逻辑：处理业务关系提前插入的情况）
                                    try {
                                        fixExistingRecordsSortAndField0025(id);
                                        log.info("已完成已有记录的sort和field0025修复，id: {}", id);
                                    } catch (Exception fixException) {
                                        log.error("修复已有记录的sort和field0025失败，id: {}", id, fixException);
                                        // 继续执行，不中断流程
                                    }

                                    // 2.2 将已存在的日期转换为Set以便快速查找
                                    java.util.Set<String> existingDateSet = new java.util.HashSet<>(existingDates);
                                    // 过滤掉已存在的日期
                                    dateList = dateList.stream()
                                            .filter(date -> !existingDateSet.contains(date))
                                            .collect(java.util.stream.Collectors.toList());

                                    if (dateList.isEmpty()) {
                                        log.info("所有日期已存在，跳过插入，id: {}", id);
                                        // 即使所有日期都存在，也更新标志位，避免重复检查
                                        dailyRepaymentPlanMapper.updateIsAddDetailFlag(id);
                                        continue;
                                    }
                                    log.info("过滤后剩余 {} 条新日期需要插入，id: {}", dateList.size(), id);
                                }

                                // 3. 查询当前最大的sort值，从最大值+1开始
                                Integer maxSort = dailyRepaymentPlanMapper.getMaxSort(id);
                                if (maxSort == null) {
                                    maxSort = 0;
                                }
                                Integer startSort = maxSort + 1;
                                log.info("当前最大sort值: {}，将从 {} 开始插入，id: {}", maxSort, startSort, id);

                                // 4. 生成起始ID（使用ClientNumber生成）
                                String startIdStr = ClientNumber.generateNumber();
                                Long startId = Long.parseLong(startIdStr);

                                // 5. 批量插入，每次100条（SQL Server插入条数限制）
                                int batchSize = 100;
                                int totalSize = dateList.size();
                                int insertedCount = 0;

                                log.info("准备插入 {} 条明细记录，将分 {} 批插入（每批 {} 条），id: {}",
                                        totalSize, (totalSize + batchSize - 1) / batchSize, batchSize, id);

                                // 分批插入
                                for (int i = 0; i < totalSize; i += batchSize) {
                                    int endIndex = Math.min(i + batchSize, totalSize);
                                    List<String> batchList = dateList.subList(i, endIndex);

                                    // 计算当前批次的起始ID和起始sort
                                    Long currentBatchStartId = startId + i;
                                    Integer currentBatchStartSort = startSort + i;

                                    // 插入当前批次（field0025 = sort）
                                    dailyRepaymentPlanMapper.addDetail(id, batchList, currentBatchStartId,
                                            currentBatchStartSort);
                                    insertedCount += batchList.size();

                                    log.debug("已插入第 {} 批，共 {} 条，sort范围: {} - {}，累计 {} / {} 条，id: {}",
                                            (i / batchSize + 1), batchList.size(),
                                            currentBatchStartSort, currentBatchStartSort + batchList.size() - 1,
                                            insertedCount, totalSize, id);
                                }

                                log.info("完成插入，共插入 {} 条明细记录，sort范围: {} - {}，id: {}",
                                        insertedCount, startSort, startSort + insertedCount - 1, id);

                                // 更新主表的是否添加明细表字段为"已添加"
                                dailyRepaymentPlanMapper.updateIsAddDetailFlag(id);

                                // 删除该formmain_id下所有字段都为null的明细表记录
                                try {
                                    dailyRepaymentPlanMapper.deleteEmptyDetailRecords(id);
                                    log.info("已清理ID为 " + id + " 的空明细表记录");
                                } catch (Exception deleteException) {
                                    log.error("删除空明细表记录失败，id: {}", id, deleteException);
                                }

                                log.info(
                                        "已为ID为 " + id + " 的记录添加了 " + dateList.size() + " 条明细表记录，起始ID: " + startId);
                            } catch (Exception e) {
                                log.error("插入明细表记录失败，id: {}", id, e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("监听主表并添加明细表记录时发生错误", e);
        }
    }

    /**
     * 根据利率变更更新指定日期范围的数据
     * 从变更日期开始，重新计算到贷款结束日期的数据
     * 
     * @param loanSerialNo 贷款流水号
     * @param changeDate   利率变更日期（格式：yyyy-MM-dd）
     * @param newRate      新利率（百分比字符串，如 "5.39%"）
     */
    public void updateDataFromDate(String loanSerialNo, String changeDate, String newRate) {
        // 获取锁，防止与用户打开表单时的计算请求产生并发冲突
        Object lock = lockManager.getLock(loanSerialNo);

        synchronized (lock) {
            try {
                log.info("开始更新指定日期范围数据，loanSerialNo: {}，changeDate: {}，newRate: {}",
                        loanSerialNo, changeDate, newRate);
                updateDataFromDateInternal(loanSerialNo, changeDate, newRate);
                log.info("完成更新指定日期范围数据，loanSerialNo: {}", loanSerialNo);
            } catch (Exception e) {
                log.error("更新指定日期范围数据失败，loanSerialNo: {}，changeDate: {}，newRate: {}",
                        loanSerialNo, changeDate, newRate, e);
                throw e;
            }
        }
    }

    /**
     * 内部方法：实际执行更新逻辑（不加锁，由外部方法加锁）
     */
    private void updateDataFromDateInternal(String loanSerialNo, String changeDate, String newRate) {
        try {
            // 1. 获取主表信息
            String formmain_id = dailyRepaymentPlanMapper.getMainTableIdBySerialNumber(loanSerialNo);
            if (formmain_id == null) {
                throw new RuntimeException("未找到流水号对应的主表记录: " + loanSerialNo);
            }

            Map<String, Object> mainTableInfo = dailyRepaymentPlanMapper.getMainTableById(formmain_id);
            if (mainTableInfo == null) {
                throw new RuntimeException("未找到主表信息，formmain_id: " + formmain_id);
            }

            // 新数据字典：field0059 开始日期，field0060 结束日期
            String loanStartTime = mainTableInfo.get("field0059") != null ? mainTableInfo.get("field0059").toString()
                    : "";
            String loanEndTime = mainTableInfo.get("field0060") != null ? mainTableInfo.get("field0060").toString()
                    : "";

            if (loanStartTime == null || loanStartTime.trim().isEmpty() ||
                    loanEndTime == null || loanEndTime.trim().isEmpty()) {
                throw new RuntimeException("贷款开始时间或结束时间为空，loanSerialNo: " + loanSerialNo);
            }

            // 2. 解析日期
            LocalDate changeLocalDate = dateUtil.parseDate(changeDate);
            LocalDate loanStartLocalDate = dateUtil.parseDate(loanStartTime);
            LocalDate loanEndLocalDate = dateUtil.parseDate(loanEndTime);

            if (changeLocalDate == null || loanStartLocalDate == null || loanEndLocalDate == null) {
                throw new RuntimeException("日期解析失败，changeDate: " + changeDate +
                        ", loanStartTime: " + loanStartTime + ", loanEndTime: " + loanEndTime);
            }

            // 3. 判断变更日期是否在贷款期间内
            if (changeLocalDate.isBefore(loanStartLocalDate)) {
                log.warn("利率变更日期早于贷款开始时间，跳过处理。loanSerialNo: {}, changeDate: {}, loanStartTime: {}",
                        loanSerialNo, changeDate, loanStartTime);
                return;
            }

            if (changeLocalDate.isAfter(loanEndLocalDate)) {
                log.warn("利率变更日期晚于贷款结束时间，跳过处理。loanSerialNo: {}, changeDate: {}, loanEndTime: {}",
                        loanSerialNo, changeDate, loanEndTime);
                return;
            }

            // 4. 更新主表利率（使用新利率）
            updateRateBySerialNumber(loanSerialNo, newRate);
            log.info("已更新主表利率，loanSerialNo: {}, newRate: {}", loanSerialNo, newRate);

            // 5. 从变更日期开始，重新计算到贷款结束日期的数据
            String startDate = changeLocalDate.toString();
            String endDate = loanEndLocalDate.toString();

            log.info("开始重新计算数据，loanSerialNo: {}, startDate: {}, endDate: {}",
                    loanSerialNo, startDate, endDate);

            // 调用计算服务进行计算，然后更新数据库
            dailyRepaymentPlanService.calculateAndUpdateDailyData(loanSerialNo, startDate, endDate, 0.0, this);

            log.info("数据重新计算完成，loanSerialNo: {}", loanSerialNo);

        } catch (Exception e) {
            log.error("更新指定日期范围数据失败（内部方法），loanSerialNo: {}, changeDate: {}, newRate: {}",
                    loanSerialNo, changeDate, newRate, e);
            throw e; // 重新抛出异常，让调用方知道处理失败
        }
    }

}
