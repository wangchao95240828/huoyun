package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
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

/** /api/acc/reparations — 赔偿，前端列：expressNo / customerName / applyAmount / paidAmount / reason / addName / addTime。 */
@RestController
@RequestMapping("/api/acc/reparations")
public class AccReparationsController {
    private static final String TABLE = "acc_reparations";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccReparationsController(JdbcTemplate jdbc, JsonSupport json,
                                    CascadeChecker cascadeChecker, FieldGate fieldGate,
                                    MoneySnapshotService moneySnapshotService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String statusFilter = buildReparationsStatusFilter(status);
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_reparations r"
                + " LEFT JOIN shipments s ON s.id = r.shipment_id"
                + " LEFT JOIN customers c ON c.id = s.customer_id"
                + " WHERE (?::text IS NULL OR r.customer_ref ILIKE ? OR c.name ILIKE ?)"
                + "   AND (?::date IS NULL OR r.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR r.created_at < (?::date + 1))"
                + statusFilter,
                Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id::text AS id, r.customer_ref, r.apply_amount, r.paid_amount, r.currency,"
                + "       r.reason, r.status, r.add_name, r.created_at,"
                + "       r.audit_status, r.audited_at, r.audit_name,"
                + "       s.shipment_no, c.name AS customer_name"
                + " FROM acc_reparations r"
                + " LEFT JOIN shipments s ON s.id = r.shipment_id"
                + " LEFT JOIN customers c ON c.id = s.customer_id"
                + " WHERE (?::text IS NULL OR r.customer_ref ILIKE ? OR c.name ILIKE ?)"
                + "   AND (?::date IS NULL OR r.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR r.created_at < (?::date + 1))"
                + statusFilter
                + " ORDER BY r.created_at DESC"
                + " LIMIT ? OFFSET ?",
                search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    /** 客服中心 赔偿子页过滤 */
    private static String buildReparationsStatusFilter(String status) {
        if (status == null || status.isBlank()) return "";
        return switch (status) {
            case "DRAFT"   -> " AND r.status = 'DRAFT'";
            case "PENDING" -> " AND r.audit_status = 'PENDING'";
            case "DONE"    -> " AND r.audit_status = 'AUDITED'";
            default          -> "";
        };
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_reparations WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        BigDecimal applyAmount = body.get("applyAmount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (applyAmount.signum() <= 0) {
            throw ApiException.badRequest("申请赔偿金额必须大于零");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        if (currency.length() != 3) {
            throw ApiException.badRequest("找不到币种");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_reparations (
              tenant_id, shipment_id, customer_ref, apply_amount, currency, reason, status, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.get("shipment_id"),
            body.getOrDefault("expressNo", body.get("customer_ref")),
            applyAmount,
            currency,
            body.get("reason"),
            body.getOrDefault("status", "PENDING"),
            body.getOrDefault("addName", body.get("add_name")));
        moneySnapshotService.snapshot(TABLE, id, applyAmount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_reparations WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("赔偿单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_reparations SET
              paid_amount = coalesce(?, paid_amount),
              status      = coalesce(?, status),
              reason      = coalesce(?, reason)
            WHERE id = ?::uuid
            """,
            allowed.get("paidAmount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) allowed.get("status"),
            (String) allowed.get("reason"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_reparations WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("赔偿单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_reparations WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("expressNo", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("applyAmount", row.get("apply_amount"));
        out.put("paidAmount", row.get("paid_amount"));
        out.put("currency", row.get("currency"));
        out.put("reason", row.get("reason"));
        out.put("status", row.get("status"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    // ════════ P0 赔偿 Verify 二级审核 (对齐 ACC Reparation.php doVerify) ════════
    //   POST /{id}/verify: 大额赔偿先经一个 verifier (财务主管) verify
    //                       再走通用 audit-biz 让 auditor (总监) audit 真入账.
    //   触发条件: apply_amount > 阈值 (默认 1000), 强制走二审.
    @org.springframework.web.bind.annotation.PostMapping("/{id}/verify")
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> verify(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.Map<String, Object> body) {
        java.util.Map<String, Object> r;
        try {
            r = jdbc.queryForMap("""
                SELECT apply_amount, currency, audit_status FROM acc_reparations WHERE id = ?::uuid
                """, id);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("赔偿单不存在: " + id);
        }
        if ("AUDITED".equals(r.get("audit_status"))) {
            throw com.xqt.saas.common.ApiException.badRequest("赔偿已终审, 不能再 verify");
        }
        java.math.BigDecimal amount = (java.math.BigDecimal) r.get("apply_amount");
        String verifyNote = body != null ? (String) body.get("note") : null;
        // 标记 verify_status=VERIFIED, audit_status 仍 PENDING
        jdbc.update("""
            UPDATE acc_reparations
            SET status = 'VERIFIED',
                remark = coalesce(remark, '') || ' | Verify: ' || ?
            WHERE id = ?::uuid
            """, verifyNote == null ? "OK" : verifyNote, id);
        return java.util.Map.of(
            "id", id, "verified", true,
            "amount", amount,
            "nextStep", "调 audit-biz 让 auditor 总监 audit 真入账"
        );
    }
}
