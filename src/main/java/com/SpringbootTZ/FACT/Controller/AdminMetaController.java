package com.SpringbootTZ.FACT.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringbootTZ.FACT.Service.FormMetaSqlGenerator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/meta")
public class AdminMetaController {

    private final FormMetaSqlGenerator formMetaSqlGenerator;
    private final ObjectMapper objectMapper;

    public AdminMetaController(FormMetaSqlGenerator formMetaSqlGenerator, ObjectMapper objectMapper) {
        this.formMetaSqlGenerator = formMetaSqlGenerator;
        this.objectMapper = objectMapper;
    }

    public static class GeneratePayload {
        private String outDir;

        public String getOutDir() {
            return outDir;
        }

        public void setOutDir(String outDir) {
            this.outDir = outDir;
        }
    }

    /**
     * 读取 classpath:config/form_meta_config.json，生成 SQL 文件到目录（只生成不执行）。
     * 可选传入 outDir，未传则落到 ${app.meta.generate.out-dir} 或 user.dir/fact-generated-sql。
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody(required = false) GeneratePayload payload) {
        Map<String, Object> resp = new HashMap<>();
        try {
            FormMetaSqlGenerator.GenerateResult r;
            if (payload != null && payload.getOutDir() != null && !payload.getOutDir().trim().isEmpty()) {
                r = formMetaSqlGenerator.generateToDir(payload.getOutDir().trim());
            } else {
                r = formMetaSqlGenerator.generateToDefaultDir();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("outDir", r.getOutDir().toString());
            data.put("files", r.getFiles());
            data.put("warnings", r.getWarnings());

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

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> resp = new HashMap<>();
        try {
            ClassPathResource res = new ClassPathResource("config/form_meta_config.json");
            if (!res.exists()) {
                throw new IllegalStateException("classpath 未找到 config/form_meta_config.json");
            }
            JsonNode json = objectMapper.readTree(res.getInputStream());
            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", json);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
        }
        return resp;
    }
}

