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

/**
 * /api/acc/fee-types — 前端列：name / type / unit / method / remark
 * 来源 charge_items：name=name、type=category、unit=default_uom、method=default_side。
 *
 * 注意路径包含连字符，Spring 的 RequestMapping 支持。
 */
@RestController
@RequestMapping("/api/acc/fee-types")
public class AccFeeTypesController {
    private static final String TABLE = "charge_items";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccFeeTypesController(JdbcTemplate jdbc, JsonSupport json,
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

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM charge_items
                WHERE (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, category,
                       default_side::text AS default_side,
                       default_uom::text  AS default_uom,
                       audit_status, audited_at, audit_name
                FROM charge_items
                WHERE (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                ORDER BY category, code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM charge_items WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        if (code == null || code.isBlank()) throw ApiException.badRequest("费用类型代码必填");
        if (!code.matches("[\\x00-\\x7F]+")) throw ApiException.badRequest("费用类型代码不能包含中文");
        if (name == null || name.isBlank()) throw ApiException.badRequest("费用类型名称必填");
        String category = (String) body.getOrDefault("type", body.getOrDefault("category", "FREIGHT"));
        String side = (String) body.getOrDefault("method", body.getOrDefault("default_side", "AR"));
        String uom = (String) body.getOrDefault("unit", body.getOrDefault("default_uom", "KG"));
        if (!java.util.List.of("AR","AP","BOTH").contains(side)) {
            throw ApiException.badRequest("结算方向必须是 AR/AP/BOTH");
        }
        // ACC FeeType.php L212/L234: 判断公式校验
        Object condRaw = body.get("condition");
        if (condRaw != null && !condRaw.toString().isBlank()) {
            String cond = condRaw.toString();
            // 必须含 {var} 参数
            if (!cond.matches(".*\\{[^}]+\\}.*")) {
                throw ApiException.badRequest("判断公式要么为空，要么必须包含一个参数 {xxx}");
            }
        }
        // ACC FeeType.php L?: 超限值区间格式 30-50
        Object rangeRaw = body.get("overLimit");
        if (rangeRaw != null && !rangeRaw.toString().isBlank()) {
            String rng = rangeRaw.toString();
            if (!rng.matches("\\d+(\\.\\d+)?-\\d+(\\.\\d+)?")) {
                throw ApiException.badRequest("超限值必须为区间范围，例如 30-50");
            }
            String[] parts = rng.split("-");
            double start = Double.parseDouble(parts[0]);
            double end = Double.parseDouble(parts[1]);
            if (start >= end) {
                throw ApiException.badRequest("超限值区间起始值 " + start + " 不能大于或等于终止值 " + end);
            }
        }
        String id = jdbc.queryForObject("""
            INSERT INTO charge_items (tenant_id, code, name, category, default_side, default_uom)
            VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?, ?, ?::charge_side, ?::billing_uom
            )
            RETURNING id::text
            """, String.class, code, name, category, side, uom);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM charge_items WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("费用类型已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE charge_items SET
              code         = coalesce(?, code),
              name         = coalesce(?, name),
              category     = coalesce(?, category)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("code"),
            (String) allowed.get("name"),
            (String) allowed.getOrDefault("type", allowed.get("category")),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM charge_items WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("费用类型已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM charge_items WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("code", row.get("code"));
        out.put("type", row.get("category"));
        out.put("unit", row.get("default_uom"));
        out.put("method", row.get("default_side"));
        out.put("remark", "");
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
