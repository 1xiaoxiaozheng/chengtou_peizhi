package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
// 项目分期还款计划表的Mapper接口
public interface InstallmentPaymentMapper {
        /**
         * 获取主表的所有id和是否添加明细表字段
         * 数据字典：formmain_0039，field0043 是否添加明细表
         */
        @Select("SELECT id, field0043 FROM formmain_0039")
        public List<Map<String, Object>> getAllIdAndIsAddDetail();

        /**
         * 根据id获取主表信息
         * 数据字典：formmain_0039
         * field0009: 贷款开始日期
         * field0010: 贷款结束日期
         * field0013: 还本模式
         * field0014: 付息模式
         * field0040: 默认还款日
         * field0044: 默认开始还款月
         * field0067: 是否属于IRR
         */
        @Select("SELECT field0009, field0010, field0013, field0014, field0040, field0044, field0067 FROM formmain_0039 WHERE id = #{id}")
        public Map<String, Object> getMainTableById(@Param("id") String id);

        /**
         * 更新主表的是否添加明细表字段
         * 数据字典：formmain_0039，field0043 是否添加明细表
         */
        @Update("UPDATE formmain_0039 SET field0043 = '已添加' WHERE id = #{id}")
        public void updateIsAddDetailFlag(@Param("id") String id);

        /**
         * 将主表的是否添加明细表字段设置为指定值（如"是"）
         * 用于是否属于IRR=是时，直接标记为已添加明细，不进行日期生成与付息计算
         */
        @Update("UPDATE formmain_0039 SET field0043 = #{value} WHERE id = #{id}")
        void updateField0043(@Param("id") String id, @Param("value") String value);

        /**
         * 批量插入明细表记录
         * 数据字典：formson_0043
         * field0026: 时间
         * field0025: 序号
         * sort: 排序号
         * field0047: 是否默认还款日（"是"或"否"）
         */
        @Insert("<script>" +
                        "INSERT INTO formson_0043(id, field0026, field0025, sort, formmain_id, field0047) VALUES " +
                        "<foreach collection='dateInfoList' item='dateInfo' separator=','>" +
                        "(#{dateInfo.id}, #{dateInfo.date}, #{dateInfo.sort}, #{dateInfo.sort}, #{formmain_id}, #{dateInfo.isDefaultPaymentDay})"
                        +
                        "</foreach>" +
                        "</script>")
        public void addDetail(@Param("formmain_id") String formmain_id,
                        @Param("dateInfoList") List<Map<String, Object>> dateInfoList);

        /**
         * 查询指定formmain_id下已存在的日期列表（用于去重）
         * 数据字典：formson_0043，field0026 时间
         */
        @Select("SELECT CAST(field0026 AS DATE) as date_str FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL")
        public List<String> getExistingDates(@Param("formmain_id") String formmain_id);

        /**
         * 查询指定formmain_id下当前最大的sort值
         */
        @Select("SELECT ISNULL(MAX(sort), 0) FROM formson_0043 WHERE formmain_id = #{formmain_id}")
        public Integer getMaxSort(@Param("formmain_id") String formmain_id);

        /**
         * 删除指定formmain_id下field0026为空的记录（插入前清理）
         */
        @Delete("DELETE FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NULL")
        public void deleteEmptyDateRecords(@Param("formmain_id") String formmain_id);

        /**
         * 查询指定formmain_id下所有有日期的记录，按日期排序（用于修复sort和field0025）
         * 数据字典：formson_0043，field0026 时间
         */
        @Select("SELECT id, CAST(field0026 AS DATE) as date_str FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL ORDER BY field0026 ASC")
        public List<Map<String, Object>> getExistingDateRecordsOrdered(@Param("formmain_id") String formmain_id);

        /**
         * 更新单条记录的sort和field0025（序号）
         * 数据字典：formson_0043，field0025 序号，sort 排序号
         */
        @Update("UPDATE formson_0043 SET sort = #{sort}, field0025 = #{sort} WHERE id = #{id}")
        public void updateRecordSortAndField0025(@Param("id") String id, @Param("sort") Integer sort);

        /**
         * 根据formmain_id获取所有明细表记录
         * 数据字典：formson_0043
         */
        @Select("SELECT * FROM formson_0043 WHERE formmain_id = #{formmain_id} ORDER BY field0026 ASC")
        public List<Map<String, Object>> getDetailTableByFormmainId(@Param("formmain_id") String formmain_id);

        /**
         * 根据formmain_id和时间查询贷款余额
         * 数据字典：formson_0043，field0026 时间，field0028 贷款余额
         * 使用 TOP 1 确保只返回一条记录，避免 TooManyResultsException
         */
        @Select("SELECT TOP 1 field0028 FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE) ORDER BY id ASC")
        public String getLoanBalanceByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        /**
         * 根据formmain_id和时间查询计划还本
         * 数据字典：formson_0043，field0026 时间，field0029 计划还本
         * 使用 TOP 1 确保只返回一条记录，避免 TooManyResultsException
         */
        @Select("SELECT TOP 1 field0029 FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE) ORDER BY id ASC")
        public String getRepaymentByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        /**
         * 根据formmain_id和时间查询下柜资金
         * 数据字典：formson_0043，field0026 时间，field0027 下柜资金
         * 使用 TOP 1 确保只返回一条记录，避免 TooManyResultsException
         */
        @Select("SELECT TOP 1 field0027 FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE) ORDER BY id ASC")
        public String getDisbursementByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        /**
         * 根据formmain_id和时间查询上一个计息日的记录
         * 数据字典：formson_0043，field0026 时间
         */
        @Select("SELECT TOP 1 * FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) < CAST(#{time} AS DATE) ORDER BY field0026 DESC")
        public Map<String, Object> getPreviousPaymentDate(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        /**
         * 更新明细表的模拟付息和贷款余额
         * 数据字典：formson_0043，field0026 时间，field0028 贷款余额，field0030 模拟付息
         * 注意：不再更新 field0033（实际付息）
         * 添加 field0026 IS NOT NULL 条件，确保只更新有效日期的记录
         */
        @Update("UPDATE formson_0043 SET field0028 = #{loanBalance}, field0030 = #{interest} WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE)")
        public void updateQuarterlyData(@Param("formmain_id") String formmain_id, @Param("time") String time,
                        @Param("loanBalance") String loanBalance, @Param("interest") String interest);

        /**
         * 只更新明细表的贷款余额（不更新付息）
         * 数据字典：formson_0043，field0026 时间，field0028 贷款余额
         * 添加 field0026 IS NOT NULL 条件，确保只更新有效日期的记录
         */
        @Update("UPDATE formson_0043 SET field0028 = #{loanBalance} WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE)")
        public void updateLoanBalanceOnly(@Param("formmain_id") String formmain_id, @Param("time") String time,
                        @Param("loanBalance") String loanBalance);

        /**
         * 根据formmain_id获取主表的单据编号（流水号）
         * 数据字典：formmain_0039，field0001 单据编号
         */
        @Select("SELECT field0001 FROM formmain_0039 WHERE id = #{formmain_id}")
        public String getSerialNumberByFormmainId(@Param("formmain_id") String formmain_id);

        /**
         * 根据单据编号（流水号）获取formmain_0039的主表id
         * 数据字典：formmain_0039，field0001 单据编号
         */
        @Select("SELECT id FROM formmain_0039 WHERE field0001 = #{serialNumber}")
        public String getMainTableIdBySerialNumber(@Param("serialNumber") String serialNumber);

        /**
         * 根据formmain_id获取主表信息（包括贷款开始日期、结束日期、最新利率等）
         * 数据字典：formmain_0039
         * field0001: 单据编号, field0009: 贷款开始日期, field0010: 贷款结束日期, field0012: 最新利率
         * field0040: 默认还款日, field0044: 默认开始还款月
         */
        @Select("SELECT field0001, field0009, field0010, field0012, field0040, field0044 FROM formmain_0039 WHERE id = #{formmain_id}")
        public Map<String, Object> getMainTableInfoById(@Param("formmain_id") String formmain_id);

        /**
         * 根据formmain_id获取首次下柜日期
         * 数据字典：formson_0043，field0027 下柜资金，field0026 时间
         */
        @Select("SELECT TOP 1 field0026 FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0027 IS NOT NULL AND field0027 > 0 ORDER BY field0026 ASC")
        public String getFirstDisbursementDate(@Param("formmain_id") String formmain_id);

        /**
         * 根据明细表id获取主表id
         * 数据字典：formson_0043 明细表
         */
        @Select("SELECT formmain_id FROM formson_0043 WHERE id = #{detailTableId}")
        public String getMainTableIdByDetailTableId(@Param("detailTableId") String detailTableId);

        /**
         * 根据formmain_id获取所有有日期的明细表记录（用于重新计算付息）
         * 数据字典：formson_0043，field0026 时间
         */
        @Select("SELECT id, CAST(field0026 AS DATE) as date_str FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL ORDER BY field0026 ASC")
        public List<Map<String, Object>> getDetailRecordsWithDates(@Param("formmain_id") String formmain_id);

        /**
         * 根据formmain_id获取所有计息日记录（包括所有字段）
         * 数据字典：formson_0043
         */
        @Select("SELECT * FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL ORDER BY field0026 ASC")
        public List<Map<String, Object>> getAllPaymentDates(@Param("formmain_id") String formmain_id);

        /**
         * 根据formmain_id和日期范围获取明细表记录
         * 数据字典：formson_0043
         * field0026: 时间
         * field0027: 下柜资金
         * field0029: 计划还本
         */
        @Select("SELECT * FROM formson_0043 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) >= CAST(#{startDate} AS DATE) AND CAST(field0026 AS DATE) <= CAST(#{endDate} AS DATE) ORDER BY field0026 ASC")
        public List<Map<String, Object>> getPaymentDatesByDateRange(@Param("formmain_id") String formmain_id,
                        @Param("startDate") String startDate, @Param("endDate") String endDate);

        /**
         * 查询今天或之后的第一个还款日的贷款余额
         * 数据字典：formson_0043，field0026 时间，field0028 贷款余额
         */
        @Select("SELECT TOP 1 field0028 " +
                        "FROM formson_0043 " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND CAST(field0026 AS DATE) >= CAST(#{currentDate} AS DATE) " +
                        "ORDER BY field0026 ASC")
        public String getLoanBalanceForFirstFutureDate(@Param("formmain_id") String formmain_id,
                        @Param("currentDate") String currentDate);

        /**
         * 更新主表的贷款余额合计（field0035）
         * 数据字典：formmain_0039，field0035 贷款余额合计
         */
        @Update("UPDATE formmain_0039 SET field0035 = #{loanBalance} WHERE id = #{formmain_id}")
        public void updateMainTableLoanBalanceSummary(@Param("formmain_id") String formmain_id,
                        @Param("loanBalance") String loanBalance);

        /**
         * 更新主表的最新利率（field0012）和利率生效时间（field0042）
         * 数据字典：formmain_0039，field0012 最新利率，field0042 利率生效时间
         */
        @Update("UPDATE formmain_0039 SET field0012 = #{interestRate}, field0042 = #{effectiveDate} WHERE id = #{formmain_id}")
        public void updateMainTableInterestRate(@Param("formmain_id") String formmain_id,
                        @Param("interestRate") String interestRate, @Param("effectiveDate") String effectiveDate);

        /**
         * 更新明细表的实际付息（field0033）
         * 数据字典：formson_0043，field0026 时间，field0033 实际付息
         * 添加 field0026 IS NOT NULL 条件，确保只更新有效日期的记录
         */
        @Update("UPDATE formson_0043 SET field0033 = #{actualInterest} WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE)")
        public void updateActualInterest(@Param("formmain_id") String formmain_id, @Param("time") String time,
                        @Param("actualInterest") BigDecimal actualInterest);

        /**
         * 更新明细表的是否默认还款日（field0047）
         * 数据字典：formson_0043，field0026 时间，field0047 是否默认还款日
         * 添加 field0026 IS NOT NULL 条件，确保只更新有效日期的记录
         */
        @Update("UPDATE formson_0043 SET field0047 = #{isDefaultPaymentDay} WHERE id = #{id}")
        public void updateIsDefaultPaymentDay(@Param("id") String id,
                        @Param("isDefaultPaymentDay") String isDefaultPaymentDay);
}
