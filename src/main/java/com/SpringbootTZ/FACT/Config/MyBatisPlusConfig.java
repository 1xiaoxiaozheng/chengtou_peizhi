package com.SpringbootTZ.FACT.Config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis-Plus配置类
 * 配置动态表名处理器，支持根据配置文件动态替换表名
 */
@Configuration
public class MyBatisPlusConfig {

    private final TableNameConfig tableNameConfig;

    @Autowired
    public MyBatisPlusConfig(TableNameConfig tableNameConfig) {
        this.tableNameConfig = tableNameConfig;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 动态表名拦截器
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();

        // 表名映射：将硬编码的表名映射到配置的表名
        Map<String, String> tableNameMap = new HashMap<>();
        tableNameMap.put("formmain_0146", tableNameConfig.getDailyRepaymentPlanMain());
        tableNameMap.put("formson_0147", tableNameConfig.getDailyRepaymentPlanDetail());
        tableNameMap.put("formmain_0144", tableNameConfig.getMonthlyRepaymentPlanMain());
        tableNameMap.put("formson_0145", tableNameConfig.getMonthlyRepaymentPlanDetail());
        tableNameMap.put("formmain_0032", tableNameConfig.getInterestMain());

        // 设置表名处理器
        dynamicTableNameInnerInterceptor.setTableNameHandler((sql, tableName) -> {
            // 如果表名在映射中，返回配置的表名，否则返回原表名
            return tableNameMap.getOrDefault(tableName, tableName);
        });

        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);

        return interceptor;
    }
}
