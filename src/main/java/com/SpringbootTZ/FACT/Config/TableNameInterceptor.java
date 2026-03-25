package com.SpringbootTZ.FACT.Config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MyBatis拦截器：动态替换SQL中的表名
 * 根据配置文件中的表名配置，自动替换SQL中的硬编码表名
 * 
 * 使用说明：
 * 1. 在application.properties中配置表名
 * 2. 拦截器会自动替换所有SQL中的硬编码表名
 * 3. 支持按日还款计划表、按月还款计划表、利率表
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }),
        @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class,
                org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class })
})
public class TableNameInterceptor implements Interceptor {

    private final TableNameConfig tableNameConfig;

    @Autowired
    public TableNameInterceptor(TableNameConfig tableNameConfig) {
        this.tableNameConfig = tableNameConfig;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);

        // 获取原始SQL
        String originalSql = boundSql.getSql();

        // 替换表名
        String newSql = replaceTableNames(originalSql);

        // 如果SQL被修改，使用反射修改BoundSql中的sql字段
        if (!newSql.equals(originalSql)) {
            MetaObject metaObject = MetaObject.forObject(boundSql,
                    new DefaultObjectFactory(),
                    new DefaultObjectWrapperFactory(),
                    new DefaultReflectorFactory());
            metaObject.setValue("sql", newSql);
        }

        return invocation.proceed();
    }

    /**
     * 替换SQL中的表名
     */
    private String replaceTableNames(String sql) {
        String result = sql;

        // 替换按日还款计划表
        result = result.replaceAll("\\bformmain_0146\\b", tableNameConfig.getDailyRepaymentPlanMain());
        result = result.replaceAll("\\bformson_0147\\b", tableNameConfig.getDailyRepaymentPlanDetail());

        // 替换按月还款计划表
        result = result.replaceAll("\\bformmain_0144\\b", tableNameConfig.getMonthlyRepaymentPlanMain());
        result = result.replaceAll("\\bformson_0145\\b", tableNameConfig.getMonthlyRepaymentPlanDetail());

        // 替换利率表
        result = result.replaceAll("\\bformmain_0032\\b", tableNameConfig.getInterestMain());

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以在这里设置属性
    }
}
