package com.SpringbootTZ.FACT.Entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;

/**
 * 利率变更记录表——实体类
 *
 * 表名称： 主表字段
 * 数据库表名称： formmain_0032
 * 字段名称 字段类型 字段长度 显示名称 字段输入类型 字段最终类型
 * field0004 VARCHAR 100 当前利率 文本 VARCHAR
 * field0005 DATETIME 255 变更时间 日期时间 DATETIME
 * 
 */

@Data
@TableName("formmain_0032")
public class interest {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("field0004")
    private String currentRate; // 当前利率

    @TableField("field0005")
    private java.time.LocalDateTime changeTime; // 变更时间
}
