package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminSchemaMapper {

    @Select("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
            "WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA = 'dbo' " +
            "ORDER BY TABLE_NAME")
    List<String> listTables();

    @Select("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = #{tableName} " +
            "ORDER BY ORDINAL_POSITION")
    List<String> listColumns(@Param("tableName") String tableName);
}
