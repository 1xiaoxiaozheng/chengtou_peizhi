package com.SpringbootTZ.FACT.Entity;

import lombok.Data;

/**
 * SQL配置表映射（sys_sql_config）
 */
@Data
public class SysSqlConfig {
    private Long id;
    private String sqlKey; // 唯一标识
    private String baseTable; // 表名
    private String selectableFields; // 可查询字段（JSON数组）
    private String conditionFields; // 条件字段（JSON数组）
    private String defaultSort;
    private String remark;
}

