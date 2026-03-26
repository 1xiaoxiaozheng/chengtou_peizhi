package com.SpringbootTZ.FACT.Entity;

import lombok.Data;

/**
 * SQL配置里的条件规则
 * 例如：{"field":"username","op":"like"}
 */
@Data
public class ConditionRule {
    private String field;
    private String op; // eq / like / between
    /**
     * 可选：绑定数据字典类型（供可视化后台/前端渲染下拉框，不参与 SQL 拼装）
     */
    private String dict;
    /**
     * 可选：text / select（供 UI 提示）
     */
    private String inputType;
}

