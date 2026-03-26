package com.SpringbootTZ.FACT.Mapper;

import com.SpringbootTZ.FACT.Entity.OaDictConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 oa_dict_config，供按日 Notify 等链路拼装动态 SQL。
 * Map 的 key 与表 config_key 一致（含点号，如 middle.insert.table）。
 */
@Mapper
public interface OaDictConfigMapper {

    @Select("SELECT id, config_key, config_value, config_name FROM oa_dict_config")
    @Results({
            @Result(property = "configKey", column = "config_key"),
            @Result(property = "configValue", column = "config_value"),
            @Result(property = "configName", column = "config_name")
    })
    List<OaDictConfig> selectAll();

    @Insert("INSERT INTO oa_dict_config (config_key, config_value, config_name) VALUES (#{configKey}, #{configValue}, #{configName})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(OaDictConfig row);

    @Update("UPDATE oa_dict_config SET config_key = #{configKey}, config_value = #{configValue}, config_name = #{configName} WHERE id = #{id}")
    int updateById(OaDictConfig row);

    @Delete("DELETE FROM oa_dict_config WHERE id = #{id}")
    int deleteById(@Param("id") Integer id);

    /**
     * 每次调用重新查库，保证运维在页面改库后尽快生效（无需重启）。
     */
    default Map<String, String> selectAsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (OaDictConfig row : selectAll()) {
            if (row.getConfigKey() != null && row.getConfigValue() != null) {
                map.put(row.getConfigKey().trim(), row.getConfigValue().trim());
            }
        }
        return map;
    }
}
