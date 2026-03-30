package com.SpringbootTZ.FACT.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringbootTZ.FACT.Entity.SysDict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典配置仓储：优先从外置目录读写 JSON，
 * 外置文件不存在时回退 classpath:config/sys_dict.json。
 */
@Service
public class ConfigFileRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileRepository.class);

    private static final String CLASSPATH_DICT = "config/sys_dict.json";

    private static final String FILE_DICT = "sys_dict.json";

    private final ObjectMapper objectMapper;

    @Value("${app.config.external-dir:}")
    private String externalDirProp;

    @Value("${app.config.sync-to-project-config:true}")
    private boolean syncToProjectConfig;

    private final Object loadLock = new Object();

    private volatile boolean inMemoryLoaded = false;
    private final Map<String, List<SysDict>> dictByType = new HashMap<>();

    public ConfigFileRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private Path externalBaseDir() {
        String dir = externalDirProp;
        if (dir == null || dir.trim().isEmpty()) {
            dir = Paths.get(System.getProperty("user.dir"), "fact-admin-config").toString();
        }
        return Paths.get(dir.trim()).toAbsolutePath().normalize();
    }

    private File externalDictFile() {
        return externalBaseDir().resolve(FILE_DICT).toFile();
    }

    private void ensureMemoryLoaded() {
        if (inMemoryLoaded) {
            return;
        }
        synchronized (loadLock) {
            if (inMemoryLoaded) {
                return;
            }
            reloadFromDiskIntoMemory();
            inMemoryLoaded = true;
            log.info("配置已加载入内存：externalDir={}, dictTypes={}",
                    externalBaseDir(), dictByType.size());
        }
    }

    /**
     * 从磁盘重新加载（外置优先，否则 classpath），并刷新内存缓存。
     */
    public void reloadFromDiskIntoMemory() {
        synchronized (loadLock) {
            dictByType.clear();

            List<SysDict> dictList = readDictListFromDisk();
            if (dictList != null) {
                for (SysDict d : dictList) {
                    if (d == null || d.getDictType() == null) {
                        continue;
                    }
                    dictByType.computeIfAbsent(d.getDictType().trim(), k -> new ArrayList<>()).add(d);
                }
            }

            inMemoryLoaded = true;
        }
    }

    private List<SysDict> readDictListFromDisk() {
        try {
            File ext = externalDictFile();
            if (ext.exists() && ext.length() > 0) {
                return objectMapper.readValue(ext, new TypeReference<List<SysDict>>() {
                });
            }
        } catch (Exception e) {
            log.warn("读取外置字典失败，将尝试 classpath：{}", e.getMessage());
        }

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CLASSPATH_DICT)) {
            if (in == null) {
                log.warn("classpath 未找到：{}", CLASSPATH_DICT);
                return new ArrayList<>();
            }
            List<SysDict> list = objectMapper.readValue(in, new TypeReference<List<SysDict>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            throw new IllegalStateException("读取字典配置失败", e);
        }
    }

    private void ensureDirExists(File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
    }

    private void writeJsonAtomic(File target, Object value) throws Exception {
        ensureDirExists(target);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp, value);
        if (target.exists() && !target.delete()) {
            // Windows 下偶发占用，直接覆盖写
            Files.copy(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp.toPath());
        } else {
            if (!tmp.renameTo(target)) {
                Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private File projectConfigFile(String fileName) {
        Path p = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "config", fileName)
                .toAbsolutePath()
                .normalize();
        return p.toFile();
    }

    private void syncProjectConfigIfEnabled(String fileName, Object value) {
        if (!syncToProjectConfig) {
            return;
        }
        try {
            File target = projectConfigFile(fileName);
            // 仅在源码目录存在时同步；打包运行环境通常没有 src 目录
            File parent = target.getParentFile();
            if (parent == null || !parent.exists()) {
                return;
            }
            writeJsonAtomic(target, value);
            log.info("已自动同步项目配置文件: {}", target.getAbsolutePath());
        } catch (Exception e) {
            log.warn("自动同步项目配置失败（不影响主流程）: {}", e.getMessage());
        }
    }

    public List<SysDict> listAllDicts() {
        ensureMemoryLoaded();
        List<SysDict> flat = new ArrayList<>();
        for (List<SysDict> part : dictByType.values()) {
            flat.addAll(part);
        }
        flat.sort(Comparator
                .comparing((SysDict d) -> d.getDictType() == null ? "" : d.getDictType())
                .thenComparingInt(d -> d.getSort() == null ? 0 : d.getSort()));
        return flat;
    }

    public void saveAllDicts(List<SysDict> rows) throws Exception {
        if (rows == null) {
            rows = new ArrayList<>();
        }
        writeJsonAtomic(externalDictFile(), rows);
        syncProjectConfigIfEnabled(FILE_DICT, rows);
        synchronized (loadLock) {
            reloadFromDiskIntoMemory();
        }
    }

    public List<SysDict> getDictByType(String dictType) {
        ensureMemoryLoaded();
        if (dictType == null || dictType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<SysDict> list = dictByType.get(dictType.trim());
        return list == null ? Collections.emptyList() : list;
    }

}
