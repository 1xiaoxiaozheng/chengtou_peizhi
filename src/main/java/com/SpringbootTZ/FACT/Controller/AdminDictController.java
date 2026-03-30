package com.SpringbootTZ.FACT.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringbootTZ.FACT.Entity.SysDict;
import com.SpringbootTZ.FACT.Service.ConfigFileRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin/dict")
public class AdminDictController {

    private final ConfigFileRepository configFileRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern INLINE_FIELD_WITH_REMARK = Pattern.compile(
            "\\{\\n\\s*\"primaryKey\":\\s*\"([^\"]*)\",\\n\\s*\"code\":\\s*\"([^\"]*)\",\\n\\s*\"label\":\\s*\"([^\"]*)\",\\n\\s*\"remark\":\\s*\"([^\"]*)\"\\n\\s*\\}"
    );

    public AdminDictController(ConfigFileRepository configFileRepository, ObjectMapper objectMapper) {
        this.configFileRepository = configFileRepository;
        this.objectMapper = objectMapper;
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
            syncRemarksToFormMeta(rows);
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

    /**
     * 导出该 dictType 的数据字典为 xlsx，单 sheet。
     * Excel 填写列：
     * dictKey, dictValue, sort, remark
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam String dictType) throws Exception {
        String dt = dictType == null ? "" : dictType.trim();
        if (dt.isEmpty()) {
            return ResponseEntity.badRequest().body(new byte[0]);
        }

        List<SysDict> rows = configFileRepository.getDictByType(dt);
        List<SysDict> sorted = rows == null ? new ArrayList<>() : rows;
        // 保护：过滤掉任何 null 元素，避免 NPE
        sorted = sorted.stream()
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> {
                    int sa = a.getSort() == null ? 0 : a.getSort().intValue();
                    int sb = b.getSort() == null ? 0 : b.getSort().intValue();
                    if (sa != sb) return Integer.compare(sa, sb);
                    String ka = a.getDictKey() == null ? "" : a.getDictKey();
                    String kb = b.getDictKey() == null ? "" : b.getDictKey();
                    return ka.compareTo(kb);
                })
                .collect(Collectors.toList());

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("sys_dict");

        int r = 0;
        Row header = sheet.createRow(r++);
        header.createCell(0).setCellValue("dictKey");
        header.createCell(1).setCellValue("dictValue");
        header.createCell(2).setCellValue("sort");
        header.createCell(3).setCellValue("remark");

        for (SysDict d : sorted) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(d.getDictKey() == null ? "" : d.getDictKey());
            row.createCell(1).setCellValue(d.getDictValue() == null ? "" : d.getDictValue());
            row.createCell(2).setCellValue(d.getSort() == null ? 0 : d.getSort());
            row.createCell(3).setCellValue(d.getRemark() == null ? "" : d.getRemark());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        String filename = "sys_dict_" + dt + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }

    /**
     * 导入该 dictType 的数据字典 xlsx：仅按 dictKey 更新已存在的条目，不新增、不整类替换、不删除未出现在 Excel 中的行。
     * 要求：Excel 中每一行是一条字典项（从第2行开始，跳过表头）。
     */
    @PostMapping("/import")
    public Map<String, Object> importXlsx(@RequestParam String dictType,
                                           @RequestPart("file") MultipartFile file) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String dt = dictType == null ? "" : dictType.trim();
            if (dt.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "dictType 不能为空");
                resp.put("data", null);
                return resp;
            }
            if (file == null || file.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "上传文件为空");
                resp.put("data", null);
                return resp;
            }

            List<Map<String, Object>> errors = new ArrayList<>();
            List<SysDict> newRows = new ArrayList<>();
            Set<String> dictKeys = new java.util.HashSet<>();

            try (InputStream in = file.getInputStream()) {
                Workbook workbook = new XSSFWorkbook(in);
                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();

                int last = sheet.getLastRowNum();
                for (int i = 1; i <= last; i++) { // skip header row 0
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String dictKey = formatter.formatCellValue(getCell(row, 0)).trim();
                    String dictValue = formatter.formatCellValue(getCell(row, 1)).trim();
                    String sortStr = formatter.formatCellValue(getCell(row, 2)).trim();
                    String remark = formatter.formatCellValue(getCell(row, 3)).trim();

                    // 完全空行跳过
                    if (dictKey.isEmpty() && dictValue.isEmpty() && sortStr.isEmpty() && remark.isEmpty()) {
                        continue;
                    }

                    int rowNo = i + 1; // Excel 行号从1开始
                    if (dictKey.isEmpty()) {
                        errors.add(rowError(rowNo, "dictKey 不能为空"));
                        continue;
                    }
                    if (dictKeys.contains(dictKey)) {
                        errors.add(rowError(rowNo, "dictKey 重复： " + dictKey));
                        continue;
                    }
                    dictKeys.add(dictKey);

                    int sort = 0;
                    if (!sortStr.isEmpty()) {
                        try {
                            sort = Integer.parseInt(sortStr);
                        } catch (Exception e) {
                            errors.add(rowError(rowNo, "sort 不是合法数字： " + sortStr));
                            continue;
                        }
                    }

                    SysDict d = new SysDict();
                    d.setDictType(dt);
                    d.setDictKey(dictKey);
                    d.setDictValue(dictValue);
                    d.setSort(sort);
                    d.setRemark(remark);
                    newRows.add(d);
                }

                workbook.close();
            }

            if (!errors.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "导入失败：存在校验错误");
                resp.put("data", errors);
                return resp;
            }

            List<SysDict> all = configFileRepository.listAllDicts();
            if (all == null) {
                all = new ArrayList<>();
            } else {
                all = new ArrayList<>(all);
            }

            Map<String, SysDict> existingByKey = new HashMap<>();
            for (SysDict d : all) {
                if (d == null || d.getDictType() == null) {
                    continue;
                }
                if (!dt.equals(d.getDictType().trim())) {
                    continue;
                }
                String k = d.getDictKey() == null ? "" : d.getDictKey().trim();
                if (k.isEmpty()) {
                    continue;
                }
                existingByKey.putIfAbsent(k, d);
            }

            int updated = 0;
            int skipped = 0;
            for (SysDict fromExcel : newRows) {
                String key = fromExcel.getDictKey() == null ? "" : fromExcel.getDictKey().trim();
                SysDict target = existingByKey.get(key);
                if (target == null) {
                    skipped++;
                    continue;
                }
                target.setDictValue(fromExcel.getDictValue());
                target.setSort(fromExcel.getSort());
                target.setRemark(fromExcel.getRemark());
                updated++;
            }

            configFileRepository.saveAllDicts(all);
            syncRemarksToFormMeta(all);

            resp.put("success", true);
            resp.put("message", "导入成功：dictType=" + dt + "，有效更新 " + updated + " 条，跳过 " + skipped + " 条（dictKey 在系统中不存在）");
            Map<String, Object> data = new HashMap<>();
            data.put("updated", updated);
            data.put("skipped", skipped);
            resp.put("data", data);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
            return resp;
        }
    }

    /**
     * 预览导入：只解析/校验 Excel，不落盘。
     * 返回：
     * - success=false: message + data(errors)
     * - success=true: data={ updated, skipped, total, previewRows(List<SysDict>) }
     */
    @PostMapping("/import/preview")
    public Map<String, Object> previewImportXlsx(@RequestParam String dictType,
                                                 @RequestPart("file") MultipartFile file) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String dt = dictType == null ? "" : dictType.trim();
            if (dt.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "dictType 不能为空");
                resp.put("data", null);
                return resp;
            }
            if (file == null || file.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "上传文件为空");
                resp.put("data", null);
                return resp;
            }

            List<Map<String, Object>> errors = new ArrayList<>();
            List<SysDict> newRows = new ArrayList<>();
            Set<String> dictKeys = new HashSet<>();

            try (InputStream in = file.getInputStream()) {
                Workbook workbook = new XSSFWorkbook(in);
                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();

                int last = sheet.getLastRowNum();
                for (int i = 1; i <= last; i++) { // skip header row 0
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String dictKey = formatter.formatCellValue(getCell(row, 0)).trim();
                    String dictValue = formatter.formatCellValue(getCell(row, 1)).trim();
                    String sortStr = formatter.formatCellValue(getCell(row, 2)).trim();
                    String remark = formatter.formatCellValue(getCell(row, 3)).trim();

                    if (dictKey.isEmpty() && dictValue.isEmpty() && sortStr.isEmpty() && remark.isEmpty()) {
                        continue;
                    }

                    int rowNo = i + 1;
                    if (dictKey.isEmpty()) {
                        errors.add(rowError(rowNo, "dictKey 不能为空"));
                        continue;
                    }
                    if (dictKeys.contains(dictKey)) {
                        errors.add(rowError(rowNo, "dictKey 重复： " + dictKey));
                        continue;
                    }
                    dictKeys.add(dictKey);

                    int sort = 0;
                    if (!sortStr.isEmpty()) {
                        try {
                            sort = Integer.parseInt(sortStr);
                        } catch (Exception e) {
                            errors.add(rowError(rowNo, "sort 不是合法数字： " + sortStr));
                            continue;
                        }
                    }

                    SysDict d = new SysDict();
                    d.setDictType(dt);
                    d.setDictKey(dictKey);
                    d.setDictValue(dictValue);
                    d.setSort(sort);
                    d.setRemark(remark);
                    newRows.add(d);
                }

                workbook.close();
            }

            if (!errors.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "预览失败：存在校验错误");
                resp.put("data", errors);
                return resp;
            }

            List<SysDict> existing = configFileRepository.getDictByType(dt);
            Set<String> existingKeys = new HashSet<>();
            for (SysDict d : (existing == null ? java.util.Collections.<SysDict>emptyList() : existing)) {
                if (d != null && d.getDictKey() != null && !d.getDictKey().trim().isEmpty()) {
                    existingKeys.add(d.getDictKey().trim());
                }
            }

            int updated = 0;
            int skipped = 0;
            for (SysDict fromExcel : newRows) {
                String k = fromExcel.getDictKey() == null ? "" : fromExcel.getDictKey().trim();
                if (existingKeys.contains(k)) {
                    updated++;
                } else {
                    skipped++;
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("updated", updated);
            data.put("skipped", skipped);
            data.put("total", newRows.size());
            data.put("previewRows", newRows.stream().limit(50).collect(Collectors.toList()));

            resp.put("success", true);
            resp.put("message", "ok");
            resp.put("data", data);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("data", null);
            return resp;
        }
    }

    private static Cell getCell(Row row, int idx) {
        if (row == null) return null;
        Cell c = row.getCell(idx);
        return c == null ? row.createCell(idx) : c;
    }

    private static Map<String, Object> rowError(int rowNo, String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", rowNo);
        m.put("message", msg);
        return m;
    }

    /**
     * 将数据字典中的 remark 同步回项目内 form_meta_config.json。
     * 匹配规则：
     * - dictType = form.mainTable
     * - dictKey = form.mainTable（表级备注） 或 字段 code（字段备注）
     */
    private void syncRemarksToFormMeta(List<SysDict> rows) {
        if (rows == null) {
            return;
        }
        try {
            Map<String, String> remarkByTypeAndKey = new HashMap<>();
            for (SysDict d : rows) {
                if (d == null) continue;
                String dt = d.getDictType() == null ? "" : d.getDictType().trim();
                String dk = d.getDictKey() == null ? "" : d.getDictKey().trim();
                if (dt.isEmpty() || dk.isEmpty()) continue;
                String remark = d.getRemark() == null ? "" : d.getRemark().trim();
                remarkByTypeAndKey.put(dt + "||" + dk, remark);
            }

            Path metaPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "config", "form_meta_config.json")
                    .toAbsolutePath()
                    .normalize();
            if (!Files.exists(metaPath)) {
                return;
            }

            JsonNode root = objectMapper.readTree(Files.newInputStream(metaPath));
            JsonNode formsNode = root.path("forms");
            if (!formsNode.isObject()) {
                return;
            }

            for (Iterator<Map.Entry<String, JsonNode>> it = formsNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode formNode = e.getValue();
                if (!formNode.isObject()) continue;
                String mainTable = formNode.path("mainTable").asText("");
                if (mainTable == null || mainTable.trim().isEmpty()) continue;
                String dt = mainTable.trim();

                String tableRemark = remarkByTypeAndKey.get(dt + "||" + dt);
                if (tableRemark != null && formNode instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) formNode).put("remark", tableRemark);
                }

                JsonNode mainFields = formNode.path("mainFields");
                if (mainFields.isArray()) {
                    for (JsonNode f : mainFields) {
                        if (!(f instanceof com.fasterxml.jackson.databind.node.ObjectNode)) continue;
                        String code = f.path("code").asText("");
                        if (code == null || code.trim().isEmpty()) continue;
                        String r = remarkByTypeAndKey.get(dt + "||" + code.trim());
                        if (r != null) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) f).put("remark", r);
                        }
                    }
                }

                JsonNode sons = formNode.path("sons");
                if (sons.isArray()) {
                    for (JsonNode s : sons) {
                        JsonNode fields = s.path("fields");
                        if (!fields.isArray()) continue;
                        for (JsonNode f : fields) {
                            if (!(f instanceof com.fasterxml.jackson.databind.node.ObjectNode)) continue;
                            String code = f.path("code").asText("");
                            if (code == null || code.trim().isEmpty()) continue;
                            String r = remarkByTypeAndKey.get(dt + "||" + code.trim());
                            if (r != null) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) f).put("remark", r);
                            }
                        }
                    }
                }
            }

            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Matcher m = INLINE_FIELD_WITH_REMARK.matcher(pretty);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String rep = "{ \"primaryKey\": \"" + m.group(1) + "\", \"code\": \"" + m.group(2) + "\", \"label\": \"" + m.group(3) + "\", \"remark\": \"" + m.group(4) + "\" }";
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            Files.write(metaPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignore) {
            // 同步 form_meta 失败不应影响主保存链路
        }
    }
}
