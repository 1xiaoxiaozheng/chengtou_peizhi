package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Entity.SysDict;
import com.SpringbootTZ.FACT.Service.DictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/{dictType}")
    public Map<String, Object> getDict(@PathVariable String dictType) {
        Map<String, Object> resp = new HashMap<>();
        try {
            List<SysDict> list = dictService.getDict(dictType);
            List<Map<String, Object>> data = new ArrayList<>();
            for (SysDict d : list) {
                Map<String, Object> item = new HashMap<>();
                item.put("dictKey", d.getDictKey());
                item.put("dictValue", d.getDictValue());
                data.add(item);
            }
            resp.put("success", true);
            resp.put("message", "获取成功");
            resp.put("data", data);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", new ArrayList<>());
        }
        return resp;
    }
}

