package com.SpringbootTZ.FACT.Entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

//项目按日还款计划表
public class DailyRepaymentPlan {
    /**
     * 表单名称：	项目按日还款计划表
     * 所属应用：	人事审批
     * 表单类型：	无流程表单
     * 创建人员：	小呆呆
     * 所属人员：	小呆呆
     * 主表信息
     * 表名称：	 	主表字段
     * 数据库表名称：	 	formmain_0146
     * 字段名称	 	字段类型	 	字段长度	 	显示名称	 	字段输入类型	 	字段最终类型
     * field0001	 	TIMESTAMP	 	255	 	开始时间	 	日期	 	TIMESTAMP
     * field0002	 	TIMESTAMP	 	255	 	结束时间	 	日期	 	TIMESTAMP
     * field0003	 	VARCHAR	 	100	 	贷款时间	 	文本	 	VARCHAR
     * field0004	 	VARCHAR	 	100	 	利率	 	文本	 	VARCHAR	     文本	 	DECIMAL
     * field0014	 	DECIMAL	 	20	 	还本模式	 	下拉	 	DECIMAL
     * field0015	 	DECIMAL	 	20	 	付息模式	 	下拉	 	DECIMAL
     * field0016	 	VARCHAR	 	100	 	时间合计	 	文本	 	VARCHAR
     * field0017	 	VARCHAR	 	100	 	下柜资金合计	 	文本	 	VARCHAR
     * field0018	 	VARCHAR	 	100	 	贷款余额合计	 	文本	 	VARCHAR
     * field0019	 	VARCHAR	 	100	 	还本合计	 	文本	 	VARCHAR
     * field0020	 	VARCHAR	 	100	 	付息合计	 	文本	 	VARCHAR
     * field0021	 	VARCHAR	 	100	 	文本6	 	文本	 	VARCHAR
     * field0023	 	VARCHAR	 	100	 	流水号	 	文本	 	VARCHAR
     * field0024	 	VARCHAR	 	100	 	是否添加明细表	 	文本	 	VARCHAR
     * 从表信息
     *
     * 表名称：	 	明细表1(明细表1)
     * 创建时间	 	2025-09-29 14:46:46
     * 数据库表名称：	 	formson_0147
     * 字段名称	 	字段类型	 	字段长度	 	显示名称	 	字段输入类型	 	字段最终类型
     * field0008	 	VARCHAR	 	100	 	下柜资金	 	文本	 	VARCHAR
     * field0009	 	VARCHAR	 	100	 	贷款余额	 	文本	 	VARCHAR
     * field0010	 	VARCHAR	 	100	 	还本	 	文本	 	VARCHAR
     * field0011	 	VARCHAR	 	100	 	付息	 	文本	 	VARCHAR
     * field0012	 	VARCHAR	 	100	 	备注	 	文本	 	VARCHAR
     * field0022	 	TIMESTAMP	 	255	 	时间	 	日期	 	TIMESTAMP
     */

    @Data
    @TableName("formmain_0146")
    public class DailyRepaymentPlanMainEntity {
        private String field0001;   //开始时间
        private String field0002;   //结束时间
        private String field0003;   //贷款时间
        private String field0004;   //利率
        private String field0014;   //还本模式
        private String field0015;   //付息模式
        private String field0016;   //时间合计
        private String field0017;   //下柜资金合计
        private String field0018;   //贷款余额合计
        private String field0019;   //还本合计
        private String field0020;   //付息合计
        private String field0021;   //文本6
        private String field0023;   //流水号
        private String field0024;   //是否添加明细表
    }

    @Data
    @TableName("formson_0147")
    public class DailyRepaymentPlanSonEntity {
        private String field0008;   //下柜资金
        private String field0009;   //贷款余额
        private String field0010;   //还本
        private String field0011;   //付息
        private String field0012;   //备注
        private String field0022;   //时间
    }
}
