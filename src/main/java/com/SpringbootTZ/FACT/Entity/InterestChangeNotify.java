package com.SpringbootTZ.FACT.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//利率变更中间表
@TableName("interest_change_notify")
public class InterestChangeNotify {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("source_id")
    private Long sourceId; // 关联 formmain_0032 的主键
    @TableField("bill_no")
    private String billNo; // 单据编号（field0001）
    @TableField("current_rate")
    private BigDecimal currentRate; // 利率值（NUMERIC(20,6)）
    @TableField("rate_effective_time")
    private LocalDateTime rateEffectiveTime; // 利率生效时间（field0041）
    @TableField("process_status")
    private Integer processStatus; // 0-未处理，1-已处理，2-处理失败
    @TableField("create_time")
    private LocalDateTime createTime;

    // id 的 getter/setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // sourceId 的 getter/setter
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    // billNo 的 getter/setter
    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    // currentRate 的 getter/setter
    public BigDecimal getCurrentRate() {
        return currentRate;
    }

    public void setCurrentRate(BigDecimal currentRate) {
        this.currentRate = currentRate;
    }

    // rateEffectiveTime 的 getter/setter
    public void setRateEffectiveTime(LocalDateTime rateEffectiveTime) {
        this.rateEffectiveTime = rateEffectiveTime;
    }

    public LocalDateTime getRateEffectiveTime() {
        return rateEffectiveTime;
    }

    // processStatus 的 getter/setter
    public Integer getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(Integer processStatus) {
        this.processStatus = processStatus;
    }

    // createTime 的 getter/setter
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}