package com.SpringbootTZ.FACT.Entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;

/**
 * 按日还款记录表中间表——实体类
 *
 * 表名称： Middle_Insert_Records
 * 数据库表名称： Middle_Insert_Records
 * 字段名称 字段类型 字段长度 显示名称 字段输入类型 字段最终类型
 * id INT AUTO_INCREMENT PRIMARY KEY
 * target_table_id VARCHAR 50 目标表ID 文本 VARCHAR
 * monitored_field VARCHAR 255 监控字段 文本 VARCHAR
 * create_time DATETIME 创建时间 日期时间 DATETIME
 * process_status TINYINT 处理状态 数字 TINYINT
 * process_time DATETIME 处理时间 日期时间 DATETIME
 * fail_reason VARCHAR 1000 失败原因 文本 VARCHAR
 * retry_count INT 重试次数 数字 INT
 */

@Data
@TableName("Middle_Insert_Records")
public class MiddleInsertRecords {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("target_table_id")
    private String targetTableId; // 目标表ID

    @TableField("monitored_field")
    private String monitoredField; // 监控字段

    @TableField("create_time")
    private java.time.LocalDateTime createTime; // 创建时间

    @TableField("process_status")
    private Integer processStatus; // 处理状态

    @TableField("process_time")
    private java.time.LocalDateTime processTime; // 处理时间

    @TableField("fail_reason")
    private String failReason; // 失败原因

    @TableField("retry_count")
    private Integer retryCount; // 重试次数
}
