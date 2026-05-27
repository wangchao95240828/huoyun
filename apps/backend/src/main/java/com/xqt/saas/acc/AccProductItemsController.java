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

@RestController
@RequestMapping("/api/acc/product-items")
public class AccProductItemsController {
    private static final String TABLE = "acc_product_items";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccProductItemsController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_product_items WHERE ?::text IS NULL OR (code ILIKE ? OR name ILIKE ?)",
                Long.class, search, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, name_en, hs_code, category,
                       unit_price, currency, is_active, remark,
                       audit_status, audited_at, audit_name, created_at
                FROM acc_product_items
                WHERE ?::text IS NULL OR (code ILIKE ? OR name ILIKE ?)
                ORDER BY code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_product_items WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_product_items (tenant_id, code, name, name_en, hs_code, category,
                                           unit_price, currency, is_active, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?::text, ?::text,
                    ?::text, ?::numeric, ?::text, ?::boolean, ?::text)
            RETURNING id::text
            """, String.class, code, name, body.get("nameEn"), body.get("hsCode"),
            body.get("category"), body.get("unitPrice"), body.get("currency"),
            body.get("isActive"), body.get("remark"));
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_product_items WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("品名已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_product_items SET
              name = coalesce(?, name),
              name_en = coalesce(?::text, name_en),
              hs_code = coalesce(?::text, hs_code),
              category = coalesce(?::text, category),
              unit_price = coalesce(?::numeric, unit_price),
              is_active = coalesce(?::boolean, is_active),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("name"), (String) allowed.get("nameEn"),
            (String) allowed.get("hsCode"), (String) allowed.get("category"),
            allowed.get("unitPrice"), allowed.get("isActive"),
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_product_items WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("品名已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_product_items WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        out.put("nameEn", row.get("name_en"));
        out.put("hsCode", row.get("hs_code"));
        out.put("category", row.get("category"));
        out.put("unitPrice", row.get("unit_price"));
        out.put("currency", row.get("currency"));
        out.put("isActive", row.get("is_active"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
