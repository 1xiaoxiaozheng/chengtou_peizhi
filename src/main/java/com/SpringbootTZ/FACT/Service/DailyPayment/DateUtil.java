package com.SpringbootTZ.FACT.Service.DailyPayment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期工具类
 * 提供日期解析和日期列表生成等公共方法，避免循环依赖
 */
@Component
public class DateUtil {

    private static final Logger log = LoggerFactory.getLogger(DateUtil.class);

    /**
     * 解析日期字符串，支持多种格式
     *
     * @param dateStr 日期字符串
     * @return LocalDate对象
     */
    public LocalDate parseDate(String dateStr) {
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
     * 生成从开始时间到结束时间的每一天日期列表
     *
     * @param startTimeStr 开始时间字符串
     * @param endTimeStr   结束时间字符串
     * @return 日期字符串列表，格式为 yyyy-MM-dd
     */
    public List<String> generateDateList(String startTimeStr, String endTimeStr) {
        List<String> dateList = new ArrayList<>();

        try {
            // 解析时间字符串，处理可能的格式
            LocalDate startDate = parseDate(startTimeStr);
            LocalDate endDate = parseDate(endTimeStr);

            if (startDate != null && endDate != null && !startDate.isAfter(endDate)) {
                LocalDate currentDate = startDate;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                // 生成每一天的日期，格式为 yyyy-MM-dd（如：2027-01-12）
                while (!currentDate.isAfter(endDate)) {
                    String dateStr = currentDate.format(formatter);
                    dateList.add(dateStr);
                    currentDate = currentDate.plusDays(1);
                }

                if (!dateList.isEmpty()) {
                    log.debug("生成日期列表：从 {} 到 {}，共 {} 条，格式：yyyy-MM-dd",
                            dateList.get(0), dateList.get(dateList.size() - 1), dateList.size());
                }
            }
        } catch (Exception e) {
            log.error("生成日期列表时发生错误，startTimeStr: {}, endTimeStr: {}", startTimeStr, endTimeStr, e);
        }

        return dateList;
    }
}
