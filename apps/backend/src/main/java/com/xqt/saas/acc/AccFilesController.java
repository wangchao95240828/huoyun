package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/acc/files — ACC 基础信息→运费管理→文件管理（合同/价格表/模板等附件）。 */
@RestController
@RequestMapping("/api/acc/files")
public class AccFilesController {
    private static final String TABLE = "acc_files";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccFilesController(JdbcTemplate jdbc, JsonSupport json,
                              CascadeChecker cascadeChecker, FieldGate fieldGate) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String fileType
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_files"
                + " WHERE (?::text IS NULL OR file_name ILIKE ?)"
                + "   AND (?::text IS NULL OR file_type = ?)",
                Long.class, search, search, fileType, fileType);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text AS id, file_name, file_type, mime_type, size_bytes,"
                + "       storage_url, uploader_name, remark, status,"
                + "       audit_status, audited_at, audit_name, created_at"
                + " FROM acc_files"
                + " WHERE (?::text IS NULL OR file_name ILIKE ?)"
                + "   AND (?::text IS NULL OR file_type = ?)"
                + " ORDER BY created_at DESC"
                + " LIMIT ? OFFSET ?",
                search, search, fileType, fileType, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_files WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = jdbc.queryForObject(
            "INSERT INTO acc_files ("
            + "  tenant_id, file_name, file_type, mime_type, size_bytes,"
            + "  storage_url, uploader_name, remark, status"
            + ") VALUES ("
            + "  current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, ?, ?, ?"
            + ") RETURNING id::text",
            String.class,
            body.get("fileName"),
            body.get("fileType"),
            body.get("mimeType"),
            body.get("sizeBytes") instanceof Number n ? n.longValue() : 0L,
            body.get("storageUrl"),
            body.get("uploaderName"),
            body.get("remark"),
            body.getOrDefault("status", "ACTIVE"));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_files WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("文件已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> a = gate.allowed();
        jdbc.update(
            "UPDATE acc_files SET"
            + "  file_name   = coalesce(?, file_name),"
            + "  file_type   = coalesce(?, file_type),"
            + "  storage_url = coalesce(?, storage_url),"
            + "  remark      = coalesce(?, remark),"
            + "  status      = coalesce(?, status)"
            + " WHERE id = ?::uuid",
            (String) a.get("fileName"),
            (String) a.get("fileType"),
            (String) a.get("storageUrl"),
            (String) a.get("remark"),
            (String) a.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_files WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("文件已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_files WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("fileName", row.get("file_name"));
        out.put("fileType", row.get("file_type"));
        out.put("mimeType", row.get("mime_type"));
        out.put("sizeBytes", row.get("size_bytes"));
        out.put("storageUrl", row.get("storage_url"));
        out.put("uploaderName", row.get("uploader_name"));
        out.put("remark", row.get("remark"));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("createdAt", json.value(row.get("created_at")));
        return out;
    }
}
