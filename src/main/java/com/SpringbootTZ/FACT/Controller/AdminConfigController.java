package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Entity.SysDict;
import com.SpringbootTZ.FACT.Entity.SysSqlConfig;
import com.SpringbootTZ.FACT.Service.ConfigFileRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final ConfigFileRepository configFileRepository;

    public AdminConfigController(ConfigFileRepository configFileRepository) {
        this.configFileRepository = configFileRepository;
    }

    @GetMapping("/export")
    public Map<String, Object> exportAll() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sysDict", configFileRepository.listAllDicts());
            data.put("sysSqlConfig", configFileRepository.listAllSqlConfigs());

            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", data);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    public static class ImportPayload {
        private List<SysDict> sysDict;
        private List<SysSqlConfig> sysSqlConfig;

        public List<SysDict> getSysDict() {
            return sysDict;
        }

        public void setSysDict(List<SysDict> sysDict) {
            this.sysDict = sysDict;
        }

        public List<SysSqlConfig> getSysSqlConfig() {
            return sysSqlConfig;
        }

        public void setSysSqlConfig(List<SysSqlConfig> sysSqlConfig) {
            this.sysSqlConfig = sysSqlConfig;
        }
    }

    @PostMapping("/import")
    public Map<String, Object> importAll(@RequestBody ImportPayload payload) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (payload == null) {
                throw new IllegalArgumentException("import payload为空");
            }
            // 覆盖写入：便于恢复/环境迁移
            configFileRepository.saveAllDicts(payload.getSysDict());
            configFileRepository.saveAllSqlConfigs(payload.getSysSqlConfig());

            resp.put("success", true);
            resp.put("message", "导入成功");
            resp.put("data", null);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}

