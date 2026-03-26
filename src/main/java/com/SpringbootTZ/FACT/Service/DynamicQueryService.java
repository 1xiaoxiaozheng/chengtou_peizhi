package com.SpringbootTZ.FACT.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringbootTZ.FACT.Entity.Condition;
import com.SpringbootTZ.FACT.Entity.ConditionRule;
import com.SpringbootTZ.FACT.Entity.SysSqlConfig;
import com.SpringbootTZ.FACT.Mapper.DynamicQueryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class DynamicQueryService {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryService.class);

    private static final Pattern IDENT_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final DynamicQueryDao dynamicQueryDao;
    private final ObjectMapper objectMapper;
    private final ConfigFileRepository configFileRepository;

    public DynamicQueryService(DynamicQueryDao dynamicQueryDao, ObjectMapper objectMapper, ConfigFileRepository configFileRepository) {
        this.dynamicQueryDao = dynamicQueryDao;
        this.objectMapper = objectMapper;
        this.configFileRepository = configFileRepository;
    }

    public List<Map<String, Object>> dynamicQuery(String sqlKey, Map<String, Object> params) {
        if (sqlKey == null || sqlKey.trim().isEmpty()) {
            throw new IllegalArgumentException("sqlKey不能为空");
        }
        String key = sqlKey.trim();

        SysSqlConfig config = configFileRepository.getSqlConfigByKey(key);
        if (config == null) {
            throw new IllegalArgumentException("未找到sqlKey配置: " + key);
        }

        String baseTable = validateIdentifier(config.getBaseTable(), "base_table");
        List<String> selectableFields = parseStringList(config.getSelectableFields());
        List<ConditionRule> conditionRules = parseConditionRules(config.getConditionFields());

        // 允许的入参字段：可查询字段 + 条件字段（严格校验，防止前端/调用方携带额外字段）
        Set<String> allowedKeys = new HashSet<>();
        allowedKeys.addAll(selectableFields);
        for (ConditionRule r : conditionRules) {
            allowedKeys.add(r.getField());
        }

        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        for (String reqKey : safeParams.keySet()) {
            if (!allowedKeys.contains(reqKey)) {
                throw new IllegalArgumentException("非法入参字段: " + reqKey);
            }
        }

        // 构建条件（只用配置里定义的规则）
        List<Condition> conditions = new ArrayList<>();
        for (ConditionRule rule : conditionRules) {
            if (rule == null) {
                continue;
            }
            String field = rule.getField();
            String op = rule.getOp();
            Object rawValue = safeParams.get(field);
            if (rawValue == null) {
                continue;
            }

            if ("eq".equals(op) || "like".equals(op)) {
                if (rawValue instanceof String && ((String) rawValue).trim().isEmpty()) {
                    continue;
                }
                Condition c = new Condition();
                c.setField(field);
                c.setOp(op);
                c.setValue(op.equals("like") ? rawValue.toString() : rawValue);
                conditions.add(c);
            } else if ("between".equals(op)) {
                BetweenPair pair = extractBetweenPair(rawValue);
                if (pair == null || pair.from == null || pair.to == null) {
                    continue;
                }
                Condition c = new Condition();
                c.setField(field);
                c.setOp(op);
                c.setValueFrom(pair.from);
                c.setValueTo(pair.to);
                conditions.add(c);
            } else {
                throw new IllegalArgumentException("不支持的op: " + op);
            }
        }

        // sort：允许 "field" 或 "field ASC" / "field DESC"，字段必须来自 selectable_fields
        String sort = normalizeAndValidateSort(config.getDefaultSort(), selectableFields);

        // 二次校验：字段名必须是合法标识符
        for (String f : selectableFields) {
            validateIdentifier(f, "selectable_fields字段");
        }
        for (Condition c : conditions) {
            validateIdentifier(c.getField(), "conditions字段");
        }

        log.debug("dynamicQuery start, sqlKey={}, table={}, fields={}, conditions={}",
                key, baseTable, selectableFields, conditions.size());

        return dynamicQueryDao.query(baseTable, selectableFields, conditions, sort);
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            // 兼容：如果配置不是标准JSON（例如逗号分隔），尝试兜底解析
            String normalized = json.trim();
            normalized = normalized.replace("[", "").replace("]", "").trim();
            if (normalized.isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = normalized.split(",");
            List<String> result = new ArrayList<>();
            for (String p : parts) {
                String s = p.trim();
                if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                    s = s.substring(1, s.length() - 1);
                }
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
            return result;
        }
    }

    private List<ConditionRule> parseConditionRules(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConditionRule>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("condition_fields配置不是合法JSON: " + e.getMessage(), e);
        }
    }

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

    private static String normalizeAndValidateSort(String rawSort, List<String> selectableFields) {
        if (rawSort == null || rawSort.trim().isEmpty()) {
            return null;
        }
        String s = rawSort.trim();
        String fieldPart;
        String dirPart = "ASC";
        if (s.contains(" ")) {
            String[] sp = s.split("\\s+");
            if (sp.length != 2) {
                throw new IllegalArgumentException("default_sort 格式应为 field 或 field ASC/DESC: " + s);
            }
            fieldPart = validateIdentifier(sp[0], "default_sort字段");
            dirPart = sp[1].trim().toUpperCase();
            if (!"ASC".equals(dirPart) && !"DESC".equals(dirPart)) {
                throw new IllegalArgumentException("default_sort 排序方向只能是 ASC 或 DESC");
            }
        } else {
            fieldPart = validateIdentifier(s, "default_sort字段");
        }
        if (!selectableFields.contains(fieldPart)) {
            throw new IllegalArgumentException("default_sort字段不在selectable_fields中: " + fieldPart);
        }
        return "ASC".equals(dirPart) ? fieldPart : fieldPart + " DESC";
    }

    private BetweenPair extractBetweenPair(Object rawValue) {
        // 支持三种格式：
        // 1) [from, to]
        // 2) {"from":..., "to":...} 或 {"start":..., "end":...}
        // 3) "from,to"（字符串兜底）
        if (rawValue instanceof List) {
            List<?> list = (List<?>) rawValue;
            if (list.size() != 2) {
                return null;
            }
            return new BetweenPair(list.get(0), list.get(1));
        }
        if (rawValue instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) rawValue;
            Object from = firstNonNull(m.get("from"), m.get("start"));
            Object to = firstNonNull(m.get("to"), m.get("end"));
            return new BetweenPair(from, to);
        }
        if (rawValue instanceof String) {
            String s = ((String) rawValue).trim();
            if (!s.contains(",")) {
                return null;
            }
            String[] parts = s.split(",");
            if (parts.length != 2) {
                return null;
            }
            return new BetweenPair(parts[0].trim(), parts[1].trim());
        }
        return null;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static class BetweenPair {
        private final Object from;
        private final Object to;

        private BetweenPair(Object from, Object to) {
            this.from = from;
            this.to = to;
        }
    }
}

