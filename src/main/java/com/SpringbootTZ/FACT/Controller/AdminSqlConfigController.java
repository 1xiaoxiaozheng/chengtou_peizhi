package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Entity.SysSqlConfig;
import com.SpringbootTZ.FACT.Service.ConfigFileRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sql-config")
public class AdminSqlConfigController {

    private final ConfigFileRepository configFileRepository;

    public AdminSqlConfigController(ConfigFileRepository configFileRepository) {
        this.configFileRepository = configFileRepository;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", configFileRepository.listAllSqlConfigs());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @GetMapping("/{sqlKey}")
    public Map<String, Object> getOne(@PathVariable String sqlKey) {
        Map<String, Object> resp = new HashMap<>();
        try {
            List<SysSqlConfig> all = configFileRepository.listAllSqlConfigs();
            SysSqlConfig found = null;
            for (SysSqlConfig c : all) {
                if (c != null && sqlKey.equals(c.getSqlKey())) {
                    found = c;
                    break;
                }
            }
            resp.put("success", found != null);
            resp.put("message", found != null ? "ok" : "未找到");
            resp.put("data", found);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @DeleteMapping("/{sqlKey}")
    public Map<String, Object> delete(@PathVariable String sqlKey) {
        Map<String, Object> resp = new HashMap<>();
        try {
            configFileRepository.deleteSqlConfig(sqlKey);
            resp.put("success", true);
            resp.put("message", "删除成功");
            resp.put("data", null);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody SysSqlConfig body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            configFileRepository.saveOrUpdateSqlConfig(body);
            resp.put("success", true);
            resp.put("message", "保存成功");
            resp.put("data", null);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}
