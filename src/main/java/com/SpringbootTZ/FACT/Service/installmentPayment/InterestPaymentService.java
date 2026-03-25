package com.SpringbootTZ.FACT.Service.installmentPayment;

import com.SpringbootTZ.FACT.Mapper.InstallmentPaymentMapper;
import com.SpringbootTZ.FACT.Mapper.seeyonMapper;
import com.SpringbootTZ.FACT.Service.ClientNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分期还本付息服务
 * 定时扫描分期表，"是否添加明细表"
 *
 * 根据付息模式（按月付息、按季付息、按年付息）生成对应的还款日期
 */
@Service
public class InterestPaymentService {

    private static final Logger log = LoggerFactory.getLogger(InterestPaymentService.class);

    private final InstallmentPaymentMapper installmentPaymentMapper;
    private final seeyonMapper seeyonMapper;

    @Autowired
    public InterestPaymentService(InstallmentPaymentMapper installmentPaymentMapper, seeyonMapper seeyonMapper) {
        this.installmentPaymentMapper = installmentPaymentMapper;
        this.seeyonMapper = seeyonMapper;
    }

    /**
     * 定时扫描分期表，"是否添加明细表"
     * 
     * 每5分钟执行一次，扫描formmain_0039表
     * 如果field0043（是否添加明细表）为NULL，则根据还本模式和付息模式生成明细表日期
     * 
     * 仅处理还本模式为"分期还本"的情况
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次 (5 * 60 * 1000 = 300000毫秒)
    public void monitorMainTableAndAddDetailRecords() {
        try {
            // 获取主表的所有id和是否添加明细表字段
            List<Map<String, Object>> mainTableRecords = installmentPaymentMapper.getAllIdAndIsAddDetail();

            for (Map<String, Object> record : mainTableRecords) {
                String id = record.get("id").toString();
                // field0043 是否添加明细表
                String isAddDetail = record.get("field0043") != null ? record.get("field0043").toString() : null;

                // 检查是否添加明细表字段是否为null
                if (isAddDetail == null || "null".equals(isAddDetail) || "".equals(isAddDetail.trim())) {
                    try {
                        // 获取主表信息
                        Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableById(id);
                        if (mainTableInfo == null) {
                            log.error("未找到主表信息，id: {}", id);
                            continue;
                        }

                        // field0067 是否属于IRR=是：不添加日期，将是否添加明细表置为"是"后跳过
                        Object field0067Obj = mainTableInfo.get("field0067");
                        if (field0067Obj != null && !field0067Obj.toString().trim().isEmpty()) {
                            String irrShowValue = seeyonMapper.getEnumValue1(field0067Obj.toString().trim());
                            if ("是".equals(irrShowValue)) {
                                installmentPaymentMapper.updateField0043(id, "是");
                                log.info("是否属于IRR=是，跳过分期表日期添加，id: {}", id);
                                continue;
                            }
                        }

                        // field0009: 贷款开始日期, field0010: 贷款结束日期
                        if (mainTableInfo.get("field0009") == null || mainTableInfo.get("field0010") == null) {
                            log.error("主表日期信息不完整，id: {}", id);
                            continue;
                        }

                        // 获取还本模式和付息模式（枚举ID）
                        Object repaymentModeIdObj = mainTableInfo.get("field0013"); // 还本模式
                        Object interestModeIdObj = mainTableInfo.get("field0014"); // 付息模式
                        Object defaultPaymentDayObj = mainTableInfo.get("field0040"); // 默认还款日
                        Object defaultStartPaymentMonthObj = mainTableInfo.get("field0044"); // 默认开始还款月

                        if (repaymentModeIdObj == null || interestModeIdObj == null) {
                            log.error("还本模式或付息模式为空，id: {}", id);
                            continue;
                        }

                        // 通过枚举ID获取中文显示名
                        String repaymentMode = seeyonMapper.getEnumValue1(repaymentModeIdObj.toString());
                        String interestMode = seeyonMapper.getEnumValue1(interestModeIdObj.toString());

                        if (repaymentMode == null || interestMode == null) {
                            log.error("获取枚举值失败，id: {}, repaymentModeId: {}, interestModeId: {}",
                                    id, repaymentModeIdObj, interestModeIdObj);
                            continue;
                        }

                        // // 仅处理还本模式为"分期还本"的情况
                        // if (!"分期还本".equals(repaymentMode)) {
                        // log.debug("还本模式不是分期还本，跳过，id: {}, 还本模式: {}", id, repaymentMode);
                        // continue;
                        // }

                        // 分期付息模式由客户手动导入，直接跳过，不输出日志
                        if ("分期付息".equals(interestMode)) {
                            continue;
                        }

                        log.info("处理记录，id: {}, 还本模式: {}, 付息模式: {}", id, repaymentMode, interestMode);

                        // 解析开始时间和结束时间
                        String startTimeStr = mainTableInfo.get("field0009").toString();
                        String endTimeStr = mainTableInfo.get("field0010").toString();

                        LocalDate startDate = parseDate(startTimeStr);
                        LocalDate endDate = parseDate(endTimeStr);

                        if (startDate == null || endDate == null) {
                            log.error("日期解析失败，id: {}, startTimeStr: {}, endTimeStr: {}",
                                    id, startTimeStr, endTimeStr);
                            continue;
                        }

                        // 解析默认还款日
                        int defaultPaymentDay = 20; // 默认20号
                        if (defaultPaymentDayObj != null && !defaultPaymentDayObj.toString().trim().isEmpty()
                                && !"null".equals(defaultPaymentDayObj.toString())) {
                            try {
                                defaultPaymentDay = Integer.parseInt(defaultPaymentDayObj.toString().trim());
                            } catch (NumberFormatException e) {
                                log.warn("默认还款日解析失败，使用默认值20，id: {}, defaultPaymentDay: {}",
                                        id, defaultPaymentDayObj);
                            }
                        }

                        // 解析默认开始还款月（按季付息使用）
                        int defaultStartPaymentMonth = 3; // 默认3月（对应原来的3、6、9、12月）
                        if (defaultStartPaymentMonthObj != null
                                && !defaultStartPaymentMonthObj.toString().trim().isEmpty()
                                && !"null".equals(defaultStartPaymentMonthObj.toString())) {
                            try {
                                defaultStartPaymentMonth = Integer
                                        .parseInt(defaultStartPaymentMonthObj.toString().trim());
                                // 确保月份在1-12范围内
                                if (defaultStartPaymentMonth < 1 || defaultStartPaymentMonth > 12) {
                                    log.warn("默认开始还款月不在有效范围内(1-12)，使用默认值3，id: {}, defaultStartPaymentMonth: {}",
                                            id, defaultStartPaymentMonth);
                                    defaultStartPaymentMonth = 3;
                                }
                            } catch (NumberFormatException e) {
                                log.warn("默认开始还款月解析失败，使用默认值3，id: {}, defaultStartPaymentMonth: {}",
                                        id, defaultStartPaymentMonthObj);
                            }
                        }

                        // 根据付息模式生成日期列表
                        List<String> dateList = new ArrayList<>();
                        if ("按月付息".equals(interestMode)) {
                            dateList = generateMonthlyPaymentDates(startDate, endDate, defaultPaymentDay);
                        } else if ("按季付息".equals(interestMode)) {
                            // 按季付息：使用自定义开始还款月，每3个月一次
                            dateList = generateQuarterlyPaymentDatesFixed(startDate, endDate, defaultPaymentDay,
                                    defaultStartPaymentMonth);
                        } else if ("按年付息".equals(interestMode)) {
                            // 按年付息：使用默认开始还款月和默认还款日
                            dateList = generateYearlyPaymentDates(startDate, endDate, defaultPaymentDay,
                                    defaultStartPaymentMonth);
                        } else if ("按半年付息".equals(interestMode)) {
                            // 按半年付息：使用默认开始还款月和默认还款日，每6个月一次
                            log.info(
                                    "开始生成按半年付息日期，id: {}, startDate: {}, endDate: {}, defaultPaymentDay: {}, defaultStartPaymentMonth: {}",
                                    id, startDate, endDate, defaultPaymentDay, defaultStartPaymentMonth);
                            dateList = generateSemiAnnualPaymentDates(startDate, endDate, defaultPaymentDay,
                                    defaultStartPaymentMonth);
                            log.info("按半年付息日期生成完成，id: {}, 生成日期数量: {}", id, dateList.size());
                        } else {
                            log.warn("未知的付息模式: {}，id: {}", interestMode, id);
                            continue;
                        }

                        if (dateList.isEmpty()) {
                            log.info("未生成任何日期，跳过插入，id: {}", id);
                            // 即使没有生成日期，也更新标志位，避免重复检查
                            installmentPaymentMapper.updateIsAddDetailFlag(id);
                            continue;
                        }

                        log.info("生成 {} 条日期记录，id: {}", dateList.size(), id);

                        // 1. 先删除field0026为空的记录（清理空行）
                        try {
                            installmentPaymentMapper.deleteEmptyDateRecords(id);
                            log.info("已清理ID为 {} 的field0026为空记录", id);
                        } catch (Exception deleteException) {
                            log.error("删除field0026为空记录失败，id: {}", id, deleteException);
                        }

                        // 2. 查询已存在的日期，过滤掉重复的日期
                        List<String> existingDates = installmentPaymentMapper.getExistingDates(id);
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
                            Set<String> existingDateSet = new HashSet<>(existingDates);
                            // 过滤掉已存在的日期
                            dateList = dateList.stream()
                                    .filter(date -> !existingDateSet.contains(date))
                                    .collect(Collectors.toList());

                            if (dateList.isEmpty()) {
                                log.info("所有日期已存在，跳过插入，id: {}", id);
                                // 即使所有日期都存在，也更新标志位，避免重复检查
                                installmentPaymentMapper.updateIsAddDetailFlag(id);
                                continue;
                            }
                            log.info("过滤后剩余 {} 条新日期需要插入，id: {}", dateList.size(), id);
                        }

                        // 3. 查询当前最大的sort值，从最大值+1开始
                        Integer maxSort = installmentPaymentMapper.getMaxSort(id);
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

                        // 生成日期信息列表（包含日期和是否默认还款日）
                        // 判断逻辑：
                        // - 按月付息：看默认还款日（field0040），如果日期是该月的默认还款日，就是默认还款日
                        // - 按季、半年、按年：看默认开始还款月（field0044）和默认还款日（field0040），
                        // 如果日期是还款月且是该月的默认还款日，就是默认还款日
                        List<Map<String, Object>> dateInfoList = new ArrayList<>();
                        for (int i = 0; i < dateList.size(); i++) {
                            String date = dateList.get(i);
                            Map<String, Object> dateInfo = new HashMap<>();
                            dateInfo.put("id", startId + i);
                            dateInfo.put("date", date);
                            dateInfo.put("sort", startSort + i);

                            // 根据付息模式判断是否是默认还款日
                            boolean isDefaultDay = checkIsDefaultPaymentDay(date, interestMode, defaultPaymentDay,
                                    defaultStartPaymentMonth);
                            dateInfo.put("isDefaultPaymentDay", isDefaultDay ? "是" : "否");

                            dateInfoList.add(dateInfo);
                        }

                        log.info("准备插入 {} 条明细记录，将分 {} 批插入（每批 {} 条），id: {}",
                                totalSize, (totalSize + batchSize - 1) / batchSize, batchSize, id);

                        // 分批插入
                        for (int i = 0; i < totalSize; i += batchSize) {
                            int endIndex = Math.min(i + batchSize, totalSize);
                            List<Map<String, Object>> batchList = dateInfoList.subList(i, endIndex);

                            // 插入当前批次
                            installmentPaymentMapper.addDetail(id, batchList);
                            insertedCount += batchList.size();

                            log.debug("已插入第 {} 批，共 {} 条，sort范围: {} - {}，累计 {} / {} 条，id: {}",
                                    (i / batchSize + 1), batchList.size(),
                                    startSort + i, startSort + endIndex - 1,
                                    insertedCount, totalSize, id);
                        }

                        log.info("完成插入，共插入 {} 条明细记录，sort范围: {} - {}，id: {}",
                                insertedCount, startSort, startSort + insertedCount - 1, id);

                        // 全量更新所有明细记录的 field0047（是否默认还款日）
                        // 包括用户手动导入的记录（field0047 为 null）和刚插入的记录
                        try {
                            updateAllIsDefaultPaymentDay(id, interestMode, defaultPaymentDay, defaultStartPaymentMonth);
                            log.info("完成全量更新 field0047（是否默认还款日），id: {}", id);
                        } catch (Exception updateException) {
                            log.error("全量更新 field0047 失败，id: {}", id, updateException);
                            // 继续执行，不中断流程
                        }

                        // 更新主表的是否添加明细表字段为"已添加"
                        installmentPaymentMapper.updateIsAddDetailFlag(id);

                        log.info("已为ID为 {} 的记录添加了 {} 条明细表记录，起始ID: {}", id, insertedCount, startId);

                    } catch (Exception e) {
                        log.error("处理记录失败，id: {}", id, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("监听主表并添加明细表记录时发生错误", e);
        }
    }

    /**
     * 生成按月付息的日期列表
     * 贷款开始日期后的每个月的默认还款日
     * 若最后一个固定还款日 < end_date，补充 end_date 作为「最终结清日」
     * 
     * @param startDate         贷款开始日期
     * @param endDate           贷款结束日期
     * @param defaultPaymentDay 默认还款日（1-31）
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    private List<String> generateMonthlyPaymentDates(LocalDate startDate, LocalDate endDate, int defaultPaymentDay) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 添加保护：最多生成1000个日期（防止内存溢出）
        int maxDates = 1000;

        // 从开始日期的下一个月开始
        LocalDate currentDate = startDate.plusMonths(1);
        LocalDate lastPaymentDate = null;

        while (!currentDate.isAfter(endDate) && dateList.size() < maxDates) {
            // 计算该月的还款日
            LocalDate paymentDate = getPaymentDateInMonth(currentDate, defaultPaymentDay);

            // 如果还款日在结束日期之前或等于结束日期，则添加
            if (!paymentDate.isAfter(endDate)) {
                dateList.add(paymentDate.format(formatter));
                lastPaymentDate = paymentDate;
            }

            // 移动到下一个月
            currentDate = currentDate.plusMonths(1);
        }

        if (dateList.size() >= maxDates) {
            log.warn("按月付息日期生成达到最大数量限制({})，可能贷款期限过长。startDate: {}, endDate: {}",
                    maxDates, startDate, endDate);
        }

        // 若最后一个固定还款日 < end_date，补充 end_date 作为「最终结清日」
        if (lastPaymentDate != null && lastPaymentDate.isBefore(endDate)) {
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("添加贷款结束日期作为最终结清日: {}", endDateStr);
            }
        } else if (lastPaymentDate == null) {
            // 如果连第一个月的还款日都没生成（可能开始日期到下一个月之间不足一个月），也添加结束日期
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("贷款周期不足一个月，添加贷款结束日期作为最终结清日: {}", endDateStr);
            }
        }

        // 添加贷款开始日期作为第一个日期
        String startDateStr = startDate.format(formatter);
        if (!dateList.contains(startDateStr)) {
            dateList.add(0, startDateStr); // 插入到列表开头
            log.debug("添加贷款开始日期作为第一个日期: {}", startDateStr);
        }

        return dateList;
    }

    /**
     * 生成按季付息的日期列表（每3个月一次）
     * 从贷款开始日期起，每满3个月付息一次
     * 如果非完整周期，贷款结束日也要作为最后一笔非完整期利息的结清日
     * 
     * @param startDate         贷款开始日期
     * @param endDate           贷款结束日期
     * @param defaultPaymentDay 默认还款日（1-31）
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    private List<String> generateQuarterlyPaymentDates(LocalDate startDate, LocalDate endDate, int defaultPaymentDay) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 从开始日期开始，每3个月一次
        LocalDate currentDate = startDate;
        LocalDate lastPaymentDate = null;

        while (!currentDate.isAfter(endDate)) {
            // 计算3个月后的日期
            LocalDate nextQuarterDate = currentDate.plusMonths(3);

            // 如果3个月后的日期在结束日期之前或等于结束日期，则添加
            if (!nextQuarterDate.isAfter(endDate)) {
                // 计算该月的还款日
                LocalDate paymentDate = getPaymentDateInMonth(nextQuarterDate, defaultPaymentDay);

                // 如果还款日在结束日期之前或等于结束日期，则添加
                if (!paymentDate.isAfter(endDate)) {
                    dateList.add(paymentDate.format(formatter));
                    lastPaymentDate = paymentDate;
                }
            }

            // 移动到下一个季度
            currentDate = nextQuarterDate;
        }

        // 如果非完整周期（最后一个付息日到结束日期之间还有时间），添加结束日期作为最后一笔非完整期利息的结清日
        if (lastPaymentDate != null && lastPaymentDate.isBefore(endDate)) {
            // 检查结束日期是否已经存在于列表中
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("添加贷款结束日期作为最后一笔非完整期利息的结清日: {}", endDateStr);
            }
        } else if (lastPaymentDate == null && !startDate.plusMonths(3).isAfter(endDate)) {
            // 如果连第一个3个月周期都没到，但开始日期到结束日期之间不足3个月，也添加结束日期
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("贷款周期不足3个月，添加贷款结束日期作为结清日: {}", endDateStr);
            }
        }

        return dateList;
    }

    /**
     * 生成按季付息的日期列表（自定义开始还款月，每3个月一次）
     * 例如：如果默认开始还款月是2，那么还款月就是2、5、8、11（每3个月一次）
     * 再补充贷款结束日作为最后一笔非完整期利息的结清日
     * 
     * @param startDate                贷款开始日期
     * @param endDate                  贷款结束日期
     * @param defaultPaymentDay        默认还款日（1-31，如20表示每月20号）
     * @param defaultStartPaymentMonth 默认开始还款月（1-12，如2表示从2月开始，还款月为2、5、8、11）
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    private List<String> generateQuarterlyPaymentDatesFixed(LocalDate startDate, LocalDate endDate,
            int defaultPaymentDay, int defaultStartPaymentMonth) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 计算还款月序列（每3个月一次）
        // 例如：如果defaultStartPaymentMonth=2，那么序列是：2, 5, 8, 11
        int[] paymentMonths = new int[4];
        for (int i = 0; i < 4; i++) {
            int month = defaultStartPaymentMonth + i * 3;
            // 处理跨年情况，例如如果defaultStartPaymentMonth=11，那么11+3=14，需要转换为2
            paymentMonths[i] = ((month - 1) % 12) + 1;
        }

        // 找到第一个大于等于开始月份的还款月
        int currentYear = startDate.getYear();
        int currentMonth = startDate.getMonthValue();

        // 找到第一个符合条件的还款月
        boolean found = false;
        for (int paymentMonth : paymentMonths) {
            if (paymentMonth >= currentMonth) {
                currentMonth = paymentMonth;
                found = true;
                break;
            }
        }

        // 如果没找到，说明开始月份在所有还款月之后，使用下一年的第一个还款月
        if (!found) {
            currentYear++;
            currentMonth = paymentMonths[0];
        }

        // 如果当前月份就是还款月，检查开始日期是否在该还款日之前或当天
        // 如果开始日期在该还款日之后，则从下一个季度开始
        if (isPaymentMonth(currentMonth, paymentMonths)) {
            LocalDate firstQuarterDate = LocalDate.of(currentYear, currentMonth,
                    Math.min(defaultPaymentDay, LocalDate.of(currentYear, currentMonth, 1).lengthOfMonth()));
            if (startDate.isAfter(firstQuarterDate)) {
                // 开始日期在季度付息日之后，从下一个季度开始
                currentMonth = getNextPaymentMonth(currentMonth, paymentMonths);
                if (currentMonth < paymentMonths[0]) {
                    currentYear++;
                }
            }
        }

        // 生成所有季度的付息日期
        // 添加循环保护：最多生成1000个日期（防止内存溢出）和循环次数限制
        int maxDates = 1000; // 最多生成1000个日期
        int maxIterations = 10000; // 最多循环10000次，防止死循环
        int iterationCount = 0;

        while ((currentYear < endDate.getYear()
                || (currentYear == endDate.getYear() && currentMonth <= endDate.getMonthValue()))
                && dateList.size() < maxDates && iterationCount < maxIterations) {
            iterationCount++;

            // 检查当前月份是否是还款月
            if (isPaymentMonth(currentMonth, paymentMonths)) {
                // 检查日期是否有效（处理月末情况，如2月30日不存在）
                int maxDayInMonth = LocalDate.of(currentYear, currentMonth, 1).lengthOfMonth();
                int actualDay = Math.min(defaultPaymentDay, maxDayInMonth);
                LocalDate paymentDate = LocalDate.of(currentYear, currentMonth, actualDay);

                // 确保日期在贷款期间内（开始日期之后，结束日期之前或等于结束日期）
                if (!paymentDate.isBefore(startDate) && !paymentDate.isAfter(endDate)) {
                    dateList.add(paymentDate.format(formatter));
                }

                // 移动到下一个季度（+3个月）
                int nextMonth = getNextPaymentMonth(currentMonth, paymentMonths);
                if (nextMonth < currentMonth) {
                    // 跨年了
                    currentYear++;
                }
                currentMonth = nextMonth;

                // 安全检查：如果年份已经超过结束年份，强制退出
                if (currentYear > endDate.getYear()) {
                    break;
                }
            } else {
                // 如果不是还款月，直接跳到下一个还款月，而不是逐月递增
                // 找到下一个大于当前月份的还款月
                int nextPaymentMonth = -1;
                for (int paymentMonth : paymentMonths) {
                    if (paymentMonth > currentMonth) {
                        nextPaymentMonth = paymentMonth;
                        break;
                    }
                }

                if (nextPaymentMonth == -1) {
                    // 当前月份在所有还款月之后，使用下一年的第一个还款月
                    currentYear++;
                    currentMonth = paymentMonths[0];
                } else {
                    currentMonth = nextPaymentMonth;
                }

                // 安全检查：如果年份已经超过结束年份，强制退出
                if (currentYear > endDate.getYear()) {
                    break;
                }
            }
        }

        if (iterationCount >= maxIterations) {
            log.error(
                    "按季付息日期生成达到最大循环次数限制，可能存在死循环。startDate: {}, endDate: {}, defaultStartPaymentMonth: {}, currentYear: {}, currentMonth: {}",
                    startDate, endDate, defaultStartPaymentMonth, currentYear, currentMonth);
        }

        if (dateList.size() >= maxDates) {
            log.warn("按季付息日期生成达到最大数量限制({})，可能贷款期限过长。startDate: {}, endDate: {}",
                    maxDates, startDate, endDate);
        }

        // 补充贷款结束日作为最后一笔非完整期利息的结清日
        // 如果最后一个付息日到结束日期之间还有时间，添加结束日期
        if (!dateList.isEmpty()) {
            String lastPaymentDateStr = dateList.get(dateList.size() - 1);
            LocalDate lastPaymentDate = LocalDate.parse(lastPaymentDateStr);
            if (lastPaymentDate.isBefore(endDate)) {
                String endDateStr = endDate.format(formatter);
                if (!dateList.contains(endDateStr)) {
                    dateList.add(endDateStr);
                    log.debug("添加贷款结束日期作为最后一笔非完整期利息的结清日: {}", endDateStr);
                }
            }
        } else {
            // 如果没有生成任何季度付息日，但贷款周期存在，也添加结束日期
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("未生成季度付息日，添加贷款结束日期作为结清日: {}", endDateStr);
            }
        }

        // 添加贷款开始日期作为第一个日期
        String startDateStr = startDate.format(formatter);
        if (!dateList.contains(startDateStr)) {
            dateList.add(0, startDateStr); // 插入到列表开头
            log.debug("添加贷款开始日期作为第一个日期: {}", startDateStr);
        }

        return dateList;
    }

    /**
     * 判断指定月份是否是还款月
     * 
     * @param month         月份（1-12）
     * @param paymentMonths 还款月数组
     * @return true表示是还款月，false表示不是
     */
    private boolean isPaymentMonth(int month, int[] paymentMonths) {
        for (int paymentMonth : paymentMonths) {
            if (month == paymentMonth) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取下一个还款月
     * 
     * @param currentMonth  当前月份（1-12）
     * @param paymentMonths 还款月数组
     * @return 下一个还款月
     */
    private int getNextPaymentMonth(int currentMonth, int[] paymentMonths) {
        for (int i = 0; i < paymentMonths.length; i++) {
            if (paymentMonths[i] == currentMonth) {
                int nextIndex = (i + 1) % paymentMonths.length;
                return paymentMonths[nextIndex];
            }
        }
        // 如果当前月份不在还款月数组中，返回第一个还款月
        return paymentMonths[0];
    }

    /**
     * 生成按半年付息的日期列表
     * 使用默认开始还款月和默认还款日，每6个月一次
     * 
     * 例如：默认开始还款月=2，默认还款日=20，贷款开始日期=2024-01-01，结束日期=2027-12-31
     * 生成：2024-02-20, 2024-08-20, 2025-02-20, 2025-08-20, 2026-02-20, 2026-08-20,
     * 2027-02-20, 2027-08-20, 2027-12-31（贷款结束日）
     * 
     * 还款月计算：如果默认开始还款月=2，那么还款月就是2、8（2+6=8）
     * 如果默认开始还款月=5，那么还款月就是5、11（5+6=11）
     * 如果默认开始还款月=8，那么还款月就是8、2（8+6=14，跨年变成2）
     * 
     * @param startDate                贷款开始日期
     * @param endDate                  贷款结束日期
     * @param defaultPaymentDay        默认还款日（1-31）
     * @param defaultStartPaymentMonth 默认开始还款月（1-12）
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    private List<String> generateSemiAnnualPaymentDates(LocalDate startDate, LocalDate endDate,
            int defaultPaymentDay, int defaultStartPaymentMonth) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 计算还款月序列（每6个月一次，只有两个还款月）
        // 例如：如果defaultStartPaymentMonth=2，那么序列是：2, 8
        // 如果defaultStartPaymentMonth=5，那么序列是：5, 11
        // 如果defaultStartPaymentMonth=8，那么序列是：8, 2（跨年）
        int[] paymentMonths = new int[2];
        paymentMonths[0] = defaultStartPaymentMonth;
        int secondMonth = defaultStartPaymentMonth + 6;
        // 处理跨年情况，例如如果defaultStartPaymentMonth=8，那么8+6=14，需要转换为2
        paymentMonths[1] = ((secondMonth - 1) % 12) + 1;

        // 找到第一个大于等于开始月份的还款月
        int currentYear = startDate.getYear();
        int currentMonth = startDate.getMonthValue();

        // 找到第一个符合条件的还款月
        boolean found = false;
        for (int paymentMonth : paymentMonths) {
            if (paymentMonth >= currentMonth) {
                currentMonth = paymentMonth;
                found = true;
                break;
            }
        }

        // 如果没找到，说明开始月份在所有还款月之后，使用下一年的第一个还款月
        if (!found) {
            currentYear++;
            currentMonth = paymentMonths[0];
        }

        // 如果当前月份就是还款月，检查开始日期是否在该还款日之前或当天
        // 如果开始日期在该还款日之后，则从下一个半年开始
        if (isPaymentMonth(currentMonth, paymentMonths)) {
            LocalDate firstSemiAnnualDate = LocalDate.of(currentYear, currentMonth,
                    Math.min(defaultPaymentDay, LocalDate.of(currentYear, currentMonth, 1).lengthOfMonth()));
            if (startDate.isAfter(firstSemiAnnualDate)) {
                // 开始日期在半年付息日之后，从下一个半年开始
                currentMonth = getNextPaymentMonth(currentMonth, paymentMonths);
                if (currentMonth < paymentMonths[0]) {
                    currentYear++;
                }
            }
        }

        // 生成所有半年的付息日期
        // 优化循环逻辑：直接跳到下一个还款月，而不是逐月递增，避免死循环
        int maxIterations = 1000; // 设置最大循环次数，防止死循环
        int iterationCount = 0;

        while ((currentYear < endDate.getYear()
                || (currentYear == endDate.getYear() && currentMonth <= endDate.getMonthValue()))
                && iterationCount < maxIterations) {
            iterationCount++;

            // 检查当前月份是否是还款月
            if (isPaymentMonth(currentMonth, paymentMonths)) {
                // 检查日期是否有效（处理月末情况，如2月30日不存在）
                int maxDayInMonth = LocalDate.of(currentYear, currentMonth, 1).lengthOfMonth();
                int actualDay = Math.min(defaultPaymentDay, maxDayInMonth);
                LocalDate paymentDate = LocalDate.of(currentYear, currentMonth, actualDay);

                // 确保日期在贷款期间内（开始日期之后，结束日期之前或等于结束日期）
                if (!paymentDate.isBefore(startDate) && !paymentDate.isAfter(endDate)) {
                    dateList.add(paymentDate.format(formatter));
                }

                // 移动到下一个半年（+6个月）
                int nextPaymentMonth = getNextPaymentMonth(currentMonth, paymentMonths);

                // 计算下一个还款月的年份
                if (nextPaymentMonth < currentMonth) {
                    // 跨年了
                    currentYear++;
                }
                currentMonth = nextPaymentMonth;

                // 添加安全检查：如果年份已经超过结束年份，强制退出
                if (currentYear > endDate.getYear()) {
                    break;
                }
                // 如果年份等于结束年份，但月份已经超过结束月份，也退出
                if (currentYear == endDate.getYear() && currentMonth > endDate.getMonthValue()) {
                    break;
                }
            } else {
                // 如果不是还款月，直接跳到下一个还款月，而不是逐月递增
                // 找到下一个大于当前月份的还款月
                int nextPaymentMonth = -1;
                for (int paymentMonth : paymentMonths) {
                    if (paymentMonth > currentMonth) {
                        nextPaymentMonth = paymentMonth;
                        break;
                    }
                }

                if (nextPaymentMonth == -1) {
                    // 当前月份在所有还款月之后，使用下一年的第一个还款月
                    currentYear++;
                    currentMonth = paymentMonths[0];
                } else {
                    currentMonth = nextPaymentMonth;
                }

                // 添加安全检查：如果年份已经超过结束年份，强制退出
                if (currentYear > endDate.getYear()) {
                    break;
                }
                // 如果年份等于结束年份，但月份已经超过结束月份，也退出
                if (currentYear == endDate.getYear() && currentMonth > endDate.getMonthValue()) {
                    break;
                }
            }
        }

        if (iterationCount >= maxIterations) {
            log.error(
                    "按半年付息日期生成达到最大循环次数限制，可能存在死循环。startDate: {}, endDate: {}, defaultStartPaymentMonth: {}, currentYear: {}, currentMonth: {}",
                    startDate, endDate, defaultStartPaymentMonth, currentYear, currentMonth);
        }

        // 补充贷款结束日作为最后一笔非完整期利息的结清日
        // 如果最后一个付息日到结束日期之间还有时间，添加结束日期
        if (!dateList.isEmpty()) {
            String lastPaymentDateStr = dateList.get(dateList.size() - 1);
            LocalDate lastPaymentDate = LocalDate.parse(lastPaymentDateStr);
            if (lastPaymentDate.isBefore(endDate)) {
                String endDateStr = endDate.format(formatter);
                if (!dateList.contains(endDateStr)) {
                    dateList.add(endDateStr);
                    log.debug("添加贷款结束日期作为最后一笔非完整期利息的结清日: {}", endDateStr);
                }
            }
        } else {
            // 如果没有生成任何半年付息日，但贷款周期存在，也添加结束日期
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("未生成半年付息日，添加贷款结束日期作为结清日: {}", endDateStr);
            }
        }

        // 添加贷款开始日期作为第一个日期
        String startDateStr = startDate.format(formatter);
        if (!dateList.contains(startDateStr)) {
            dateList.add(0, startDateStr); // 插入到列表开头
            log.debug("添加贷款开始日期作为第一个日期: {}", startDateStr);
        }

        return dateList;
    }

    /**
     * 生成按年付息的日期列表
     * 使用默认开始还款月和默认还款日，每年同一天
     * 
     * 例如：默认开始还款月=5，默认还款日=20，贷款开始日期=2024-01-01，结束日期=2027-12-31
     * 生成：2025-05-20, 2026-05-20, 2027-05-20, 2027-12-31（贷款结束日）
     * 
     * @param startDate                贷款开始日期
     * @param endDate                  贷款结束日期
     * @param defaultPaymentDay        默认还款日（1-31）
     * @param defaultStartPaymentMonth 默认开始还款月（1-12）
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    private List<String> generateYearlyPaymentDates(LocalDate startDate, LocalDate endDate,
            int defaultPaymentDay, int defaultStartPaymentMonth) {
        List<String> dateList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 添加保护：最多生成1000个日期（防止内存溢出）
        int maxDates = 1000;

        // 使用默认开始还款月和默认还款日
        int paymentMonth = defaultStartPaymentMonth;
        int paymentDay = defaultPaymentDay;

        // 从开始日期的下一年开始
        int startYear = startDate.getYear();
        int currentYear = startYear + 1;

        while (currentYear <= endDate.getYear() && dateList.size() < maxDates) {
            // 尝试创建该年的还款日（每年同一天）
            LocalDate paymentDate;
            try {
                // 检查日期是否有效（处理月末情况，如2月30日不存在）
                int maxDayInMonth = LocalDate.of(currentYear, paymentMonth, 1).lengthOfMonth();
                int actualDay = Math.min(paymentDay, maxDayInMonth);
                paymentDate = LocalDate.of(currentYear, paymentMonth, actualDay);
            } catch (Exception e) {
                // 如果日期无效，则使用该月的最后一天
                paymentDate = LocalDate.of(currentYear, paymentMonth, 1)
                        .withDayOfMonth(LocalDate.of(currentYear, paymentMonth, 1).lengthOfMonth());
            }

            // 如果还款日在开始日期之后，结束日期之前或等于结束日期，则添加
            if (!paymentDate.isBefore(startDate) && !paymentDate.isAfter(endDate)) {
                dateList.add(paymentDate.format(formatter));
            }

            // 移动到下一年
            currentYear++;
        }

        if (dateList.size() >= maxDates) {
            log.warn("按年付息日期生成达到最大数量限制({})，可能贷款期限过长。startDate: {}, endDate: {}",
                    maxDates, startDate, endDate);
        }

        // 补充贷款结束日作为最后一笔非完整期利息的结清日
        // 如果最后一个付息日到结束日期之间还有时间，添加结束日期
        if (!dateList.isEmpty()) {
            String lastPaymentDateStr = dateList.get(dateList.size() - 1);
            LocalDate lastPaymentDate = LocalDate.parse(lastPaymentDateStr);
            if (lastPaymentDate.isBefore(endDate)) {
                String endDateStr = endDate.format(formatter);
                if (!dateList.contains(endDateStr)) {
                    dateList.add(endDateStr);
                    log.debug("添加贷款结束日期作为最后一笔非完整期利息的结清日: {}", endDateStr);
                }
            }
        } else {
            // 如果没有生成任何按年付息日，但贷款周期存在，也添加结束日期
            String endDateStr = endDate.format(formatter);
            if (!dateList.contains(endDateStr)) {
                dateList.add(endDateStr);
                log.debug("未生成按年付息日，添加贷款结束日期作为结清日: {}", endDateStr);
            }
        }

        // 添加贷款开始日期作为第一个日期
        String startDateStr = startDate.format(formatter);
        if (!dateList.contains(startDateStr)) {
            dateList.add(0, startDateStr); // 插入到列表开头
            log.debug("添加贷款开始日期作为第一个日期: {}", startDateStr);
        }

        return dateList;
    }

    /**
     * 获取指定月份中的还款日
     * 如果指定日期超过该月的最大天数，则使用该月的最后一天
     * 
     * @param monthDate  月份日期（任意一天都可以）
     * @param paymentDay 还款日（1-31）
     * @return 该月的还款日
     */
    private LocalDate getPaymentDateInMonth(LocalDate monthDate, int paymentDay) {
        int year = monthDate.getYear();
        int month = monthDate.getMonthValue();

        // 获取该月的最大天数
        int maxDayInMonth = monthDate.lengthOfMonth();

        // 如果指定的还款日超过该月的最大天数，则使用该月的最后一天
        int actualDay = Math.min(paymentDay, maxDayInMonth);

        return LocalDate.of(year, month, actualDay);
    }

    /**
     * 修复已有记录的sort和field0025（序号）
     * 兜底逻辑：处理业务关系提前插入日期记录的情况
     * 按日期排序，从1开始重新分配sort和field0025，确保连续且一致
     * 
     * @param formmain_id 主表ID
     */
    private void fixExistingRecordsSortAndField0025(String formmain_id) {
        try {
            log.info("开始修复已有记录的sort和field0025，formmain_id: {}", formmain_id);

            // 1. 查询所有有日期的记录，按日期排序
            List<Map<String, Object>> existingRecords = installmentPaymentMapper
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
                installmentPaymentMapper.updateRecordSortAndField0025(recordId, sort);

                log.debug("已修复记录 - id: {}, date: {}, sort: {}, field0025: {}", recordId, dateStr, sort, sort);

                sort++;
            }

            log.info("完成修复，共修复 {} 条记录，sort范围: 1 - {}，formmain_id: {}", existingRecords.size(),
                    existingRecords.size(), formmain_id);

        } catch (Exception e) {
            log.error("修复已有记录的sort和field0025失败，formmain_id: {}", formmain_id, e);
            throw e;
        }
    }

    /**
     * 根据单号（流水号）生成分期表的日期列表
     * 用于模拟付息自定义控件，返回该单号对应的所有还款日期
     * 
     * @param serialNumber 单据编号（流水号）
     * @return 日期字符串列表，格式为 yyyy-MM-dd，如果查询失败或没有数据返回空列表
     */
    public List<String> generatePaymentDatesBySerialNumber(String serialNumber) {
        List<String> dateList = new ArrayList<>();

        try {
            log.info("开始生成日期列表，serialNumber: {}", serialNumber);

            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                log.warn("单号为空，无法生成日期列表");
                return dateList;
            }

            // 1. 根据单号获取主表ID
            log.info("步骤1: 根据单号获取主表ID，serialNumber: {}", serialNumber);
            String mainTableId = installmentPaymentMapper.getMainTableIdBySerialNumber(serialNumber);
            log.info("步骤1完成: 获取到主表ID: {}", mainTableId);

            if (mainTableId == null || mainTableId.trim().isEmpty()) {
                log.warn("未找到单号对应的分期表记录，serialNumber: {}", serialNumber);
                return dateList;
            }

            // 2. 获取主表信息
            log.info("步骤2: 获取主表信息，mainTableId: {}", mainTableId);
            Map<String, Object> mainTableInfo = installmentPaymentMapper.getMainTableById(mainTableId);
            log.info("步骤2完成: 获取到主表信息，字段数量: {}", mainTableInfo != null ? mainTableInfo.size() : 0);

            if (mainTableInfo == null) {
                log.warn("未找到主表信息，mainTableId: {}, serialNumber: {}", mainTableId, serialNumber);
                return dateList;
            }

            // 3. 检查日期信息
            log.info("步骤3: 检查日期信息");
            if (mainTableInfo.get("field0009") == null || mainTableInfo.get("field0010") == null) {
                log.warn("主表日期信息不完整，mainTableId: {}, serialNumber: {}", mainTableId, serialNumber);
                return dateList;
            }
            log.info("步骤3完成: 日期信息完整");

            // 4. 获取付息模式（枚举ID）
            log.info("步骤4: 获取付息模式");
            Object interestModeIdObj = mainTableInfo.get("field0014"); // 付息模式
            if (interestModeIdObj == null) {
                log.warn("付息模式为空，mainTableId: {}, serialNumber: {}", mainTableId, serialNumber);
                return dateList;
            }
            log.info("步骤4完成: 付息模式枚举ID: {}", interestModeIdObj);

            // 5. 通过枚举ID获取中文显示名
            log.info("步骤5: 通过枚举ID获取中文显示名，interestModeId: {}", interestModeIdObj);
            String interestMode = null;
            try {
                interestMode = seeyonMapper.getEnumValue1(interestModeIdObj.toString());
                log.info("步骤5完成: 获取到付息模式: {}", interestMode);
            } catch (Exception e) {
                log.error("步骤5失败: 获取付息模式枚举值异常，interestModeId: {}", interestModeIdObj, e);
                return dateList;
            }

            if (interestMode == null) {
                log.warn("获取付息模式枚举值失败，mainTableId: {}, serialNumber: {}, interestModeId: {}",
                        mainTableId, serialNumber, interestModeIdObj);
                return dateList;
            }

            // 6. 分期付息模式由客户手动导入，返回空列表
            if ("分期付息".equals(interestMode)) {
                log.debug("付息模式为分期付息，返回空列表，serialNumber: {}", serialNumber);
                return dateList;
            }

            // 7. 解析开始时间和结束时间
            String startTimeStr = mainTableInfo.get("field0009").toString();
            String endTimeStr = mainTableInfo.get("field0010").toString();

            LocalDate startDate = parseDate(startTimeStr);
            LocalDate endDate = parseDate(endTimeStr);

            if (startDate == null || endDate == null) {
                log.warn("日期解析失败，mainTableId: {}, serialNumber: {}, startTimeStr: {}, endTimeStr: {}",
                        mainTableId, serialNumber, startTimeStr, endTimeStr);
                return dateList;
            }

            // 8. 解析默认还款日和默认开始还款月
            Object defaultPaymentDayObj = mainTableInfo.get("field0040"); // 默认还款日
            Object defaultStartPaymentMonthObj = mainTableInfo.get("field0044"); // 默认开始还款月

            int defaultPaymentDay = 20; // 默认20号
            if (defaultPaymentDayObj != null && !defaultPaymentDayObj.toString().trim().isEmpty()
                    && !"null".equals(defaultPaymentDayObj.toString())) {
                try {
                    defaultPaymentDay = Integer.parseInt(defaultPaymentDayObj.toString().trim());
                } catch (NumberFormatException e) {
                    log.warn("默认还款日解析失败，使用默认值20，serialNumber: {}, defaultPaymentDay: {}",
                            serialNumber, defaultPaymentDayObj);
                }
            }

            int defaultStartPaymentMonth = 3; // 默认3月
            if (defaultStartPaymentMonthObj != null
                    && !defaultStartPaymentMonthObj.toString().trim().isEmpty()
                    && !"null".equals(defaultStartPaymentMonthObj.toString())) {
                try {
                    defaultStartPaymentMonth = Integer.parseInt(defaultStartPaymentMonthObj.toString().trim());
                    if (defaultStartPaymentMonth < 1 || defaultStartPaymentMonth > 12) {
                        defaultStartPaymentMonth = 3;
                    }
                } catch (NumberFormatException e) {
                    log.warn("默认开始还款月解析失败，使用默认值3，serialNumber: {}, defaultStartPaymentMonth: {}",
                            serialNumber, defaultStartPaymentMonthObj);
                }
            }

            // 9. 根据付息模式生成日期列表
            log.info("步骤9: 根据付息模式生成日期列表，付息模式: {}, 开始日期: {}, 结束日期: {}",
                    interestMode, startDate, endDate);

            if ("按月付息".equals(interestMode)) {
                log.info("调用按月付息日期生成方法");
                dateList = generateMonthlyPaymentDates(startDate, endDate, defaultPaymentDay);
            } else if ("按季付息".equals(interestMode)) {
                log.info("调用按季付息日期生成方法");
                dateList = generateQuarterlyPaymentDatesFixed(startDate, endDate, defaultPaymentDay,
                        defaultStartPaymentMonth);
            } else if ("按年付息".equals(interestMode)) {
                log.info("调用按年付息日期生成方法");
                dateList = generateYearlyPaymentDates(startDate, endDate, defaultPaymentDay,
                        defaultStartPaymentMonth);
            } else if ("按半年付息".equals(interestMode)) {
                log.info("调用按半年付息日期生成方法");
                dateList = generateSemiAnnualPaymentDates(startDate, endDate, defaultPaymentDay,
                        defaultStartPaymentMonth);
            } else {
                log.warn("未知的付息模式: {}，serialNumber: {}", interestMode, serialNumber);
                return dateList;
            }

            log.info("步骤9完成: 成功生成日期列表，serialNumber: {}, 付息模式: {}, 日期数量: {}",
                    serialNumber, interestMode, dateList.size());

        } catch (Exception e) {
            log.error("根据单号生成日期列表失败，serialNumber: {}", serialNumber, e);
            e.printStackTrace(); // 打印完整堆栈信息
        }

        log.info("方法执行完成，返回日期列表，数量: {}", dateList.size());
        return dateList;
    }

    /**
     * 解析日期字符串，转换为LocalDate
     * 支持多种格式：yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd HH:mm:ss.SSS等
     * 
     * @param dateStr 日期字符串
     * @return LocalDate对象，解析失败返回null
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // 处理可能的格式：yyyy-MM-dd HH:mm:ss, yyyy-MM-dd, yyyy/MM/dd等
            String cleanDateStr = dateStr.trim();

            // 如果包含时间部分，只取日期部分（处理空格、T、小数点等）
            if (cleanDateStr.contains(" ")) {
                cleanDateStr = cleanDateStr.split(" ")[0];
            }
            // 处理ISO格式的时间（如：2025-09-30T10:42）
            if (cleanDateStr.contains("T")) {
                cleanDateStr = cleanDateStr.split("T")[0];
            }
            // 处理可能的小数点（如：2026-09-21 00:00:00.0）
            if (cleanDateStr.contains(".")) {
                cleanDateStr = cleanDateStr.split("\\.")[0];
            }

            // 替换可能的斜杠为横杠
            cleanDateStr = cleanDateStr.replace("/", "-");

            // 确保格式为 yyyy-MM-dd
            if (cleanDateStr.length() > 10) {
                cleanDateStr = cleanDateStr.substring(0, 10);
            }

            // 尝试解析
            return LocalDate.parse(cleanDateStr);
        } catch (Exception e) {
            log.error("解析日期失败: " + dateStr + ", 错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 判断某个日期是否是默认还款日
     * 
     * @param dateStr                  日期字符串（格式：yyyy-MM-dd）
     * @param interestMode             付息模式（按月付息、按季付息、按年付息、按半年付息）
     * @param defaultPaymentDay        默认还款日（1-31）
     * @param defaultStartPaymentMonth 默认开始还款月（1-12，用于按季、半年、按年付息）
     * @return true表示是默认还款日，false表示不是
     */
    private boolean checkIsDefaultPaymentDay(String dateStr, String interestMode, int defaultPaymentDay,
            int defaultStartPaymentMonth) {
        try {
            // 解析日期
            LocalDate date = parseDate(dateStr);
            if (date == null) {
                return false;
            }

            int dayOfMonth = date.getDayOfMonth();
            int month = date.getMonthValue();

            // 按月付息：只看默认还款日
            if ("按月付息".equals(interestMode)) {
                // 检查日期是否是该月的默认还款日
                int maxDayInMonth = date.lengthOfMonth();
                int actualPaymentDay = Math.min(defaultPaymentDay, maxDayInMonth);
                return dayOfMonth == actualPaymentDay;
            }

            // 按季付息：看默认开始还款月和默认还款日
            if ("按季付息".equals(interestMode)) {
                // 计算还款月序列（每3个月一次）
                int[] paymentMonths = new int[4];
                for (int i = 0; i < 4; i++) {
                    int paymentMonth = defaultStartPaymentMonth + i * 3;
                    paymentMonths[i] = ((paymentMonth - 1) % 12) + 1;
                }

                // 检查是否是还款月
                boolean isPaymentMonth = false;
                for (int paymentMonth : paymentMonths) {
                    if (month == paymentMonth) {
                        isPaymentMonth = true;
                        break;
                    }
                }

                if (!isPaymentMonth) {
                    return false;
                }

                // 检查日期是否是该月的默认还款日
                int maxDayInMonth = date.lengthOfMonth();
                int actualPaymentDay = Math.min(defaultPaymentDay, maxDayInMonth);
                return dayOfMonth == actualPaymentDay;
            }

            // 按半年付息：看默认开始还款月和默认还款日
            if ("按半年付息".equals(interestMode)) {
                // 计算还款月序列（每6个月一次，只有两个还款月）
                int[] paymentMonths = new int[2];
                paymentMonths[0] = defaultStartPaymentMonth;
                int secondMonth = defaultStartPaymentMonth + 6;
                paymentMonths[1] = ((secondMonth - 1) % 12) + 1;

                // 检查是否是还款月
                boolean isPaymentMonth = false;
                for (int paymentMonth : paymentMonths) {
                    if (month == paymentMonth) {
                        isPaymentMonth = true;
                        break;
                    }
                }

                if (!isPaymentMonth) {
                    return false;
                }

                // 检查日期是否是该月的默认还款日
                int maxDayInMonth = date.lengthOfMonth();
                int actualPaymentDay = Math.min(defaultPaymentDay, maxDayInMonth);
                return dayOfMonth == actualPaymentDay;
            }

            // 按年付息：看默认开始还款月和默认还款日
            if ("按年付息".equals(interestMode)) {
                // 检查是否是默认开始还款月
                if (month != defaultStartPaymentMonth) {
                    return false;
                }

                // 检查日期是否是该月的默认还款日
                int maxDayInMonth = date.lengthOfMonth();
                int actualPaymentDay = Math.min(defaultPaymentDay, maxDayInMonth);
                return dayOfMonth == actualPaymentDay;
            }

            // 其他付息模式，默认返回false
            return false;

        } catch (Exception e) {
            log.error("判断是否是默认还款日失败，dateStr: {}, interestMode: {}", dateStr, interestMode, e);
            return false;
        }
    }

    /**
     * 全量更新所有明细记录的 field0047（是否默认还款日）
     * 包括用户手动导入的记录（field0047 为 null）和刚插入的记录
     * 
     * @param formmain_id              主表ID
     * @param interestMode             付息模式（按月付息、按季付息、按年付息、按半年付息）
     * @param defaultPaymentDay        默认还款日（1-31）
     * @param defaultStartPaymentMonth 默认开始还款月（1-12，用于按季、半年、按年付息）
     */
    private void updateAllIsDefaultPaymentDay(String formmain_id, String interestMode, int defaultPaymentDay,
            int defaultStartPaymentMonth) {
        try {
            log.info("开始全量更新 field0047（是否默认还款日），formmain_id: {}, interestMode: {}", formmain_id,
                    interestMode);

            // 1. 获取所有明细记录（包括所有字段）
            List<Map<String, Object>> allDetailRecords = installmentPaymentMapper.getAllPaymentDates(formmain_id);
            if (allDetailRecords == null || allDetailRecords.isEmpty()) {
                log.debug("未找到明细记录，formmain_id: {}", formmain_id);
                return;
            }

            log.debug("找到 {} 条明细记录，开始全量更新 field0047，formmain_id: {}", allDetailRecords.size(),
                    formmain_id);

            int updateCount = 0;
            int skipCount = 0;

            // 2. 遍历每条记录，判断并更新 field0047
            for (Map<String, Object> record : allDetailRecords) {
                try {
                    // 获取记录ID
                    Object idObj = record.get("id");
                    if (idObj == null) {
                        skipCount++;
                        continue;
                    }
                    String recordId = idObj.toString();

                    // 获取日期
                    Object dateObj = record.get("field0026");
                    if (dateObj == null) {
                        log.debug("记录日期为空，跳过更新 field0047，recordId: {}", recordId);
                        skipCount++;
                        continue;
                    }

                    String dateStr = dateObj.toString();
                    if (dateStr == null || dateStr.trim().isEmpty()) {
                        log.debug("记录日期字符串为空，跳过更新 field0047，recordId: {}", recordId);
                        skipCount++;
                        continue;
                    }

                    // 解析日期（可能包含时间部分，只取日期部分）
                    if (dateStr.contains(" ")) {
                        dateStr = dateStr.split(" ")[0];
                    }

                    // 判断是否是默认还款日
                    boolean isDefaultDay = checkIsDefaultPaymentDay(dateStr, interestMode, defaultPaymentDay,
                            defaultStartPaymentMonth);
                    String isDefaultPaymentDayValue = isDefaultDay ? "是" : "否";

                    // 更新 field0047
                    installmentPaymentMapper.updateIsDefaultPaymentDay(recordId, isDefaultPaymentDayValue);
                    updateCount++;

                    log.debug("已更新 field0047，recordId: {}, date: {}, isDefaultPaymentDay: {}", recordId, dateStr,
                            isDefaultPaymentDayValue);

                } catch (Exception e) {
                    log.error("处理单条明细记录时发生错误，formmain_id: {}", formmain_id, e);
                    skipCount++;
                    // 继续处理下一条记录，不中断整个流程
                }
            }

            log.info("全量更新 field0047 完成，formmain_id: {}, 共更新 {} 条记录，跳过 {} 条记录", formmain_id,
                    updateCount, skipCount);

        } catch (Exception e) {
            log.error("全量更新 field0047 失败，formmain_id: {}", formmain_id, e);
            throw e;
        }
    }
}
