package com.SpringbootTZ.FACT.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringbootTZ.FACT.Entity.SysDict;
import com.SpringbootTZ.FACT.Entity.SysSqlConfig;
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
import java.util.stream.Collectors;

/**
 * 字典与 SQL 查询配置：优先从外置目录读写 JSON（便于可视化后台保存），
 * 外置文件不存在时回退 classpath:config/*.json。
 */
@Service
public class ConfigFileRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileRepository.class);

    private static final String CLASSPATH_DICT = "config/sys_dict.json";
    private static final String CLASSPATH_SQL = "config/sys_sql_config.json";

    private static final String FILE_DICT = "sys_dict.json";
    private static final String FILE_SQL = "sys_sql_config.json";

    private final ObjectMapper objectMapper;

    @Value("${app.config.external-dir:}")
    private String externalDirProp;

    private final Object loadLock = new Object();

    private volatile boolean inMemoryLoaded = false;
    private final Map<String, List<SysDict>> dictByType = new HashMap<>();
    private final Map<String, SysSqlConfig> sqlConfigByKey = new HashMap<>();

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

    private File externalSqlFile() {
        return externalBaseDir().resolve(FILE_SQL).toFile();
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
            log.info("配置已加载入内存：externalDir={}, dictTypes={}, sqlKeys={}",
                    externalBaseDir(), dictByType.size(), sqlConfigByKey.size());
        }
    }

    /**
     * 从磁盘重新加载（外置优先，否则 classpath），并刷新内存缓存。
     */
    public void reloadFromDiskIntoMemory() {
        synchronized (loadLock) {
            dictByType.clear();
            sqlConfigByKey.clear();

            List<SysDict> dictList = readDictListFromDisk();
            if (dictList != null) {
                for (SysDict d : dictList) {
                    if (d == null || d.getDictType() == null) {
                        continue;
                    }
                    dictByType.computeIfAbsent(d.getDictType().trim(), k -> new ArrayList<>()).add(d);
                }
            }

            List<SysSqlConfig> sqlList = readSqlConfigListFromDisk();
            if (sqlList != null) {
                for (SysSqlConfig c : sqlList) {
                    if (c == null || c.getSqlKey() == null) {
                        continue;
                    }
                    sqlConfigByKey.put(c.getSqlKey().trim(), c);
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

    private List<SysSqlConfig> readSqlConfigListFromDisk() {
        try {
            File ext = externalSqlFile();
            if (ext.exists() && ext.length() > 0) {
                return objectMapper.readValue(ext, new TypeReference<List<SysSqlConfig>>() {
                });
            }
        } catch (Exception e) {
            log.warn("读取外置 SQL 配置失败，将尝试 classpath：{}", e.getMessage());
        }

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CLASSPATH_SQL)) {
            if (in == null) {
                log.warn("classpath 未找到：{}", CLASSPATH_SQL);
                return new ArrayList<>();
            }
            List<SysSqlConfig> list = objectMapper.readValue(in, new TypeReference<List<SysSqlConfig>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            throw new IllegalStateException("读取 SQL 配置失败", e);
        }
    }

    private void ensureExternalDirExists() throws Exception {
        Path dir = externalBaseDir();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private void writeJsonAtomic(File target, Object value) throws Exception {
        ensureExternalDirExists();
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

    public List<SysSqlConfig> listAllSqlConfigs() {
        ensureMemoryLoaded();
        return sqlConfigByKey.values().stream()
                .sorted(Comparator.comparing(c -> c.getSqlKey() == null ? "" : c.getSqlKey()))
                .collect(Collectors.toList());
    }

    public void saveAllDicts(List<SysDict> rows) throws Exception {
        if (rows == null) {
            rows = new ArrayList<>();
        }
        writeJsonAtomic(externalDictFile(), rows);
        synchronized (loadLock) {
            reloadFromDiskIntoMemory();
        }
    }

    public void saveOrUpdateSqlConfig(SysSqlConfig one) throws Exception {
        if (one == null || one.getSqlKey() == null || one.getSqlKey().trim().isEmpty()) {
            throw new IllegalArgumentException("sqlKey 不能为空");
        }
        ensureMemoryLoaded();
        List<SysSqlConfig> all = new ArrayList<>(listAllSqlConfigs());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (one.getSqlKey().equals(all.get(i).getSqlKey())) {
                all.set(i, one);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(one);
        }
        writeJsonAtomic(externalSqlFile(), all);
        synchronized (loadLock) {
            reloadFromDiskIntoMemory();
        }
    }

    public void saveAllSqlConfigs(List<SysSqlConfig> rows) throws Exception {
        if (rows == null) {
            rows = new ArrayList<>();
        }
        writeJsonAtomic(externalSqlFile(), rows);
        synchronized (loadLock) {
            reloadFromDiskIntoMemory();
        }
    }

    public void deleteSqlConfig(String sqlKey) throws Exception {
        if (sqlKey == null || sqlKey.trim().isEmpty()) {
            throw new IllegalArgumentException("sqlKey 不能为空");
        }
        String key = sqlKey.trim();
        ensureMemoryLoaded();
        List<SysSqlConfig> all = new ArrayList<>(listAllSqlConfigs());
        boolean removed = all.removeIf(c -> c != null && key.equals(c.getSqlKey()));
        if (!removed) {
            throw new IllegalArgumentException("未找到配置: " + key);
        }
        writeJsonAtomic(externalSqlFile(), all);
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

    public SysSqlConfig getSqlConfigByKey(String sqlKey) {
        ensureMemoryLoaded();
        if (sqlKey == null || sqlKey.trim().isEmpty()) {
            return null;
        }
        return sqlConfigByKey.get(sqlKey.trim());
    }
}
