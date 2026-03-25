package com.SpringbootTZ.FACT.Service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * author: tz
 * 工具类，用于生成客户端交易流水号，用于单笔支付查证定时任务、以及电子回单查询、通用下载文件查询等
 * 格式为：YYYYMMDD+6位数
 * generateNumber() 方法用于生成客户端交易流水号
 */
@Service
public class ClientNumber {

    // 获取当前时间，格式为 YYYYMMDD
    private static String getCurrentDate() {
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return now.format(formatter);
    }

    public static String generateNumber() {
        // 获取当前日期
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 获取当前时间戳
        String timestamp = String.valueOf(System.currentTimeMillis());
        // 取时间戳的后8位
        String timestampPart = timestamp.substring(Math.max(0, timestamp.length() - 8));
        
        // 生成3位随机数
        int randomNum = (int) (Math.random() * 900) + 100; // 100-999的随机数
        
        // 组合成流水号
        return date + timestampPart + randomNum;
    }

    private static long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}