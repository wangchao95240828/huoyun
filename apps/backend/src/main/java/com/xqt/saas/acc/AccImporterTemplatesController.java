package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
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

/** ACC 制单 → 进口商预设模板下拉。 */
@RestController
@RequestMapping("/api/acc/importer-templates")
public class AccImporterTemplatesController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccImporterTemplatesController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String customerId
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_importer_templates"
                + " WHERE (?::text IS NULL OR name ILIKE ?)"
                + "   AND (?::text IS NULL OR customer_id::text = ?)",
                Long.class, search, search, customerId, customerId);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id::text AS id, t.name, t.country, t.tax_id, t.address,"
                + "       t.contact_name, t.contact_phone,"
                + "       t.customer_id::text AS customer_id, cu.name AS customer_name,"
                + "       t.audit_status, t.audited_at, t.audit_name, t.created_at"
                + " FROM acc_importer_templates t"
                + " LEFT JOIN customers cu ON cu.id = t.customer_id"
                + " WHERE (?::text IS NULL OR t.name ILIKE ?)"
                + "   AND (?::text IS NULL OR t.customer_id::text = ?)"
                + " ORDER BY t.name LIMIT ? OFFSET ?",
                search, search, customerId, customerId, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_importer_templates WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = jdbc.queryForObject(
            "INSERT INTO acc_importer_templates ("
            + "  tenant_id, name, country, tax_id, address, contact_name, contact_phone, customer_id"
            + ") VALUES ("
            + "  (SELECT id FROM tenants WHERE code='xqt' LIMIT 1),"
            + "  ?, ?, ?, ?, ?, ?, ?::uuid"
            + ") RETURNING id::text",
            String.class,
            body.get("name"), body.get("country"), body.get("taxId"),
            body.get("address"), body.get("contactName"), body.get("contactPhone"),
            body.get("customerId"));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        jdbc.update(
            "UPDATE acc_importer_templates SET"
            + "  name          = coalesce(?, name),"
            + "  country       = coalesce(?, country),"
            + "  tax_id        = coalesce(?, tax_id),"
            + "  address       = coalesce(?, address),"
            + "  contact_name  = coalesce(?, contact_name),"
            + "  contact_phone = coalesce(?, contact_phone),"
            + "  customer_id   = coalesce(?::uuid, customer_id),"
            + "  updated_at    = now()"
            + " WHERE id = ?::uuid",
            body.get("name"), body.get("country"), body.get("taxId"),
            body.get("address"), body.get("contactName"), body.get("contactPhone"),
            body.get("customerId"), id);
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM acc_importer_templates WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("country", row.get("country"));
        out.put("taxId", row.get("tax_id"));
        out.put("address", row.get("address"));
        out.put("contactName", row.get("contact_name"));
        out.put("contactPhone", row.get("contact_phone"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("createdAt", json.value(row.get("created_at")));
        return out;
    }
}
