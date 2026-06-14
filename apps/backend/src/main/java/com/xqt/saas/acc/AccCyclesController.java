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

@RestController
@RequestMapping("/api/acc/cycles")
public class AccCyclesController {
    private static final String TABLE = "acc_cycles";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccCyclesController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_cycles WHERE (?::text IS NULL OR name ILIKE ?)
                """, Long.class, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id::text AS id, c.name, c.cycle, c.start_date, c.end_date,
                       c.currency, c.amount, c.remark,
                       c.audit_status, c.audited_at, c.audit_name,
                       fa.account_name AS bank_name
                FROM acc_cycles c
                LEFT JOIN financial_accounts fa ON fa.id = c.bank_account_id
                WHERE (?::text IS NULL OR c.name ILIKE ?)
                ORDER BY c.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_cycles WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        // ACC Cycle.php L827: 相同的费用已经存在 + L818 分类存在
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("周期费用名称必填");
        }
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("周期费用金额必须大于零");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        // ACC Cycle.php L342: 找不到币种
        if (currency.length() != 3) {
            throw ApiException.badRequest("找不到币种");
        }
        // ACC Cycle.php L811: 续费到期时间小于或等于当前实际的到期时间 — 这里检查 start_date < end_date
        String startDate = (String) body.getOrDefault("startDate", body.get("start_date"));
        String endDate   = (String) body.getOrDefault("endDate", body.get("end_date"));
        if (startDate != null && endDate != null && endDate.compareTo(startDate) < 0) {
            throw ApiException.badRequest("结束日期不能早于开始日期");
        }
        // 同名重复检测
        Integer dup = jdbc.queryForObject(
            "SELECT count(*) FROM acc_cycles WHERE name = ?", Integer.class, name);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("相同名称的周期费用已存在");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_cycles (
              tenant_id, name, cycle, start_date, end_date, currency, amount,
              bank_account_id, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?, ?::date, ?::date, ?, ?,
              ?::uuid, ?
            )
            RETURNING id::text
            """, String.class,
            body.get("name"),
            body.get("cycle"),
            body.getOrDefault("startDate", body.get("start_date")),
            body.getOrDefault("endDate", body.get("end_date")),
            currency, amount,
            body.get("bank_account_id"),
            body.get("remark"));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_cycles WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("周期费用已审核，字段不可修改: " + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("UPDATE acc_cycles SET remark = coalesce(?, remark) WHERE id = ?::uuid",
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_cycles WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("周期费用已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_cycles WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("cycle", row.get("cycle"));
        out.put("startDate", json.value(row.get("start_date")));
        out.put("endDate", json.value(row.get("end_date")));
        out.put("currency", row.get("currency"));
        out.put("amount", row.get("amount"));
        out.put("account", row.get("bank_name"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
