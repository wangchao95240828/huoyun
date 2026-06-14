package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
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

/** /api/acc/collects — 总单/留仓，前端列：no / piece / weight / status / remark / addName / addTime。 */
@RestController
@RequestMapping("/api/acc/collects")
public class AccCollectsController {
    private static final String TABLE = "acc_collects";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccCollectsController(JdbcTemplate jdbc, JsonSupport json,
                                 CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_collects
                WHERE (?::text IS NULL OR collect_no ILIKE ?)
                  AND (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, collect_no, piece, weight_kg, status, remark, add_name, created_at,
                       audit_status, audited_at, audit_name
                FROM acc_collects
                WHERE (?::text IS NULL OR collect_no ILIKE ?)
                  AND (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_collects WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object collectNo = body.getOrDefault("no", body.get("collect_no"));
        if (collectNo == null || collectNo.toString().isBlank()) {
            throw ApiException.badRequest("收货单号必填");
        }
        Object pieceRaw = body.get("piece");
        if (pieceRaw instanceof Number pn && pn.intValue() <= 0) {
            throw ApiException.badRequest("件数必须大于零");
        }
        Object weightRaw = body.get("weight");
        if (weightRaw instanceof Number wn && wn.doubleValue() <= 0) {
            throw ApiException.badRequest("重量必须大于零");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_collects (tenant_id, collect_no, piece, weight_kg, status, remark, add_name)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class,
            body.getOrDefault("no", body.get("collect_no")),
            body.get("piece") instanceof Number n ? n.intValue() : 0,
            body.get("weight") instanceof Number w ? new BigDecimal(w.toString()) : BigDecimal.ZERO,
            body.getOrDefault("status", "PENDING"),
            body.get("remark"),
            body.getOrDefault("addName", body.get("add_name")));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_collects WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("总单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_collects SET
              status = coalesce(?, status),
              remark = coalesce(?, remark)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("status"),
            (String) allowed.get("remark"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_collects WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("总单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_collects WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("collect_no"));
        out.put("piece", row.get("piece"));
        out.put("weight", row.get("weight_kg"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
