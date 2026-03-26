package com.SpringbootTZ.FACT.Entity;

/**
 * 对应表 oa_dict_config，用于按日/ OA 相关动态 SQL 的配置行。
 * （演示代码：复制到主工程 src/main/java 同包路径即可）
 */
public class OaDictConfig {

    private Integer id;
    private String configKey;
    private String configValue;
    private String configName;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }
}
