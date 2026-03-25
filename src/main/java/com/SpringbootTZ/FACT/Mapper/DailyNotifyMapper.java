package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
// 按日还款计划表中间表的接口

public interface DailyNotifyMapper {

    // 查询表获取状态为0的记录，并附到实体类上
    @Select("SELECT * FROM Middle_Insert_Records WHERE process_status = 0 ORDER BY create_time ASC")
    public List<Map<String, Object>> getStatusNot();

    /**
     * 查询有待处理按日表任务的流水号集合
     * 用于分期表处理前判断：若同流水号的按日表任务待处理，应跳过分期表，等下轮按日表先算
     */
    @Select("SELECT DISTINCT m.field0001 " +
                    "FROM Middle_Insert_Records mir " +
                    "INNER JOIN formson_0030 f ON mir.target_table_id = f.id " +
                    "INNER JOIN formmain_0029 m ON f.formmain_id = m.id " +
                    "WHERE mir.process_status = 0 AND m.field0001 IS NOT NULL AND m.field0001 <> ''")
    List<String> getPendingSerialNumbers();

    /**
     * 查询待处理记录，并关联 formson_0030 获取主表ID（formmain_id）
     * 用于按主表合并任务，避免同一主表重复计算
     */
    @Select("SELECT mir.id, mir.target_table_id, mir.monitored_field, mir.old_value, f.formmain_id " +
                    "FROM Middle_Insert_Records mir " +
                    "INNER JOIN formson_0030 f ON mir.target_table_id = f.id " +
                    "WHERE mir.process_status = 0 " +
                    "ORDER BY mir.create_time ASC")
    List<Map<String, Object>> getPendingRecordsWithMainTableId();

    // 根据id，更新处理状态为成功（1）
    @Update("UPDATE Middle_Insert_Records SET process_status = 1, process_time = GETDATE() WHERE id = #{id}")
    public void updateProcessStatus(@Param("id") Integer id);

    // 根据id，更新处理状态为失败（2），并记录失败原因
    @Update("UPDATE Middle_Insert_Records SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id = #{id}")
    public void updateProcessStatusFailed(@Param("id") Integer id, @Param("failReason") String failReason);

    // 根据id，重试次数+1
    @Update("UPDATE Middle_Insert_Records SET retry_count = retry_count + 1 WHERE id = #{id}")
    void incrementRetryCount(@Param("id") Integer id);

    /**
     * 批量更新处理状态为成功（1）
     * 用于合并同一主表的多条任务后一次性标记
     */
    @Update("<script>" +
                    "UPDATE Middle_Insert_Records SET process_status = 1, process_time = GETDATE() WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchUpdateProcessStatus(@Param("ids") List<Integer> ids);

    /**
     * 批量更新处理状态为失败（2），并记录失败原因
     */
    @Update("<script>" +
                    "UPDATE Middle_Insert_Records SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchUpdateProcessStatusFailed(@Param("ids") List<Integer> ids, @Param("failReason") String failReason);

    /**
     * 批量重试次数+1
     */
    @Update("<script>" +
                    "UPDATE Middle_Insert_Records SET retry_count = retry_count + 1 WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchIncrementRetryCount(@Param("ids") List<Integer> ids);

}
