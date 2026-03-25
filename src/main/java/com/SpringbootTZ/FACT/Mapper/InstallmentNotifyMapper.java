package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;


@Mapper
// 分期还款计划表中间表的接口
public interface InstallmentNotifyMapper {

    // 查询表获取状态为0的记录，并附到实体类上
    @Select("SELECT * FROM Middle_Installment_Records WHERE process_status = 0 ORDER BY create_time ASC")
    public List<Map<String, Object>> getStatusNot();

    /**
     * 查询待处理记录，并关联 formson_0043 获取主表ID（formmain_id）
     * 用于按主表合并任务，同一主表的多条任务只执行一次计算
     */
    @Select("SELECT mir.id, mir.target_table_id, mir.monitored_field, f.formmain_id " +
                    "FROM Middle_Installment_Records mir " +
                    "INNER JOIN formson_0043 f ON mir.target_table_id = f.id " +
                    "WHERE mir.process_status = 0 " +
                    "ORDER BY mir.create_time ASC")
    List<Map<String, Object>> getPendingRecordsWithMainTableId();

    // 根据id，更新处理状态为成功（1）
    @Update("UPDATE Middle_Installment_Records SET process_status = 1, process_time = GETDATE() WHERE id = #{id}")
    public void updateProcessStatus(@Param("id") Integer id);

    // 根据id，更新处理状态为失败（2），并记录失败原因
    @Update("UPDATE Middle_Installment_Records SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id = #{id}")
    public void updateProcessStatusFailed(@Param("id") Integer id, @Param("failReason") String failReason);

    // 根据id，重试次数+1
    @Update("UPDATE Middle_Installment_Records SET retry_count = retry_count + 1 WHERE id = #{id}")
    void incrementRetryCount(@Param("id") Integer id);

    /**
     * 批量更新处理状态为成功（1）
     */
    @Update("<script>" +
                    "UPDATE Middle_Installment_Records SET process_status = 1, process_time = GETDATE() WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchUpdateProcessStatus(@Param("ids") List<Integer> ids);

    /**
     * 批量更新处理状态为失败（2）
     */
    @Update("<script>" +
                    "UPDATE Middle_Installment_Records SET process_status = 2, process_time = GETDATE(), fail_reason = #{failReason} WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchUpdateProcessStatusFailed(@Param("ids") List<Integer> ids, @Param("failReason") String failReason);

    /**
     * 批量重试次数+1
     */
    @Update("<script>" +
                    "UPDATE Middle_Installment_Records SET retry_count = retry_count + 1 WHERE id IN " +
                    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
                    "#{id}" +
                    "</foreach>" +
                    "</script>")
    void batchIncrementRetryCount(@Param("ids") List<Integer> ids);

}
