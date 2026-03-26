package com.SpringbootTZ.FACT.Mapper;

import com.SpringbootTZ.FACT.Entity.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DictDao {

    @Select("SELECT id, dict_type, dict_key, dict_value, sort, status, remark, create_time, update_time " +
            "FROM [dbo].[sys_dict] " +
            "WHERE dict_type = #{dictType} AND status = 1 " +
            "ORDER BY sort ASC")
    List<SysDict> findByType(@Param("dictType") String dictType);
}

