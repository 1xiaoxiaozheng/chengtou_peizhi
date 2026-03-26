package com.SpringbootTZ.FACT.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 读取 form_meta_config.json，生成 SQL Server 版的中间表/触发器脚本（只生成文件，不执行）。
 * 目标是“没数据库也能验证产物形态”，后续再逐步补齐幂等迁移体系与结构化流水表升级。
 */
@Service
public class FormMetaSqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(FormMetaSqlGenerator.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper objectMapper;

    @Value("${app.meta.config.classpath:config/form_meta_config.json}")
    private String metaConfigClasspath = "config/form_meta_config.json";

    @Value("${app.meta.generate.out-dir:}")
    private String outDirProp = "";

    public FormMetaSqlGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static class GenerateResult {
        private final Path outDir;
        private final List<String> files;
        private final List<String> warnings;

        public GenerateResult(Path outDir, List<String> files, List<String> warnings) {
            this.outDir = outDir;
            this.files = files;
            this.warnings = warnings;
        }

        public Path getOutDir() {
            return outDir;
        }

        public List<String> getFiles() {
            return files;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public GenerateResult generateToDefaultDir() throws Exception {
        return generate(resolveOutDir(null));
    }

    public GenerateResult generateToDir(String outDir) throws Exception {
        return generate(resolveOutDir(outDir));
    }

    private Path resolveOutDir(String outDirOverride) {
        String raw = outDirOverride;
        if (raw == null || raw.trim().isEmpty()) {
            raw = outDirProp;
        }
        if (raw == null || raw.trim().isEmpty()) {
            raw = Paths.get(System.getProperty("user.dir"), "fact-generated-sql").toString();
        }
        Path base = Paths.get(raw.trim()).toAbsolutePath().normalize();
        String sub = "meta_" + LocalDateTime.now().format(TS);
        return base.resolve(sub).toAbsolutePath().normalize();
    }

    private GenerateResult generate(Path outDir) throws Exception {
        JsonNode root = readMetaConfig();
        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException("form_meta_config.json 为空或不可读");
        }

        Files.createDirectories(outDir);

        List<String> warnings = new ArrayList<>();
        List<String> files = new ArrayList<>();

        JsonNode forms = root.path("forms");
        if (!forms.isObject()) {
            throw new IllegalStateException("meta config 缺少 forms 对象");
        }

        // 先收集需要的中间表（按 output.table）
        Set<String> neededTables = new LinkedHashSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = forms.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> f = it.next();
            JsonNode events = f.getValue().path("events");
            if (!events.isObject()) {
                continue;
            }
            for (Iterator<Map.Entry<String, JsonNode>> eit = events.fields(); eit.hasNext(); ) {
                Map.Entry<String, JsonNode> ev = eit.next();
                String outTable = ev.getValue().path("output").path("table").asText(null);
                if (outTable != null && !outTable.trim().isEmpty()) {
                    neededTables.add(outTable.trim());
                }
            }
        }

        // 生成中间表建表脚本（仅覆盖本项目已出现的中间表名）
        for (String t : neededTables) {
            String ddl = generateTableDdlIfKnown(t, warnings);
            if (ddl != null) {
                String file = "001_table_" + t + ".sql";
                writeFile(outDir.resolve(file), ddl);
                files.add(file);
            } else {
                warnings.add("未知中间表（未生成建表脚本）: " + t);
            }
        }

        // 生成触发器脚本（按 event 逐个产出）
        int idx = 10;
        for (Iterator<Map.Entry<String, JsonNode>> it = forms.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> f = it.next();
            String formKey = f.getKey();
            JsonNode formNode = f.getValue();
            JsonNode events = formNode.path("events");
            if (!events.isObject()) {
                continue;
            }
            for (Iterator<Map.Entry<String, JsonNode>> eit = events.fields(); eit.hasNext(); ) {
                Map.Entry<String, JsonNode> ev = eit.next();
                String eventKey = ev.getKey();
                JsonNode eventNode = ev.getValue();
                String type = eventNode.path("type").asText("");
                String triggerName = eventNode.path("triggerName").asText("");
                String sourceTable = eventNode.path("source").path("table").asText("");
                if (triggerName.isEmpty() || sourceTable.isEmpty() || type.isEmpty()) {
                    warnings.add("跳过事件（缺 triggerName/source.table/type）: " + formKey + "." + eventKey);
                    continue;
                }

                String sql;
                if ("afterInsert".equalsIgnoreCase(type)) {
                    sql = generateAfterInsertTrigger(eventNode, warnings);
                } else if ("rowChange".equalsIgnoreCase(type)) {
                    sql = generateRowChangeTrigger(eventNode, warnings);
                } else {
                    warnings.add("未知事件类型（未生成触发器）: " + type + " for " + formKey + "." + eventKey);
                    continue;
                }

                String safeName = (idx < 100 ? "0" : "") + idx;
                String file = safeName + "_trg_" + triggerName + ".sql";
                writeFile(outDir.resolve(file), sql);
                files.add(file);
                idx++;
            }
        }

        log.info("元数据生成完成：outDir={}, files={}, warnings={}", outDir, files.size(), warnings.size());
        return new GenerateResult(outDir, files, warnings);
    }

    private JsonNode readMetaConfig() throws Exception {
        String classpath = (metaConfigClasspath == null || metaConfigClasspath.trim().isEmpty())
                ? "config/form_meta_config.json"
                : metaConfigClasspath.trim();
        ClassPathResource res = new ClassPathResource(Objects.requireNonNull(classpath, "meta config path is null"));
        if (!res.exists()) {
            throw new IllegalStateException("classpath 未找到 meta config: " + classpath);
        }
        return objectMapper.readTree(res.getInputStream());
    }

    private void writeFile(Path p, String content) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    private String generateTableDdlIfKnown(String table, List<String> warnings) {
        if ("interest_change_notify".equalsIgnoreCase(table)) {
            return joinLines(
                    "-- 生成自 form_meta_config.json：interest_change_notify（SQL Server）",
                    "CREATE TABLE interest_change_notify (",
                    "    id BIGINT IDENTITY(1,1) PRIMARY KEY,",
                    "    source_id BIGINT NOT NULL,",
                    "    bill_no NVARCHAR(255) NOT NULL,",
                    "    current_rate NUMERIC(20,6) NOT NULL,",
                    "    rate_effective_time DATETIME NOT NULL,",
                    "    process_status TINYINT DEFAULT 0,",
                    "    create_time DATETIME DEFAULT GETDATE()",
                    ");",
                    "",
                    "CREATE INDEX idx_interest_notify_source_id ON interest_change_notify(source_id);",
                    "CREATE INDEX idx_interest_notify_bill_no ON interest_change_notify(bill_no);",
                    "CREATE INDEX idx_interest_notify_process_status ON interest_change_notify(process_status);",
                    ""
            );
        }
        if ("Middle_Installment_Records".equalsIgnoreCase(table)) {
            return joinLines(
                    "-- 生成自 form_meta_config.json：Middle_Installment_Records（SQL Server）",
                    "CREATE TABLE Middle_Installment_Records (",
                    "  id INT IDENTITY(1,1) PRIMARY KEY,",
                    "  target_table_id NVARCHAR(50) NOT NULL,",
                    "  monitored_field NVARCHAR(255),",
                    "  create_time DATETIME DEFAULT GETDATE(),",
                    "  process_status TINYINT DEFAULT 0,",
                    "  process_time DATETIME NULL,",
                    "  fail_reason NVARCHAR(1000) NULL,",
                    "  retry_count INT DEFAULT 0",
                    ");",
                    "",
                    "CREATE INDEX idx_Middle_Installment_Records_target_id ON Middle_Installment_Records(target_table_id);",
                    "CREATE INDEX idx_Middle_Installment_Records_process_status ON Middle_Installment_Records(process_status);",
                    ""
            );
        }
        if ("Middle_Insert_Records".equalsIgnoreCase(table)) {
            return joinLines(
                    "-- 生成自 form_meta_config.json：Middle_Insert_Records（SQL Server）",
                    "CREATE TABLE Middle_Insert_Records (",
                    "  id INT IDENTITY(1,1) PRIMARY KEY,",
                    "  target_table_id NVARCHAR(50) NOT NULL,",
                    "  monitored_field NVARCHAR(255),",
                    "  create_time DATETIME DEFAULT GETDATE(),",
                    "  process_status TINYINT DEFAULT 0,",
                    "  process_time DATETIME NULL,",
                    "  fail_reason NVARCHAR(1000) NULL,",
                    "  retry_count INT DEFAULT 0",
                    ");",
                    "",
                    "CREATE INDEX idx_Middle_Insert_Records_target_id ON Middle_Insert_Records(target_table_id);",
                    "CREATE INDEX idx_Middle_Insert_Records_process_status ON Middle_Insert_Records(process_status);",
                    ""
            );
        }
        warnings.add("中间表未内置 DDL 模板: " + table);
        return null;
    }

    private String generateAfterInsertTrigger(JsonNode eventNode, List<String> warnings) {
        String triggerName = eventNode.path("triggerName").asText("");
        String sourceTable = eventNode.path("source").path("table").asText("");
        String outTable = eventNode.path("output").path("table").asText("");
        JsonNode mapping = eventNode.path("output").path("mapping");
        JsonNode whereNotNull = eventNode.path("output").path("whereNotNull");

        if (mapping == null || !mapping.isObject()) {
            warnings.add("afterInsert 缺少 output.mapping: " + triggerName);
            return "-- 无法生成：缺少 output.mapping";
        }
        List<String> cols = new ArrayList<>();
        List<String> selects = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = mapping.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> m = it.next();
            String col = m.getKey();
            JsonNode v = m.getValue();
            cols.add(col);
            selects.add(mapSelectExpr(v, "i", warnings));
        }

        String where = "";
        if (whereNotNull != null && whereNotNull.isArray() && whereNotNull.size() > 0) {
            List<String> parts = new ArrayList<>();
            for (JsonNode n : whereNotNull) {
                if (n == null || n.isNull()) {
                    continue;
                }
                String f = n.asText("");
                if (!f.isEmpty()) {
                    parts.add("i." + f + " IS NOT NULL");
                }
            }
            if (!parts.isEmpty()) {
                where = "    WHERE\n        " + String.join("\n        AND ", parts);
            }
        }

        return joinLines(
                "-- 生成自 form_meta_config.json：afterInsert",
                "IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = '" + triggerName + "')",
                "DROP TRIGGER " + triggerName + ";",
                "GO",
                "",
                "CREATE TRIGGER " + triggerName,
                "ON " + sourceTable,
                "AFTER INSERT",
                "AS",
                "BEGIN",
                "    SET NOCOUNT ON;",
                "",
                "    INSERT INTO " + outTable + " (",
                "        " + String.join(",\n        ", cols),
                "    )",
                "    SELECT",
                "        " + String.join(",\n        ", selects),
                "    FROM INSERTED i",
                (where.isEmpty() ? "" : where),
                "END;",
                "GO",
                ""
        );
    }

    private String generateRowChangeTrigger(JsonNode eventNode, List<String> warnings) {
        String triggerName = eventNode.path("triggerName").asText("");
        String sourceTable = eventNode.path("source").path("table").asText("");
        String outTable = eventNode.path("output").path("table").asText("");
        String monitoredFieldFormat = eventNode.path("output").path("monitoredFieldFormat").asText("{fieldCode}={value}");
        boolean skipWhenNewValueIsNull = eventNode.path("output").path("skipWhenNewValueIsNull").asBoolean(true);

        JsonNode monitoredFields = eventNode.path("monitoredFields");
        if (monitoredFields == null || !monitoredFields.isArray() || monitoredFields.size() == 0) {
            warnings.add("rowChange 缺少 monitoredFields: " + triggerName);
            return "-- 无法生成：缺少 monitoredFields";
        }

        // businessFilters（目前按 enumShowValue 生成 SHOWVALUE 过滤）
        BusinessFilterSql filterSql = buildBusinessFilterSql(eventNode, warnings);

        List<String> updateInserts = new ArrayList<>();
        List<String> insertInserts = new ArrayList<>();

        for (JsonNode mf : monitoredFields) {
            String childField = mf.path("childField").asText("");
            String logFieldCode = mf.path("logFieldCode").asText(childField);
            boolean onlyIfNewNotNull = mf.path("onlyIfNewNotNull").asBoolean(false);

            if (childField.isEmpty()) {
                continue;
            }

            String monitoredExpr = buildMonitoredFieldExpr(monitoredFieldFormat, logFieldCode, "i." + childField, warnings);
            String baseJoin = joinLinesNonEmpty(
                    "        FROM inserted i",
                    "        JOIN deleted d ON i.id = d.id",
                    filterSql.joinMainSql,
                    filterSql.joinEnumSql
            );

            List<String> whereParts = new ArrayList<>();
            whereParts.add("(" + valueChangedExpr("i." + childField, "d." + childField) + ")");
            if (skipWhenNewValueIsNull || onlyIfNewNotNull) {
                whereParts.add("i." + childField + " IS NOT NULL");
            }
            if (!filterSql.whereSql.isEmpty()) {
                whereParts.add(filterSql.whereSql);
            }

            updateInserts.add(joinLines(
                    "        INSERT INTO " + outTable + " (target_table_id, monitored_field, create_time)",
                    "        SELECT",
                    "            i.id,",
                    "            " + monitoredExpr + ",",
                    "            GETDATE()",
                    baseJoin,
                    "        WHERE " + String.join("\n          AND ", whereParts) + ";",
                    ""
            ));

            String baseJoinInsert = joinLinesNonEmpty(
                    "        FROM inserted i",
                    filterSql.joinMainSqlInsert,
                    filterSql.joinEnumSqlInsert
            );
            List<String> wherePartsInsert = new ArrayList<>();
            if (skipWhenNewValueIsNull || onlyIfNewNotNull) {
                wherePartsInsert.add("i." + childField + " IS NOT NULL");
            }
            if (!filterSql.whereSqlInsert.isEmpty()) {
                wherePartsInsert.add(filterSql.whereSqlInsert);
            }
            String whereInsert = wherePartsInsert.isEmpty() ? "" : "        WHERE " + String.join("\n          AND ", wherePartsInsert);

            insertInserts.add(joinLines(
                    "        INSERT INTO " + outTable + " (target_table_id, monitored_field, create_time)",
                    "        SELECT",
                    "            i.id,",
                    "            " + monitoredExpr + ",",
                    "            GETDATE()",
                    baseJoinInsert,
                    whereInsert + ";",
                    ""
            ));
        }

        return joinLines(
                "-- 生成自 form_meta_config.json：rowChange",
                "IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = '" + triggerName + "')",
                "DROP TRIGGER " + triggerName + ";",
                "GO",
                "",
                "CREATE TRIGGER " + triggerName,
                "ON " + sourceTable,
                "AFTER INSERT, UPDATE",
                "AS",
                "BEGIN",
                "    SET NOCOUNT ON;",
                "    SET XACT_ABORT ON;",
                "",
                "    -- 更新：inserted + deleted",
                "    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)",
                "    BEGIN",
                String.join("\n", updateInserts),
                "    END",
                "",
                "    -- 新增：只有 inserted",
                "    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)",
                "    BEGIN",
                String.join("\n", insertInserts),
                "    END",
                "END;",
                "GO",
                ""
        );
    }

    private static class BusinessFilterSql {
        String joinMainSql = "";
        String joinEnumSql = "";
        String whereSql = "";

        String joinMainSqlInsert = "";
        String joinEnumSqlInsert = "";
        String whereSqlInsert = "";
    }

    private BusinessFilterSql buildBusinessFilterSql(JsonNode eventNode, List<String> warnings) {
        BusinessFilterSql r = new BusinessFilterSql();
        JsonNode filters = eventNode.path("businessFilters");
        if (filters == null || !filters.isArray() || filters.size() == 0) {
            return r;
        }

        // mainJoin：子表->主表关联（用于取主表枚举字段）
        String mainAlias = "m";
        JsonNode mainJoinNode = eventNode.path("source").path("mainJoin");
        if (mainJoinNode == null || !mainJoinNode.isObject()) {
            // 无 mainJoin 时，默认认为过滤字段在 inserted 行（仍可用 SHOWVALUE 过滤，但这里保守跳过）
            warnings.add("businessFilters 存在但缺少 source.mainJoin（暂不生成枚举过滤 join，需人工确认）: " + eventNode.path("triggerName").asText(""));
            return r;
        }
        String mainTable = mainJoinNode.path("mainTable").asText("");
        String childMainIdField = mainJoinNode.path("childMainIdField").asText("");
        String mainIdField = mainJoinNode.path("mainIdField").asText("");
        if (mainTable.isEmpty() || childMainIdField.isEmpty() || mainIdField.isEmpty()) {
            warnings.add("businessFilters 存在但 source.mainJoin 不完整（mainTable/childMainIdField/mainIdField）: "
                    + eventNode.path("triggerName").asText(""));
            return r;
        }
        r.joinMainSql = "        JOIN " + mainTable + " " + mainAlias + " ON i." + childMainIdField + " = " + mainAlias + "." + mainIdField;
        r.joinMainSqlInsert = "        JOIN " + mainTable + " " + mainAlias + " ON i." + childMainIdField + " = " + mainAlias + "." + mainIdField;

        Map<String, String> enumAliases = new LinkedHashMap<>();
        List<String> whereParts = new ArrayList<>();
        int i = 0;
        for (JsonNode f : filters) {
            String mainEnumField = f.path("mainEnumField").asText("");
            String enumShowValue = f.path("enumShowValue").asText("");
            String matchType = f.path("matchType").asText("eq");
            if (mainEnumField.isEmpty() || enumShowValue.isEmpty()) {
                continue;
            }
            i++;
            String alias = "ei" + i;
            enumAliases.put(mainEnumField, alias);
            String cond;
            if ("eq".equalsIgnoreCase(matchType)) {
                cond = alias + ".SHOWVALUE = N'" + escapeSqlN(enumShowValue) + "'";
            } else if ("neq_or_null".equalsIgnoreCase(matchType)) {
                cond = "(" + alias + ".SHOWVALUE <> N'" + escapeSqlN(enumShowValue) + "' OR " + alias + ".SHOWVALUE IS NULL)";
            } else {
                warnings.add("未知 matchType（按 eq 处理）: " + matchType);
                cond = alias + ".SHOWVALUE = N'" + escapeSqlN(enumShowValue) + "'";
            }
            whereParts.add(cond);
        }

        List<String> joins = new ArrayList<>();
        for (Map.Entry<String, String> e : enumAliases.entrySet()) {
            joins.add("        LEFT JOIN CTP_ENUM_ITEM " + e.getValue() + " ON " + mainAlias + "." + e.getKey() + " = " + e.getValue() + ".ID");
        }
        r.joinEnumSql = String.join("\n", joins);
        r.joinEnumSqlInsert = r.joinEnumSql;
        r.whereSql = whereParts.isEmpty() ? "" : String.join(" AND ", whereParts);
        r.whereSqlInsert = r.whereSql;
        return r;
    }

    private String mapSelectExpr(JsonNode mappingValue, String insertedAlias, List<String> warnings) {
        if (mappingValue == null || mappingValue.isNull()) {
            return "NULL";
        }
        if (mappingValue.isNumber()) {
            return mappingValue.asText();
        }
        if (mappingValue.isTextual()) {
            String t = mappingValue.asText();
            if ("child.id".equalsIgnoreCase(t) || "inserted.id".equalsIgnoreCase(t) || "id".equalsIgnoreCase(t)) {
                return insertedAlias + ".id";
            }
            if (t.startsWith("field")) {
                // bill_no 这种 NOT NULL 字段常见；这里不做强制 ISNULL 包装，让 whereNotNull 决定是否过滤
                return insertedAlias + "." + t;
            }
            warnings.add("mapping 值为文本但不识别（按字符串常量处理）: " + t);
            return "N'" + escapeSqlN(t) + "'";
        }
        warnings.add("mapping 值类型不支持（按 NULL）: " + mappingValue.getNodeType());
        return "NULL";
    }

    private String buildMonitoredFieldExpr(String format, String fieldCode, String valueExpr, List<String> warnings) {
        // 支持常见格式："{fieldCode}={value}"、"fieldCode={value}" 以及包含占位符的扩展写法
        String fc = escapeSqlN(fieldCode);
        if ("{fieldCode}={value}".equals(format)) {
            return "CONCAT(N'" + fc + "=', CAST(" + valueExpr + " AS NVARCHAR(50)))";
        }
        if ("fieldCode={value}".equals(format)) {
            return "CONCAT(N'" + fc + "=', CAST(" + valueExpr + " AS NVARCHAR(50)))";
        }

        // 兼容：替换字段占位符（同时兼容带/不带大括号）
        String normalized = format
                .replace("{fieldCode}", fieldCode)
                .replace("fieldCode", fieldCode)
                .replace("{value}", "{VALUE}");
        if (normalized.contains("{VALUE}")) {
            String prefix = normalized.replace("{VALUE}", "");
            return "CONCAT(N'" + escapeSqlN(prefix) + "', CAST(" + valueExpr + " AS NVARCHAR(50)))";
        }
        warnings.add("monitoredFieldFormat 不支持（回退 {fieldCode}={value}）: " + format);
        return "CONCAT(N'" + fc + "=', CAST(" + valueExpr + " AS NVARCHAR(50)))";
    }

    private String valueChangedExpr(String newExpr, String oldExpr) {
        return newExpr + " <> " + oldExpr
                + " OR (" + newExpr + " IS NOT NULL AND " + oldExpr + " IS NULL)"
                + " OR (" + newExpr + " IS NULL AND " + oldExpr + " IS NOT NULL)";
    }

    private static String escapeSqlN(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("'", "''");
    }

    private static String joinLines(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (l == null) {
                continue;
            }
            sb.append(l);
            if (!l.endsWith("\n")) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String joinLinesNonEmpty(String... lines) {
        List<String> ok = new ArrayList<>();
        for (String l : lines) {
            if (l != null && !l.trim().isEmpty()) {
                ok.add(l);
            }
        }
        return String.join("\n", ok);
    }
}

