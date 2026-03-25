package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface seeyonMapper {

    /**
     * 根据ID查询枚举值
     *
     * @param id 枚举项ID
     * @return 枚举值
     */
    @Select("SELECT SHOWVALUE FROM CTP_ENUM_ITEM WHERE id = #{id}")
    String getEnumValue1(@Param("id") String id);





}
