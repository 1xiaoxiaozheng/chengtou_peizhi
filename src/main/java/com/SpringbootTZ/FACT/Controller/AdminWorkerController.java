package com.SpringbootTZ.FACT.Controller;

import com.SpringbootTZ.FACT.Service.MiddleTableWorkerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/worker")
public class AdminWorkerController {

    private final MiddleTableWorkerService middleTableWorkerService;

    public AdminWorkerController(MiddleTableWorkerService middleTableWorkerService) {
        this.middleTableWorkerService = middleTableWorkerService;
    }

    /**
     * 手动触发一次 Worker（不依赖定时开关）
     */
    @PostMapping("/run-once")
    public Map<String, Object> runOnce() {
        Map<String, Object> resp = new HashMap<>();
        try {
            middleTableWorkerService.runOnce();
            resp.put("success", true);
            resp.put("message", "worker run once done");
            resp.put("data", null);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", middleTableWorkerService.getStatusSnapshot());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    @GetMapping("/failed-samples")
    public Map<String, Object> failedSamples(@RequestParam("table") String table,
                                             @RequestParam(value = "limit", required = false) Integer limit) {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", middleTableWorkerService.listFailedSamples(table, limit));
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }

    public static class ResetFailedPayload {
        private String table;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

    @PostMapping("/reset-failed")
    public Map<String, Object> resetFailed(@RequestBody ResetFailedPayload payload) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (payload == null || payload.getTable() == null || payload.getTable().trim().isEmpty()) {
                throw new IllegalArgumentException("table 不能为空");
            }
            int affected = middleTableWorkerService.resetFailedToPending(payload.getTable().trim());
            Map<String, Object> data = new HashMap<>();
            data.put("table", payload.getTable().trim());
            data.put("affected", affected);
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
}

