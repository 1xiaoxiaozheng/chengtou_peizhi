package com.SpringbootTZ.FACT.Mapper;

import com.SpringbootTZ.FACT.Entity.InterestChangeNotify;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface InterestNotifyMapper extends BaseMapper<InterestChangeNotify> {

        /**
         * 根据处理状态查询记录
         * 
         * @param status 处理状态 (0-未处理，1-已处理，2-处理失败)
         * @return 记录列表
         */
        @Select("SELECT * FROM interest_change_notify WHERE process_status = #{status} ORDER BY rate_effective_time DESC")
        List<InterestChangeNotify> selectByStatus(Integer status);

        /**
         * 查询状态为0（未处理）的记录
         * 
         * @return 未处理的记录列表
         */
        @Select("SELECT * FROM interest_change_notify WHERE process_status = 0 ORDER BY create_time ASC")
        List<InterestChangeNotify> getStatusNot();

        /**
         * 更新处理状态为已处理
         * 
         * @param id 记录ID
         * @return 影响行数
         */
        @Update("UPDATE interest_change_notify SET process_status = 1 WHERE id = #{id}")
        int updateProcessStatus(@Param("id") Long id);

        /**
         * 更新处理状态为处理失败
         * 
         * @param id         记录ID
         * @param failReason 失败原因
         * @return 影响行数
         */
        @Update("UPDATE interest_change_notify SET process_status = 2 WHERE id = #{id}")
        int updateProcessStatusFailed(@Param("id") Long id);

        /**
         * 根据ID更新记录
         * 
         * @param notify 要更新的记录
         * @return 影响行数
         */
        @Update("UPDATE interest_change_notify SET process_status = #{processStatus} WHERE id = #{id}")
        int updateById(InterestChangeNotify notify);

        /**
         * 删除利率表明细表（formson_0033）中指定主表的所有记录
         * 
         * @param formmain_id 利率表主表ID（formmain_0032）
         * @return 影响行数
         */
        @Delete("DELETE FROM formson_0033 WHERE formmain_id = #{formmain_id}")
        int deleteInterestDetailRecords(@Param("formmain_id") Long formmain_id);

        /**
         * 批量插入数据到利率表明细表（formson_0033）
         * 数据字典：formson_0033
         * field0025: 序号, field0026: 时间, field0027: 下柜资金, field0028: 贷款余额
         * field0029: 还本, field0030: 付息, field0032: 计划还本, field0033: 模拟付息
         * 
         * @param records     要插入的记录列表
         * @param formmain_id 利率表主表ID（formmain_0032）
         * @return 影响行数
         */
        @Insert("<script>" +
                        "INSERT INTO formson_0033 (id, formmain_id, field0025, field0026, field0027, field0028, field0029, field0030, field0032, field0033, sort) VALUES "
                        +
                        "<foreach collection='records' item='record' separator=','>" +
                        "(#{record.id}, #{formmain_id}, " +
                        "#{record.field0025}, " +
                        "#{record.field0026}, " +
                        "#{record.field0027}, " +
                        "#{record.field0028}, " +
                        "#{record.field0029}, " +
                        "#{record.field0030}, " +
                        "#{record.field0032}, " +
                        "#{record.field0033}, " +
                        "#{record.sort})" +
                        "</foreach>" +
                        "</script>")
        int batchInsertInterestDetailRecords(@Param("records") List<Map<String, Object>> records,
                        @Param("formmain_id") Long formmain_id);

        /**
         * 根据单据编号/流水号查询最新的利率表主表ID
         * 数据字典：formmain_0032
         * field0001: 单据编号/流水号
         * field0041: 生效时间 (DATETIME)
         * 
         * @param billNo 单据编号/流水号
         * @return 最新的利率表主表ID（按生效时间降序，取第一条）
         */
        @Select("SELECT TOP 1 id FROM formmain_0032 WHERE field0001 = #{billNo} ORDER BY field0041 DESC")
        Long getLatestInterestTableIdByBillNo(@Param("billNo") String billNo);

        /**
         * 查询利率表明细表（formson_0033）中今天或之后的第一个还款日的贷款余额
         * 数据字典：formson_0033，field0026 时间，field0028 贷款余额
         *
         * @param formmain_id 利率表主表ID（formmain_0032）
         * @param currentDate 当前日期（yyyy-MM-dd）
         * @return 贷款余额，若未找到则返回 null
         */
        @Select("SELECT TOP 1 field0028 " +
                        "FROM formson_0033 " +
                        "WHERE formmain_id = #{formmain_id} " +
                        "AND field0026 IS NOT NULL " +
                        "AND CAST(field0026 AS DATE) >= CAST(#{currentDate} AS DATE) " +
                        "ORDER BY field0026 ASC")
        String getLoanBalanceForFirstFutureDate(@Param("formmain_id") String formmain_id,
                        @Param("currentDate") String currentDate);
}
