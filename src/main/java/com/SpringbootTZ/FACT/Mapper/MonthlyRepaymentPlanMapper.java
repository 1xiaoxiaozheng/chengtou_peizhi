package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

//项目按月还款计划的Mapper接口
@Mapper
public interface MonthlyRepaymentPlanMapper {
        /**
         * 表单信息
         * 表单名称： 项目按月还款计划表
         * 所属应用： 人事审批
         * 表单类型： 无流程表单
         * 创建人员： 小呆呆
         * 所属人员： 小呆呆
         * 主表信息
         * 表名称： 主表字段
         * 数据库表名称： formmain_0144
         * 字段名称 字段类型 字段长度 显示名称 字段输入类型 字段最终类型
         * field0001 TIMESTAMP 255 开始时间 日期 TIMESTAMP
         * field0002 TIMESTAMP 255 结束时间 日期 TIMESTAMP
         * field0003 VARCHAR 100 贷款时间 文本 VARCHAR
         * field0004 VARCHAR 100 利率 文本 VARCHAR
         * field0014 DECIMAL 20 还本模式 下拉 DECIMAL
         * field0015 DECIMAL 20 付息模式 下拉 DECIMAL
         * field0017 VARCHAR 100 下柜资金合计 文本 VARCHAR
         * field0018 VARCHAR 100 贷款余额合计 文本 VARCHAR
         * field0019 VARCHAR 100 还本合计 文本 VARCHAR
         * field0020 VARCHAR 100 付息合计 文本 VARCHAR
         * field0023 VARCHAR 100 流水号 文本 VARCHAR
         * field0024 VARCHAR 100 是否添加明细表 文本 VARCHAR
         * field0025 VARCHAR 100 贷款银行 文本 VARCHAR
         * 从表信息
         *
         * 表名称： 明细表1(明细表1)
         * 创建时间 2025-09-29 14:46:46
         * 数据库表名称： formson_0145
         * 字段名称 字段类型 字段长度 显示名称 字段输入类型 字段最终类型
         * field0022 TIMESTAMP 255 时间 日期 TIMESTAMP
         * field0008 VARCHAR 100 下柜资金 文本 VARCHAR
         * field0009 VARCHAR 100 贷款余额 文本 VARCHAR
         * field0010 VARCHAR 100 还本 文本 VARCHAR
         * field0011 VARCHAR 100 付息 文本 VARCHAR
         * field0012 VARCHAR 100 备注 文本 VARCHAR
         */
        // 获取主表的所有id和是否添加明细表，返回list<map>
        @Select("SELECT id ,field0024 FROM formmain_0144")
        List<Map<String, Object>> getAllIdAndIsAddDetail();

        // 接收formmain_id和list<String>时间列表,让明细表1的formmain_id=formmain_id,批量添加field0022字段和sort字段，其他都为null
        // 使用ClientNumber生成的ID
        @Insert("<script>" +
                        "INSERT INTO formson_0145(id,field0008,field0009,field0010,field0011,field0012,field0022,sort,formmain_id) VALUES "
                        +
                        "<foreach collection='timeList' item='time' index='index' separator=','>" +
                        "(#{startId} + #{index},null,null,null,null,null,#{time},#{index} + 1,#{formmain_id})" +
                        "</foreach>" +
                        "</script>")
        public void addDetail(@Param("formmain_id") String formmain_id,
                        @Param("timeList") List<String> timeList,
                        @Param("startId") Long startId);

        // 根据id获取主表的开始时间和结束时间，贷款时间
        @Select("SELECT field0001,field0002,field0003 FROM formmain_0144 WHERE id = #{id}")
        public Map<String, Object> getMainTableById(String id);

        // 更新主表的是否添加明细表字段
        @Update("UPDATE formmain_0144 SET field0024 = '已添加' WHERE id = #{id}")
        public void updateIsAddDetailFlag(String id);

        // 根据id获取主表的还本模式
        @Select("SELECT field0014 FROM formmain_0144 WHERE id = #{id}")
        public String getRepaymentModeById(String id);

        // 根据id获取付息方式
        @Select("SELECT field0015 FROM formmain_0144 WHERE id = #{id}")
        public String getInterestModeById(String id);

        // 根据id获取还款日期（field0027）
        @Select("SELECT field0027 FROM formmain_0144 WHERE id = #{id}")
        public String getPaymentDayById(String id);

        // 删除指定formmain_id下所有字段都为null的明细表记录
        @Delete("DELETE FROM formson_0145 WHERE formmain_id = #{formmain_id} " +
                        "AND (field0008 IS NULL OR field0008 = '') " +
                        "AND (field0009 IS NULL OR field0009 = '') " +
                        "AND (field0010 IS NULL OR field0010 = '') " +
                        "AND (field0011 IS NULL OR field0011 = '') " +
                        "AND (field0012 IS NULL OR field0012 = '') " +
                        "AND field0022 IS NULL")
        public void deleteEmptyDetailRecords(@Param("formmain_id") String formmain_id);

        // 根据流水号查询主表的贷款银行
        @Select("SELECT field0025 FROM formmain_0144 WHERE field0023 = #{serialNumber}")
        public String getLoanBankBySerialNumber(String serialNumber);

        // 接收利率和流水号，根据流水号找到表并更改利率
        @Update("UPDATE formmain_0144 SET field0004 = #{rate} WHERE field0023 = #{serialNumber}")
        public void updateRateBySerialNumber(@Param("rate") String rate, @Param("serialNumber") String serialNumber);

}
