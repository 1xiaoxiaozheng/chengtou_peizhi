package com.SpringbootTZ.FACT.Mapper;

import com.SpringbootTZ.FACT.Entity.Condition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Mapper
public interface DynamicQueryDao {

    @SelectProvider(type = SqlProvider.class, method = "querySql")
    List<Map<String, Object>> query(
            @Param("table") String table,
            @Param("fields") List<String> fields,
            @Param("conditions") List<Condition> conditions,
            @Param("sort") String sort);

    class SqlProvider {
        private static final Pattern IDENT_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

        private static String validateIdentifier(String raw, String name) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new IllegalArgumentException(name + "不能为空");
            }
            String v = raw.trim();
            if (!IDENT_PATTERN.matcher(v).matches()) {
                throw new IllegalArgumentException("非法" + name + ": " + v);
            }
            return v;
        }

        public String querySql(Map<String, Object> params) {
            String table = (String) params.get("table");
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) params.get("fields");
            @SuppressWarnings("unchecked")
            List<Condition> conditions = (List<Condition>) params.get("conditions");
            String sort = (String) params.get("sort");

            table = validateIdentifier(table, "表名");

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ");
            for (int i = 0; i < fields.size(); i++) {
                String f = validateIdentifier(fields.get(i), "字段名");
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("[").append(f).append("]");
            }
            sb.append(" FROM [").append(table).append("]");

            if (conditions != null && !conditions.isEmpty()) {
                sb.append(" WHERE 1=1 ");
                for (int i = 0; i < conditions.size(); i++) {
                    Condition c = conditions.get(i);
                    String field = validateIdentifier(c.getField(), "条件字段名");
                    String op = c.getOp();
                    if ("eq".equals(op)) {
                        sb.append(" AND [").append(field).append("] = #{conditions[").append(i).append("].value} ");
                    } else if ("like".equals(op)) {
                        sb.append(" AND [").append(field).append("] LIKE CONCAT('%', #{conditions[").append(i).append("].value}, '%') ");
                    } else if ("between".equals(op)) {
                        sb.append(" AND [").append(field).append("] BETWEEN #{conditions[").append(i).append("].valueFrom} ");
                        sb.append(" AND #{conditions[").append(i).append("].valueTo} ");
                    } else {
                        throw new IllegalArgumentException("不支持的操作符: " + op);
                    }
                }
            }

            if (sort != null && !sort.trim().isEmpty()) {
                String rawSort = sort.trim();
                String col;
                String direction = "ASC";
                if (rawSort.contains(" ")) {
                    String[] sp = rawSort.split("\\s+");
                    if (sp.length != 2) {
                        throw new IllegalArgumentException("非法排序表达式: " + rawSort);
                    }
                    col = validateIdentifier(sp[0], "排序字段名");
                    direction = sp[1].trim().toUpperCase();
                    if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                        throw new IllegalArgumentException("排序方向只能是 ASC 或 DESC");
                    }
                } else {
                    col = validateIdentifier(rawSort, "排序字段名");
                }
                sb.append(" ORDER BY [").append(col).append("] ").append(direction);
            }
            return sb.toString();
        }
    }
}

