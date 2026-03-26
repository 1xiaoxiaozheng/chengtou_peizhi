package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Entity.OaDictConfig;
import com.SpringbootTZ.FACT.Mapper.OaDictConfigMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * oa_dict_config 运维配置接口（演示包）。
 * 合并进主工程前建议加鉴权（例如只在内网开放或与现有 /api/admin 一致的安全策略）。
 */
@RestController
@RequestMapping("/api/admin/oa-dict-config")
public class OaDictConfigAdminController {

    private final OaDictConfigMapper oaDictConfigMapper;

    public OaDictConfigAdminController(OaDictConfigMapper oaDictConfigMapper) {
        this.oaDictConfigMapper = oaDictConfigMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", oaDictConfigMapper.selectAll());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    /**
     * 批量保存：有 id 且大于 0 则 UPDATE，否则 INSERT（适合页面上「新增行」与「改已有行」混排）。
     */
    @PostMapping("/save-batch")
    public Map<String, Object> saveBatch(@RequestBody List<OaDictConfig> rows) {
        Map<String, Object> resp = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "列表为空");
            resp.put("data", null);
            return resp;
        }
        try {
            for (OaDictConfig row : rows) {
                if (row.getConfigKey() == null || row.getConfigKey().trim().isEmpty()) {
                    continue;
                }
                row.setConfigKey(row.getConfigKey().trim());
                if (row.getConfigValue() != null) {
                    row.setConfigValue(row.getConfigValue().trim());
                }
                if (row.getConfigName() != null) {
                    row.setConfigName(row.getConfigName().trim());
                }
                if (row.getId() != null && row.getId() > 0) {
                    oaDictConfigMapper.updateById(row);
                } else {
                    row.setId(null);
                    oaDictConfigMapper.insert(row);
                }
            }
            resp.put("success", true);
            resp.put("message", "保存成功");
            resp.put("data", null);
        } catch (DataIntegrityViolationException e) {
            resp.put("success", false);
            resp.put("message", "违反唯一约束（如 config_key 重复），请检查数据");
            resp.put("data", null);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    /**
     * 按主键删除一行（运维慎用）。
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody Map<String, Integer> body) {
        Map<String, Object> resp = new HashMap<>();
        Integer id = body != null ? body.get("id") : null;
        if (id == null || id <= 0) {
            resp.put("success", false);
            resp.put("message", "缺少有效 id");
            resp.put("data", null);
            return resp;
        }
        try {
            int n = oaDictConfigMapper.deleteById(id);
            resp.put("success", true);
            resp.put("message", n > 0 ? "已删除" : "未找到记录");
            resp.put("data", n);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}
