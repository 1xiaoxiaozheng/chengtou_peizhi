package com.SpringbootTZ.FACT.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 合计数据Mapper接口
 * 用于计算明细表的合计值并更新到主表
 */
@Mapper
public interface SumDataMapper {

    /**
     * SQL Provider：将表名/字段名作为“受控配置项”动态拼装，
     * 避免在 @Select/@Update 注解里直接使用 `${}` 造成的任意SQL注入风险。
     *
     * 说明：
     * - 仍然需要调用方（Service）基于 sys_sql_config 做白名单校验；
     * - 本 Provider 会做最低限度的正则校验（表名/字段名必须符合既定命名规则）。
     */
    class SqlProvider {
        // 当前项目里出现的表命名：formson_0030 / formmain_0029 这种格式
        private static final Pattern DETAIL_TABLE_PATTERN = Pattern.compile("^formson_\\d{4}$");
        private static final Pattern MAIN_TABLE_PATTERN = Pattern.compile("^formmain_\\d{4}$");
        // 当前项目字段命名：field0027 / field0034 等
        private static final Pattern FIELD_PATTERN = Pattern.compile("^field\\d{4}$");

        private static String validateIdentifier(String raw, Pattern pattern, String name) {
            if (raw == null) {
                throw new IllegalArgumentException(name + "不能为空");
            }
            String value = raw.trim();
            if (!pattern.matcher(value).matches()) {
                throw new IllegalArgumentException("非法" + name + ": " + value);
            }
            return value;
        }

        /**
         * 计算明细表中指定字段的合计值（在 SQL Server 下）
         */
        public String calculateSumSql(Map<String, Object> params) {
            String detailTableName = (String) params.get("detailTableName");
            String fieldName = (String) params.get("fieldName");

            // 表名/字段名属于标识符，不能用 #{ } 参数化；因此必须校验后再拼接
            detailTableName = validateIdentifier(detailTableName, DETAIL_TABLE_PATTERN, "明细表名");
            fieldName = validateIdentifier(fieldName, FIELD_PATTERN, "字段名");

            return "SELECT ISNULL(SUM(CAST([" + fieldName + "] AS DECIMAL(20,10))), 0) " +
                    "FROM [" + detailTableName + "] " +
                    "WHERE formmain_id = #{formmainId} " +
                    "AND [" + fieldName + "] IS NOT NULL";
        }

        /**
         * 更新主表中指定字段的值（在 SQL Server 下）
         */
        public String updateFieldValueSql(Map<String, Object> params) {
            String mainTableName = (String) params.get("mainTableName");
            String fieldName = (String) params.get("fieldName");

            mainTableName = validateIdentifier(mainTableName, MAIN_TABLE_PATTERN, "主表名");
            fieldName = validateIdentifier(fieldName, FIELD_PATTERN, "字段名");

            return "UPDATE [" + mainTableName + "] SET [" + fieldName + "] = #{fieldValue} " +
                    "WHERE id = #{formmainId}";
        }
    }

    /**
     * 计算明细表中指定字段的合计值
     * 
     * @param detailTableName 明细表名（如：formson_0030）
     * @param formmainId      主表ID
     * @param fieldName       字段名（如：field0027）
     * @return 合计值（字符串格式）
     */
    @SelectProvider(type = SqlProvider.class, method = "calculateSumSql")
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
    @UpdateProvider(type = SqlProvider.class, method = "updateFieldValueSql")
    void updateFieldValue(@Param("mainTableName") String mainTableName,
            @Param("formmainId") String formmainId,
            @Param("fieldName") String fieldName,
            @Param("fieldValue") String fieldValue);
}
