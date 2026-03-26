package com.SpringbootTZ.FACT.Entity;

/**
 * 动态查询条件（值通过 #{...} 参数绑定，避免注入）。
 */
public class Condition {
    private String field;
    private String op; // eq / like / between

    // eq / like 使用 value
    private Object value;

    // between 使用 valueFrom / valueTo
    private Object valueFrom;
    private Object valueTo;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(Object valueFrom) {
        this.valueFrom = valueFrom;
    }

    public Object getValueTo() {
        return valueTo;
    }

    public void setValueTo(Object valueTo) {
        this.valueTo = valueTo;
    }
}

