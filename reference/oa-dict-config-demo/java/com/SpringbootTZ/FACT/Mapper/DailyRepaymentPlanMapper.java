package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
// 项目按日还款计划表 — 中间表 RepaymentPlan（可配置版：表名/流水号字段来自 cfg）
public interface DailyRepaymentPlanMapper {

    // 新数据字典（oa_dict_config config_key）
    // - oa.plan.detail.table：按日还款计划表明细表
    // - oa.plan.main.table  ：按日还款计划表主表
    // - oa.plan.serial.field：主表单据编号（作为流水号）的字段名
    //
    // 注意：${cfg['...']} 用于拼接标识符（表名/字段名），仅适合可信配置源。

    /**
     * 获取主表的所有 id 和是否添加明细表标记
     */
    @Select("SELECT id, ${cfg['oa.plan.field.is.add.detail']} AS field0061 FROM ${cfg['oa.plan.main.table']}")
    List<Map<String, Object>> getAllIdAndIsAddDetail(@Param("cfg") Map<String, String> cfg);

    /**
     * 接收 formmain_id 和时间列表，批量向“明细表”写入对应日期行，并生成 id/sort。
     */
    @Insert("<script>" +
            "INSERT INTO ${cfg['oa.plan.detail.table']}(id,${cfg['oa.plan.field.seq1']},${cfg['oa.plan.field.disburse']},${cfg['oa.plan.field.balance']},${cfg['oa.plan.field.principal']},${cfg['oa.plan.field.interest']},${cfg['oa.plan.field.remark']},${cfg['oa.plan.field.date']},sort,formmain_id) VALUES " +
            "<foreach collection='timeList' item='time' index='index' separator=','>" +
            "(#{startId} + #{index},#{startSort} + #{index},null,null,null,null,null,#{time},#{startSort} + #{index},#{formmain_id})" +
            "</foreach>" +
            "</script>")
    void addDetail(@Param("formmain_id") String formmain_id,
                    @Param("timeList") List<String> timeList,
                    @Param("startId") Long startId,
                    @Param("startSort") Integer startSort,
                    @Param("cfg") Map<String, String> cfg);

    /**
     * 查询指定 formmain_id 下已存在的日期列表（用于去重）
     */
    @Select("SELECT CAST(${cfg['oa.plan.field.date']} AS DATE) as date_str FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} AND ${cfg['oa.plan.field.date']} IS NOT NULL")
    List<String> getExistingDates(@Param("formmain_id") String formmain_id,
                                    @Param("cfg") Map<String, String> cfg);

    /**
     * 查询指定 formmain_id 下当前最大的 sort 值
     */
    @Select("SELECT ISNULL(MAX(sort), 0) FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id}")
    Integer getMaxSort(@Param("formmain_id") String formmain_id,
                        @Param("cfg") Map<String, String> cfg);

    /**
     * 删除指定 formmain_id 下 field0026 为空的明细记录（插入前清理）
     */
    @Delete("DELETE FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} AND ${cfg['oa.plan.field.date']} IS NULL")
    void deleteEmptyDateRecords(@Param("formmain_id") String formmain_id,
                                  @Param("cfg") Map<String, String> cfg);

    /**
     * 根据 id 获取主表的开始时间和结束时间等信息
     */
    @Select("SELECT "
            + "${cfg['oa.plan.field.start.date']} AS field0059,"
            + "${cfg['oa.plan.field.end.date']} AS field0060,"
            + "${cfg['oa.plan.field.bank']} AS field0046,"
            + "${cfg['oa.plan.field.is.add.detail']} AS field0061,"
            + "${cfg['oa.plan.field.rate']} AS field0042,"
            + "field0067 "
            + "FROM ${cfg['oa.plan.main.table']} WHERE id = #{id}")
    Map<String, Object> getMainTableById(@Param("id") String id,
                                           @Param("cfg") Map<String, String> cfg);

    /**
     * 更新主表的是否添加明细表标记字段
     */
    @Update("UPDATE ${cfg['oa.plan.main.table']} SET ${cfg['oa.plan.field.is.add.detail']} = '已添加' WHERE id = #{id}")
    void updateIsAddDetailFlag(@Param("id") String id,
                                 @Param("cfg") Map<String, String> cfg);

    /**
     * 根据 id 获取主表的还本模式（此处表名固定未纳入 cfg）
     */
    @Select("SELECT ${cfg['oa.plan.field.repayment.mode']} FROM ${cfg['oa.plan.basic.table']} WHERE id = #{id}")
    String getRepaymentModeById(@Param("id") String id,
                                   @Param("cfg") Map<String, String> cfg);

    /**
     * 删除指定 formmain_id 下所有字段都为 null 的明细记录
     */
    @Delete("DELETE FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.disburse']} IS NULL " +
            "AND ${cfg['oa.plan.field.balance']} IS NULL " +
            "AND ${cfg['oa.plan.field.principal']} IS NULL " +
            "AND ${cfg['oa.plan.field.interest']} IS NULL " +
            "AND (${cfg['oa.plan.field.remark']} IS NULL OR ${cfg['oa.plan.field.remark']} = '') " +
            "AND ${cfg['oa.plan.field.date']} IS NULL")
    void deleteEmptyDetailRecords(@Param("formmain_id") String formmain_id,
                                    @Param("cfg") Map<String, String> cfg);

    /**
     * 根据流水号查询主表的贷款银行
     */
    @Select("SELECT ${cfg['oa.plan.field.bank']} AS field0046 FROM ${cfg['oa.plan.main.table']} WHERE ${cfg['oa.plan.serial.field']} = #{serialNumber}")
    String getLoanBankBySerialNumber(@Param("serialNumber") String serialNumber,
                                       @Param("cfg") Map<String, String> cfg);

    /**
     * 根据利率和流水号，根据流水号找到主表并更新利率
     */
    @Update("UPDATE ${cfg['oa.plan.main.table']} SET ${cfg['oa.plan.field.rate']} = #{rate} WHERE ${cfg['oa.plan.serial.field']} = #{serialNumber}")
    void updateRateBySerialNumber(@Param("rate") String rate,
                                    @Param("serialNumber") String serialNumber,
                                    @Param("cfg") Map<String, String> cfg);

    /**
     * 根据单据编号/流水号获取主表 id
     */
    @Select("SELECT id FROM ${cfg['oa.plan.main.table']} WHERE ${cfg['oa.plan.serial.field']} = #{serialNumber}")
    String getMainTableIdBySerialNumber(@Param("serialNumber") String serialNumber,
                                          @Param("cfg") Map<String, String> cfg);

    /**
     * 根据主表 id 获取流水号（单据编号）
     */
    @Select("SELECT ${cfg['oa.plan.serial.field']} FROM ${cfg['oa.plan.main.table']} WHERE id = #{id}")
    String getSerialNumberByMainTableId(@Param("id") String id,
                                           @Param("cfg") Map<String, String> cfg);

    /**
     * 根据明细表 id 获取流水号（单据编号）
     */
    @Select("SELECT ${cfg['oa.plan.serial.field']} FROM ${cfg['oa.plan.main.table']} " +
            "WHERE id = (SELECT formmain_id FROM ${cfg['oa.plan.detail.table']} WHERE id = #{detailTableId})")
    String getSerialNumberByDetailTableId(@Param("detailTableId") String detailTableId,
                                            @Param("cfg") Map<String, String> cfg);

    /**
     * 根据明细表 id 获取主表 id
     */
    @Select("SELECT formmain_id FROM ${cfg['oa.plan.detail.table']} WHERE id = #{detailTableId}")
    String getMainTableIdByDetailTableId(@Param("detailTableId") String detailTableId,
                                            @Param("cfg") Map<String, String> cfg);

    /**
     * 根据主表 id 获取明细表数据（按日期升序）
     */
    @Select("SELECT * FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} ORDER BY ${cfg['oa.plan.field.date']} ASC")
    List<Map<String, Object>> getDetailTableByFormmainId(@Param("formmain_id") String formmain_id,
                                                            @Param("cfg") Map<String, String> cfg);

    /**
     * 接收 formmain_id 与日期，查询对应日期行的贷款余额
     */
    @Select("SELECT ${cfg['oa.plan.field.balance']} AS field0028 FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} AND CAST(${cfg['oa.plan.field.date']} AS DATE) = CAST(#{time} AS DATE)")
    String getLoanBalanceByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                                               @Param("time") String time,
                                               @Param("cfg") Map<String, String> cfg);

    /**
     * 接收 formmain_id 与日期，查询对应日期行的还本
     */
    @Select("SELECT ${cfg['oa.plan.field.principal']} AS field0029 FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} AND CAST(${cfg['oa.plan.field.date']} AS DATE) = CAST(#{time} AS DATE)")
    String getRepaymentByFormmainIdAndTime(@Param("formmain_id") String formmain_id,
                                              @Param("time") String time,
                                              @Param("cfg") Map<String, String> cfg);

    /**
     * 更新指定日期行的贷款余额/付息，并同步写入模拟付息字段（field0033）
     */
    @Update("UPDATE ${cfg['oa.plan.detail.table']} SET ${cfg['oa.plan.field.balance']} = #{loanBalance}, ${cfg['oa.plan.field.interest']} = #{interest}, ${cfg['oa.plan.field.simulate.interest']} = #{interest} " +
            "WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.date']} >= CAST(#{time} AS DATE) " +
            "AND ${cfg['oa.plan.field.date']} < DATEADD(DAY, 1, CAST(#{time} AS DATE))")
    void updateDailyData(@Param("formmain_id") String formmain_id,
                          @Param("time") String time,
                          @Param("loanBalance") String loanBalance,
                          @Param("interest") String interest,
                          @Param("cfg") Map<String, String> cfg);

    /**
     * 更新主表合计字段
     */
    @Update("UPDATE ${cfg['oa.plan.main.table']} SET ${cfg['oa.plan.field.total.disburse']} = #{totalDisbursedAmount}, ${cfg['oa.plan.field.total.balance']} = #{totalLoanBalance}, ${cfg['oa.plan.field.total.principal']} = #{totalPrincipalPaid}, ${cfg['oa.plan.field.total.interest']} = #{totalInterestPaid} WHERE id = #{formmainId}")
    void updateMainTableSummaryFields(@Param("formmainId") String formmainId,
                                        @Param("totalDisbursedAmount") String totalDisbursedAmount,
                                        @Param("totalLoanBalance") String totalLoanBalance,
                                        @Param("totalPrincipalPaid") String totalPrincipalPaid,
                                        @Param("totalInterestPaid") String totalInterestPaid,
                                        @Param("cfg") Map<String, String> cfg);

    /**
     * 根据贷款银行查询所有按日还款计划表的流水号列表（此处表名固定未纳入 cfg）
     */
    @Select("SELECT ${cfg['oa.plan.field.bank.serial']} AS field0023 FROM ${cfg['oa.plan.basic.table']} WHERE ${cfg['oa.plan.field.bank.name']} = #{loanBank}")
    List<String> getLoanSerialNosByBank(@Param("loanBank") String loanBank,
                                           @Param("cfg") Map<String, String> cfg);

    /**
     * 查询指定 formmain_id 下首次下柜日期
     */
    @Select("SELECT TOP 1 CAST(${cfg['oa.plan.field.date']} AS DATE) as first_disbursement_date " +
            "FROM ${cfg['oa.plan.detail.table']} " +
            "WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.date']} IS NOT NULL " +
            "AND ${cfg['oa.plan.field.disburse']} IS NOT NULL " +
            "AND CAST(${cfg['oa.plan.field.disburse']} AS DECIMAL(18,2)) > 0 " +
            "ORDER BY ${cfg['oa.plan.field.date']} ASC")
    String getFirstDisbursementDate(@Param("formmain_id") String formmain_id,
                                      @Param("cfg") Map<String, String> cfg);

    /**
     * 查询今天或之后的第一个还款日的贷款余额
     */
    @Select("SELECT TOP 1 ${cfg['oa.plan.field.balance']} AS field0028 " +
            "FROM ${cfg['oa.plan.detail.table']} " +
            "WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.date']} IS NOT NULL " +
            "AND CAST(${cfg['oa.plan.field.date']} AS DATE) >= CAST(#{currentDate} AS DATE) " +
            "ORDER BY ${cfg['oa.plan.field.date']} ASC")
    String getLoanBalanceForFirstFutureDate(@Param("formmain_id") String formmain_id,
                                               @Param("currentDate") String currentDate,
                                               @Param("cfg") Map<String, String> cfg);

    /**
     * 更新主表的贷款余额合计（field0035）
     */
    @Update("UPDATE ${cfg['oa.plan.main.table']} SET ${cfg['oa.plan.field.total.balance']} = #{loanBalance} WHERE id = #{formmain_id}")
    void updateMainTableLoanBalanceSummary(@Param("formmain_id") String formmain_id,
                                              @Param("loanBalance") String loanBalance,
                                              @Param("cfg") Map<String, String> cfg);

    /**
     * 查询指定 formmain_id 下所有有日期的记录（用于修复 sort / field0025）
     */
    @Select("SELECT id, CAST(${cfg['oa.plan.field.date']} AS DATE) as date_str FROM ${cfg['oa.plan.detail.table']} WHERE formmain_id = #{formmain_id} AND ${cfg['oa.plan.field.date']} IS NOT NULL ORDER BY ${cfg['oa.plan.field.date']} ASC")
    List<Map<String, Object>> getExistingDateRecordsOrdered(@Param("formmain_id") String formmain_id,
                                                                @Param("cfg") Map<String, String> cfg);

    /**
     * 更新单条记录的 sort 和 field0025
     */
    @Update("UPDATE ${cfg['oa.plan.detail.table']} SET sort = #{sort}, ${cfg['oa.plan.field.seq1']} = #{sort} WHERE id = #{id}")
    void updateRecordSortAndField0025(@Param("id") String id,
                                         @Param("sort") Integer sort,
                                         @Param("cfg") Map<String, String> cfg);

    /**
     * 查询指定 formmain_id 与日期范围内的付息总和（用于按季/按月付息累加）
     */
    @Select("SELECT ISNULL(SUM(CAST(${cfg['oa.plan.field.interest']} AS DECIMAL(20,10))), 0) as total_interest " +
            "FROM ${cfg['oa.plan.detail.table']} " +
            "WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.date']} IS NOT NULL " +
            "AND ${cfg['oa.plan.field.interest']} IS NOT NULL " +
            "AND CAST(${cfg['oa.plan.field.date']} AS DATE) >= CAST(#{startDate} AS DATE) " +
            "AND CAST(${cfg['oa.plan.field.date']} AS DATE) <= CAST(#{endDate} AS DATE)")
    String getTotalInterestByDateRange(@Param("formmain_id") String formmain_id,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate,
                                          @Param("cfg") Map<String, String> cfg);

    /**
     * 批量将指定日期及之后的所有记录贷款余额和付息设置为 0
     * 用于提前退出优化：当某日贷款余额为 0 且无下柜时，后续日期无需逐日计算，一次性置 0 即可
     */
    @Update("UPDATE ${cfg['oa.plan.detail.table']} SET ${cfg['oa.plan.field.balance']} = '0', ${cfg['oa.plan.field.interest']} = '0', ${cfg['oa.plan.field.simulate.interest']} = '0' " +
            "WHERE formmain_id = #{formmain_id} " +
            "AND ${cfg['oa.plan.field.date']} IS NOT NULL " +
            "AND CAST(${cfg['oa.plan.field.date']} AS DATE) >= CAST(#{fromDate} AS DATE)")
    void batchUpdateZeroFromDate(@Param("formmain_id") String formmain_id,
                                   @Param("fromDate") String fromDate,
                                   @Param("cfg") Map<String, String> cfg);
}

