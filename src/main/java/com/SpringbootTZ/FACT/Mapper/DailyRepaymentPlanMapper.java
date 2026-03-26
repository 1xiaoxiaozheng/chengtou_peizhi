package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
// 项目按日期还款计划的Mapper接口
public interface DailyRepaymentPlanMapper {
        // 获取主表的所有id和是否添加明细表，返回list<map>
        // 新数据字典：field0061 是否添加明细表日期
        @Select("SELECT id,field0061 FROM formmain_0029 ")
        public List<Map<String, Object>> getAllIdAndIsAddDetail();

        // 接收formmain_id和list<String>时间列表,让明细表1的formmain_id=formmain_id,批量添加field0026字段和sort字段，其他都为null
        // 使用ClientNumber生成的ID
        // 新数据字典：formson_0030 明细表，field0025 序号，field0026 时间，field0027 下柜资金，field0028
        // 贷款余额，field0029
        // 还本，field0030 付息
        // field0025 = sort（序号等于排序号）
        @Insert("<script>" +
                        "INSERT INTO formson_0030(id,field0025,field0027,field0028,field0029,field0030,field0031,field0026,sort,formmain_id) VALUES "
                        +
                        "<foreach collection='timeList' item='time' index='index' separator=','>" +
                        "(#{startId} + #{index},#{startSort} + #{index},null,null,null,null,null,#{time},#{startSort} + #{index},#{formmain_id})"
                        +
                        "</foreach>" +
                        "</script>")
        public void addDetail(@Param("formmain_id") String formmain_id,
                        @Param("timeList") List<String> timeList,
                        @Param("startId") Long startId,
                        @Param("startSort") Integer startSort);

        // 查询指定formmain_id下已存在的日期列表（用于去重）
        // 新数据字典：field0026 时间
        @Select("SELECT CAST(field0026 AS DATE) as date_str FROM formson_0030 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL")
        public List<String> getExistingDates(@Param("formmain_id") String formmain_id);

        // 查询指定formmain_id下当前最大的sort值
        @Select("SELECT ISNULL(MAX(sort), 0) FROM formson_0030 WHERE formmain_id = #{formmain_id}")
        public Integer getMaxSort(@Param("formmain_id") String formmain_id);

        // 删除指定formmain_id下field0026为空的记录（插入前清理）
        @Delete("DELETE FROM formson_0030 WHERE formmain_id = #{formmain_id} AND field0026 IS NULL")
        public void deleteEmptyDateRecords(@Param("formmain_id") String formmain_id);

        // 根据id获取主表的开始时间和结束时间
        // 新数据字典：field0059 开始日期，field0060 结束日期，field0046 贷款银行，field0061
        // 是否添加明细表日期，field0042 最新利率，field0067 是否属于IRR
        @Select("SELECT field0059,field0060,field0046,field0061,field0042,field0067 FROM formmain_0029 WHERE id = #{id}")
        public Map<String, Object> getMainTableById(@Param("id") String id);

        // 更新主表的是否添加明细表字段
        // 新数据字典：field0061 是否添加明细表日期
        @Update("UPDATE formmain_0029 SET field0061 = '已添加' WHERE id = #{id}")
        public void updateIsAddDetailFlag(@Param("id") String id);

        // 根据id获取主表的还本模式
        @Select("SELECT field0014 FROM formmain_0146 WHERE id = #{id}")
        public String getRepaymentModeById(@Param("id") String id);

        // 删除指定formmain_id下所有字段都为null的明细表记录
        // 新数据字典：field0027 下柜资金(NUMERIC)，field0028 贷款余额(NUMERIC)，field0029 还本(NUMERIC)，
        // field0030 付息(NUMERIC)，field0031 备注(VARCHAR)，field0026 时间(DATETIME)
        // 注意：NUMERIC类型字段不能与空字符串比较，只能检查IS NULL
        @Delete("DELETE FROM formson_0030 WHERE formmain_id = #{formmain_id} " +
                        "AND field0027 IS NULL " +
                        "AND field0028 IS NULL " +
                        "AND field0029 IS NULL " +
                        "AND field0030 IS NULL " +
                        "AND (field0031 IS NULL OR field0031 = '') " +
                        "AND field0026 IS NULL")
        public void deleteEmptyDetailRecords(@Param("formmain_id") String formmain_id);

        // 根据流水号查询主表的贷款银行
        // 新数据字典：formmain_0029，field0046 贷款银行，field0001 单据编号（作为流水号）
        @Select("SELECT field0046 FROM formmain_0029 WHERE field0001 = #{serialNumber}")
        public String getLoanBankBySerialNumber(@Param("serialNumber") String serialNumber);

        // 接收利率和流水号，根据流水号找到表并更改利率
        // 新数据字典：formmain_0029，field0042 最新利率，field0001 单据编号（作为流水号）
        @Update("UPDATE formmain_0029 SET field0042 = #{rate} WHERE field0001 = #{serialNumber}")
        public void updateRateBySerialNumber(@Param("rate") String rate, @Param("serialNumber") String serialNumber);

        // 根据单据编号/流水号获取主表id
        // 新数据字典：formmain_0029，field0001 单据编号（作为流水号）
        @Select("SELECT id FROM formmain_0029 WHERE field0001 = #{serialNumber}")
        public String getMainTableIdBySerialNumber(@Param("serialNumber") String serialNumber);

        // 根据主表id获取流水号（单据编号）
        // 新数据字典：formmain_0029，field0001 单据编号（作为流水号）
        @Select("SELECT field0001 FROM formmain_0029 WHERE id = #{id}")
        public String getSerialNumberByMainTableId(@Param("id") String id);

        // 根据明细表id获取流水号（单据编号）
        // 新数据字典：formson_0030 明细表，formmain_0029 主表，field0001 单据编号（作为流水号）
        @Select("SELECT field0001 FROM formmain_0029 WHERE id = (SELECT formmain_id FROM formson_0030 WHERE id = #{detailTableId})")
        public String getSerialNumberByDetailTableId(@Param("detailTableId") String detailTableId);

        // 根据明细表id获取主表id
        // 新数据字典：formson_0030 明细表
        @Select("SELECT formmain_id FROM formson_0030 WHERE id = #{detailTableId}")
        public String getMainTableIdByDetailTableId(@Param("detailTableId") String detailTableId);

        // 根据formmain_id = formmain_id,获取数据为list<map>
        // 新数据字典：formson_0030 明细表
        // 必须按日期升序处理，否则会先处理到还本日误判提前退出，导致首次下柜日之后的付息未计算
        @Select("SELECT * FROM formson_0030 WHERE formmain_id = #{formmain_id} ORDER BY field0026 ASC")
        public List<Map<String, Object>> getDetailTableByFormmainId(@Param("formmain_id") String formmain_id);

        // 接收formmain_id，以及field0026字符，查询field0028贷款余额
        // 新数据字典：field0026 时间，field0028 贷款余额
        // 使用CAST函数处理TIMESTAMP字段，确保日期匹配（SQL Server兼容）
        @Select("SELECT field0028 FROM formson_0030 WHERE formmain_id = #{formmain_id} AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE)")
        public String getLoanBalanceByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        // 接收formmain_id,以及field0026字段，查询field0029还本
        // 新数据字典：field0026 时间，field0029 还本
        // 使用CAST函数处理TIMESTAMP字段，确保日期匹配（SQL Server兼容）
        @Select("SELECT field0029 FROM formson_0030 WHERE formmain_id = #{formmain_id} AND CAST(field0026 AS DATE) = CAST(#{time} AS DATE)")
        public String getRepaymentByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                        @Param("time") String time);

        // 接收formmain_id,以及field0026，以及field0028（贷款余额），以及field0030（付息），更新这条记录
        // 新数据字典：field0026 时间，field0028 贷款余额，field0030 付息，field0033 模拟付息（等于付息）
        // 使用范围条件避免对 field0026 做 CAST，便于走索引、减少锁竞争与死锁
        @Update("UPDATE formson_0030 SET field0028 = #{loanBalance}, field0030 = #{interest}, field0033 = #{interest} "
                        +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 >= CAST(#{time} AS DATE) " +
                        "AND field0026 < DATEADD(DAY, 1, CAST(#{time} AS DATE))")
        public void updateDailyData(@Param("formmain_id") String formmain_id, @Param("time") String time,
                        @Param("loanBalance") String loanBalance, @Param("interest") String interest);

        // 更新主表的合计字段
        // 新数据字典：formmain_0029，field0034 下柜资金合计，field0035 贷款余额合计，field0036
        // 还本合计，field0037 付息合计
        @Update("UPDATE formmain_0029 SET field0034 = #{totalDisbursedAmount}, field0035 = #{totalLoanBalance}, field0036 = #{totalPrincipalPaid}, field0037 = #{totalInterestPaid} WHERE id = #{formmainId}")
        public void updateMainTableSummaryFields(@Param("formmainId") String formmainId,
                        @Param("totalDisbursedAmount") String totalDisbursedAmount,
                        @Param("totalLoanBalance") String totalLoanBalance,
                        @Param("totalPrincipalPaid") String totalPrincipalPaid,
                        @Param("totalInterestPaid") String totalInterestPaid);

        // 根据贷款银行查询所有按日还款计划表的流水号列表
        @Select("SELECT field0023 FROM formmain_0146 WHERE field0025 = #{loanBank}")
        public List<String> getLoanSerialNosByBank(@Param("loanBank") String loanBank);

        // 查询指定formmain_id下首次下柜日期（field0027不为null且大于0的最早日期）
        // 新数据字典：field0026 时间，field0027 下柜资金
        @Select("SELECT TOP 1 CAST(field0026 AS DATE) as first_disbursement_date " +
                        "FROM formson_0030 " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND field0027 IS NOT NULL " +
                        "AND CAST(field0027 AS DECIMAL(18,2)) > 0 " +
                        "ORDER BY field0026 ASC")
        public String getFirstDisbursementDate(@Param("formmain_id") String formmain_id);

        // 查询今天或之后的第一个还款日的贷款余额
        // 新数据字典：field0026 时间，field0028 贷款余额
        @Select("SELECT TOP 1 field0028 " +
                        "FROM formson_0030 " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND CAST(field0026 AS DATE) >= CAST(#{currentDate} AS DATE) " +
                        "ORDER BY field0026 ASC")
        public String getLoanBalanceForFirstFutureDate(@Param("formmain_id") String formmain_id,
                        @Param("currentDate") String currentDate);

        // 更新主表的贷款余额合计（field0035）
        // 新数据字典：formmain_0029，field0035 贷款余额合计
        @Update("UPDATE formmain_0029 SET field0035 = #{loanBalance} WHERE id = #{formmain_id}")
        public void updateMainTableLoanBalanceSummary(@Param("formmain_id") String formmain_id,
                        @Param("loanBalance") String loanBalance);

        // 查询指定formmain_id下所有有日期的记录，按日期排序（用于修复sort和field0025）
        // 新数据字典：field0026 时间
        @Select("SELECT id, CAST(field0026 AS DATE) as date_str FROM formson_0030 WHERE formmain_id = #{formmain_id} AND field0026 IS NOT NULL ORDER BY field0026 ASC")
        public List<Map<String, Object>> getExistingDateRecordsOrdered(@Param("formmain_id") String formmain_id);

        // 更新单条记录的sort和field0025（序号）
        // 新数据字典：field0025 序号，sort 排序号
        @Update("UPDATE formson_0030 SET sort = #{sort}, field0025 = #{sort} WHERE id = #{id}")
        public void updateRecordSortAndField0025(@Param("id") String id, @Param("sort") Integer sort);

        // 查询指定formmain_id下指定日期范围内的付息总和（用于按季/按月付息累加）
        // 新数据字典：field0026 时间，field0030 付息
        // 日期范围：startDate（包含）到 endDate（包含）
        @Select("SELECT ISNULL(SUM(CAST(field0030 AS DECIMAL(20,10))), 0) as total_interest " +
                        "FROM formson_0030 " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND field0030 IS NOT NULL " +
                        "AND CAST(field0026 AS DATE) >= CAST(#{startDate} AS DATE) " +
                        "AND CAST(field0026 AS DATE) <= CAST(#{endDate} AS DATE)")
        public String getTotalInterestByDateRange(@Param("formmain_id") String formmain_id,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);

        /**
         * 批量将指定日期及之后的所有记录的贷款余额和付息设置为0
         * 用于提前退出优化：当某日贷款余额为0且无下柜时，后续日期无需逐日计算，一次性置0即可
         * 新数据字典：field0026 时间，field0028 贷款余额，field0030 付息，field0033 模拟付息
         */
        @Update("UPDATE formson_0030 SET field0028 = '0', field0030 = '0', field0033 = '0' " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND CAST(field0026 AS DATE) >= CAST(#{fromDate} AS DATE)")
        public void batchUpdateZeroFromDate(@Param("formmain_id") String formmain_id,
                        @Param("fromDate") String fromDate);

}