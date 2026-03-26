package com.SpringbootTZ.FACT.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 启动时校验 form_meta_config.json 是否完整。
 * 目的：在没有数据库的开发环境也能明确提示“缺哪些 enumIds/仍依赖 SHOWVALUE”。
 */
@Component
public class FormMetaConfigValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FormMetaConfigValidator.class);

    private static final String CLASSPATH_META = "config/form_meta_config.json";
    private static final String FILE_META = "form_meta_config.json";

    private final ObjectMapper objectMapper;

    @Value("${app.config.external-dir:}")
    private String externalDirProp;

    public FormMetaConfigValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            JsonNode root = loadMetaConfig();
            if (root == null || root.isMissingNode()) {
                log.warn("未找到 form_meta_config.json（external 优先，其次 classpath:{}），已跳过元数据校验", CLASSPATH_META);
                return;
            }

            JsonNode forms = root.path("forms");
            if (!forms.isObject()) {
                log.warn("form_meta_config.json 缺少 forms 对象，已跳过元数据校验");
                return;
            }

            List<String> warnings = new ArrayList<>();

            Iterator<Map.Entry<String, JsonNode>> it = forms.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String formKey = e.getKey();
                JsonNode form = e.getValue();

                // 1) enumIds 为空或存在 null：提示需要在有库环境补齐（推荐企业级做法）
                JsonNode enumIds = form.path("enumIds");
                if (enumIds.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> eit = enumIds.fields();
                    while (eit.hasNext()) {
                        Map.Entry<String, JsonNode> ee = eit.next();
                        if (ee.getValue() == null || ee.getValue().isNull()) {
                            warnings.add("forms." + formKey + ".enumIds." + ee.getKey() + " 仍为 null（建议填入 CTP_ENUM_ITEM.ID，避免依赖 SHOWVALUE）");
                        }
                    }
                }

                // 2) events.businessFilters 仍在用 enumShowValue：提示目前依赖文案，存在风险
                JsonNode events = form.path("events");
                if (events.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> evIt = events.fields();
                    while (evIt.hasNext()) {
                        Map.Entry<String, JsonNode> ev = evIt.next();
                        String eventKey = ev.getKey();
                        JsonNode event = ev.getValue();

                        JsonNode bfs = event.path("businessFilters");
                        if (bfs.isArray()) {
                            for (int i = 0; i < bfs.size(); i++) {
                                JsonNode bf = bfs.get(i);
                                if (bf != null && bf.hasNonNull("enumShowValue")) {
                                    warnings.add("forms." + formKey + ".events." + eventKey + ".businessFilters[" + i + "] 仍使用 enumShowValue=" +
                                            bf.path("enumShowValue").asText("") + "（企业级建议：改为 enumKey + enumIds）");
                                }
                            }
                        }
                    }
                }
            }

            if (warnings.isEmpty()) {
                log.info("form_meta_config.json 校验通过：未发现 enumIds 缺失或 enumShowValue 依赖");
            } else {
                log.warn("form_meta_config.json 校验发现 {} 个风险项（不影响启动，但建议补齐/整改）", warnings.size());
                for (String w : warnings) {
                    log.warn("META-CHECK: {}", w);
                }
            }
        } catch (Exception ex) {
            // 不要阻断启动，只提示
            log.warn("form_meta_config.json 校验失败（已忽略，不影响启动）：{}", ex.getMessage());
        }
    }

    private JsonNode loadMetaConfig() {
        // external 优先
        try {
            File ext = externalMetaFile();
            if (ext.exists() && ext.length() > 0) {
                return objectMapper.readTree(ext);
            }
        } catch (Exception e) {
            log.warn("读取外置 form_meta_config.json 失败，将尝试 classpath：{}", e.getMessage());
        }

        // classpath fallback
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CLASSPATH_META)) {
            if (in == null) {
                return null;
            }
            return objectMapper.readTree(in);
        } catch (Exception e) {
            log.warn("读取 classpath form_meta_config.json 失败：{}", e.getMessage());
            return null;
        }
    }

    private File externalMetaFile() {
        String dir = externalDirProp;
        if (dir == null || dir.trim().isEmpty()) {
            dir = Paths.get(System.getProperty("user.dir"), "fact-admin-config").toString();
        }
        Path base = Paths.get(dir.trim()).toAbsolutePath().normalize();
        return base.resolve(FILE_META).toFile();
    }
}

