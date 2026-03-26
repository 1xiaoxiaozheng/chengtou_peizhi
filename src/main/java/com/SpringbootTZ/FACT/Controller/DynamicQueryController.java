package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Service.DynamicQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class DynamicQueryController {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryController.class);

    private final DynamicQueryService dynamicQueryService;

    public DynamicQueryController(DynamicQueryService dynamicQueryService) {
        this.dynamicQueryService = dynamicQueryService;
    }

    @PostMapping("/{sqlKey}")
    public Map<String, Object> query(@PathVariable String sqlKey, @RequestBody Map<String, Object> params) {
        Map<String, Object> resp = new HashMap<>();
        try {
            List<Map<String, Object>> data = dynamicQueryService.dynamicQuery(sqlKey, params);
            resp.put("success", true);
            resp.put("message", "查询成功");
            resp.put("data", data);
        } catch (Exception e) {
            log.error("动态查询失败, sqlKey={}", sqlKey, e);
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}

