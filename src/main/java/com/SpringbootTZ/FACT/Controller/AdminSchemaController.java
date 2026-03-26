package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Mapper.AdminSchemaMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从 SQL Server INFORMATION_SCHEMA 拉取表/字段，供可视化配置选表、选列。
 */
@RestController
@RequestMapping("/api/admin/schema")
public class AdminSchemaController {

    private static final Pattern SAFE_TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final AdminSchemaMapper adminSchemaMapper;

    public AdminSchemaController(AdminSchemaMapper adminSchemaMapper) {
        this.adminSchemaMapper = adminSchemaMapper;
    }

    @GetMapping("/tables")
    public Map<String, Object> tables() {
        Map<String, Object> resp = new HashMap<>();
        try {
            List<String> rows = adminSchemaMapper.listTables();
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", rows);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @GetMapping("/columns")
    public Map<String, Object> columns(@RequestParam("table") String table) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (table == null || !SAFE_TABLE.matcher(table.trim()).matches()) {
                throw new IllegalArgumentException("非法表名: " + table);
            }
            List<String> rows = adminSchemaMapper.listColumns(table.trim());
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", rows);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}
