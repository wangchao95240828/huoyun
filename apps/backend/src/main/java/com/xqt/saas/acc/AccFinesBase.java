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

/**
 * 罚款公共 controller 基类。customer-fines / supplier-fines 共用 `acc_fines` 表，side 区分。
 * 对应 ACC: CFine.php (customer) / SupplierFine (supplier)。
 */
abstract class AccFinesBase {
    protected static final String TABLE = "acc_fines";

    protected final JdbcTemplate jdbc;
    protected final JsonSupport json;
    protected final CascadeChecker cascadeChecker;
    protected final FieldGate fieldGate;
    protected final MoneySnapshotService moneySnapshotService;

    protected AccFinesBase(JdbcTemplate jdbc, JsonSupport json,
                           CascadeChecker cascadeChecker, FieldGate fieldGate,
                           MoneySnapshotService moneySnapshotService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
    }

    /** 子类返回 'CUSTOMER' 或 'SUPPLIER'。 */
    protected abstract String side();

    /** customer / supplier 的中文标签，用于报错。 */
    protected abstract String labelCn();

    protected Map<String, Object> listImpl(Integer page, Integer pageSize, String keyword,
                                           String dateFrom, String dateTo) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_fines f
                LEFT JOIN customers c ON c.id = f.customer_id
                LEFT JOIN partners  p ON p.id = f.partner_id
                WHERE f.side = ?
                  AND (?::text IS NULL OR f.fine_no ILIKE ? OR c.name ILIKE ? OR p.name ILIKE ?)
                  AND (?::date IS NULL OR f.the_date >= ?::date)
                  AND (?::date IS NULL OR f.the_date < (?::date + 1))
                """, Long.class, side(), search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.id::text AS id, f.fine_no, f.the_date, f.amount, f.currency,
                       f.remark, f.add_name, f.created_at,
                       f.audit_status, f.audited_at, f.audit_name,
                       c.name AS customer_name, p.name AS partner_name
                FROM acc_fines f
                LEFT JOIN customers c ON c.id = f.customer_id
                LEFT JOIN partners  p ON p.id = f.partner_id
                WHERE f.side = ?
                  AND (?::text IS NULL OR f.fine_no ILIKE ? OR c.name ILIKE ? OR p.name ILIKE ?)
                  AND (?::date IS NULL OR f.the_date >= ?::date)
                  AND (?::date IS NULL OR f.the_date < (?::date + 1))
                ORDER BY f.created_at DESC
                LIMIT ? OFFSET ?
                """, side(), search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    protected Map<String, Object> rawImpl(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_fines WHERE id = ?::uuid AND side = ? LIMIT 1", id, side());
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    protected Map<String, Object> createImpl(Map<String, Object> body) {
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_fines (
              tenant_id, fine_no, the_date, side, customer_id, partner_id, amount, currency, remark, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::date, ?, ?::uuid, ?::uuid, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.getOrDefault("no", body.get("fine_no")),
            body.getOrDefault("theDate", body.get("the_date")),
            side(),
            "CUSTOMER".equals(side()) ? body.get("customer_id") : null,
            "SUPPLIER".equals(side()) ? body.get("partner_id") : null,
            amount, currency,
            body.get("remark"),
            body.getOrDefault("addName", body.get("add_name")));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    protected Map<String, Object> updateImpl(String id, Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_fines WHERE id = ?::uuid AND side = ?",
            String.class, id, side());
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest(labelCn() + "罚款已审核，字段不可修改: "
                + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_fines SET
              amount = coalesce(?, amount),
              remark = coalesce(?, remark)
            WHERE id = ?::uuid AND side = ?
            """,
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) allowed.get("remark"),
            id, side());
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    protected Map<String, Object> deleteImpl(String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_fines WHERE id = ?::uuid AND side = ?",
            String.class, id, side());
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest(labelCn() + "罚款已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_fines WHERE id = ?::uuid AND side = ?", id, side());
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("fine_no"));
        out.put("theDate", json.value(row.get("the_date")));
        out.put("customerName", row.get("customer_name"));
        out.put("supplierName", row.get("partner_name"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("remark", row.get("remark"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        return out;
    }
}
