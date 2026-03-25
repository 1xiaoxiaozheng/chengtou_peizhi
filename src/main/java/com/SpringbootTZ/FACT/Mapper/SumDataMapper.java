package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 合计数据Mapper接口
 * 用于计算明细表的合计值并更新到主表
 */
@Mapper
public interface SumDataMapper {

    /**
     * 计算明细表中指定字段的合计值
     * 
     * @param detailTableName 明细表名（如：formson_0030）
     * @param formmainId      主表ID
     * @param fieldName       字段名（如：field0027）
     * @return 合计值（字符串格式）
     */
    @Select("SELECT ISNULL(SUM(CAST(${fieldName} AS DECIMAL(20,10))), 0) " +
            "FROM ${detailTableName} " +
            "WHERE formmain_id = #{formmainId} " +
            "AND ${fieldName} IS NOT NULL")
    String calculateSum(@Param("detailTableName") String detailTableName,
            @Param("formmainId") String formmainId,
            @Param("fieldName") String fieldName);

    /**
     * 更新主表中指定字段的值
     * 
     * @param mainTableName 主表名（如：formmain_0029）
     * @param formmainId    主表ID
     * @param fieldName     字段名（如：field0034）
     * @param fieldValue    字段值
     */
    @Update("UPDATE ${mainTableName} SET ${fieldName} = #{fieldValue} WHERE id = #{formmainId}")
    void updateFieldValue(@Param("mainTableName") String mainTableName,
            @Param("formmainId") String formmainId,
            @Param("fieldName") String fieldName,
            @Param("fieldValue") String fieldValue);
}
