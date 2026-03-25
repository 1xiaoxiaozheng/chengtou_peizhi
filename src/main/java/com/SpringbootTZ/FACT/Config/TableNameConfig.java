package com.SpringbootTZ.FACT.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * OA表单表名配置类
 * 用于配置不同OA环境下的表名，便于代码迁移
 */
@Configuration
public class TableNameConfig {

    // 按日还款计划表
    @Value("${table.daily-repayment-plan.main:formmain_0146}")
    private String dailyRepaymentPlanMain;

    @Value("${table.daily-repayment-plan.detail:formson_0147}")
    private String dailyRepaymentPlanDetail;

    // 按月还款计划表
    @Value("${table.monthly-repayment-plan.main:formmain_0144}")
    private String monthlyRepaymentPlanMain;

    @Value("${table.monthly-repayment-plan.detail:formson_0145}")
    private String monthlyRepaymentPlanDetail;

    // 利率表
    @Value("${table.interest.main:formmain_0032}")
    private String interestMain;

    // Getter方法
    public String getDailyRepaymentPlanMain() {
        return dailyRepaymentPlanMain;
    }

    public String getDailyRepaymentPlanDetail() {
        return dailyRepaymentPlanDetail;
    }

    public String getMonthlyRepaymentPlanMain() {
        return monthlyRepaymentPlanMain;
    }

    public String getMonthlyRepaymentPlanDetail() {
        return monthlyRepaymentPlanDetail;
    }

    public String getInterestMain() {
        return interestMain;
    }
}
