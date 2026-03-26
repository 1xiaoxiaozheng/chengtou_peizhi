package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Entity.SysDict;
import com.SpringbootTZ.FACT.Service.ConfigFileRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/dict")
public class AdminDictController {

    private final ConfigFileRepository configFileRepository;

    public AdminDictController(ConfigFileRepository configFileRepository) {
        this.configFileRepository = configFileRepository;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", configFileRepository.listAllDicts());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    /**
     * 当前已存在的字典类型（用于 SQL 条件里绑定 dict 下拉）
     */
    @GetMapping("/types")
    public Map<String, Object> types() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Set<String> types = new LinkedHashSet<>();
            for (SysDict d : configFileRepository.listAllDicts()) {
                if (d != null && d.getDictType() != null && !d.getDictType().trim().isEmpty()) {
                    types.add(d.getDictType().trim());
                }
            }
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", types);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody List<SysDict> rows) {
        Map<String, Object> resp = new HashMap<>();
        try {
            configFileRepository.saveAllDicts(rows);
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
