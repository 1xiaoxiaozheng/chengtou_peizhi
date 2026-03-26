package com.SpringbootTZ.FACT.Mapper;

import com.SpringbootTZ.FACT.Entity.SysSqlConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SqlConfigDao {

    @Select("SELECT id, sql_key, base_table, selectable_fields, condition_fields, default_sort, remark " +
            "FROM [dbo].[sys_sql_config] " +
            "WHERE sql_key = #{sqlKey}")
    SysSqlConfig findByKey(@Param("sqlKey") String sqlKey);
}

