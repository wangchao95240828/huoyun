package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.audit.AuditAction;
import com.xqt.saas.framework.audit.AuditService;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/acc/transfers")
public class AccTransfersController {
    private static final String TABLE = "acc_transfers";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;
    private final AuditService auditService;

    public AccTransfersController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  MoneySnapshotService moneySnapshotService,
                                  AuditService auditService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
        this.auditService = auditService;
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
                SELECT count(*) FROM acc_transfers
                WHERE (?::text IS NULL OR transfer_no ILIKE ?)
                  AND (?::date IS NULL OR the_date >= ?::date)
                  AND (?::date IS NULL OR the_date < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id::text AS id, t.transfer_no, t.the_date, t.amount, t.currency,
                       t.remark, t.add_name, t.created_at,
                       t.audit_status, t.audited_at, t.audit_name,
                       f1.account_name AS from_bank,
                       f2.account_name AS to_bank
                FROM acc_transfers t
                LEFT JOIN financial_accounts f1 ON f1.id = t.from_bank_id
                LEFT JOIN financial_accounts f2 ON f2.id = t.to_bank_id
                WHERE (?::text IS NULL OR t.transfer_no ILIKE ?)
                  AND (?::date IS NULL OR t.the_date >= ?::date)
                  AND (?::date IS NULL OR t.the_date < (?::date + 1))
                ORDER BY t.created_at DESC
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
            "SELECT * FROM acc_transfers WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_transfers (
              tenant_id, transfer_no, the_date, from_bank_id, to_bank_id,
              amount, currency, remark, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?::date,
              ?::uuid, ?::uuid, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.get("transfer_no"),
            body.getOrDefault("theDate", body.get("the_date")),
            body.get("from_bank_id"),
            body.get("to_bank_id"),
            amount, currency,
            body.get("remark"),
            body.getOrDefault("addName", body.get("add_name")));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_transfers WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("转账已审核，字段不可修改: " + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_transfers SET
              remark = coalesce(?, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_transfers WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("转账已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_transfers WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * C2 — 资金转账审核（双账户联动 + 二审门槛）
     *
     * 审核联动：
     *   1. from_account ≠ to_account（防自己转自己）
     *   2. > 10w 强制 verify_status=VERIFIED 才能审核（防单人误操作）
     *   3. 检查 from 账户余额充足（行锁防并发）
     *   4. 双账户原子联动：A 减 + B 加 + 两条 balance_ledger
     *   5. 全流程单事务，任一步失败全部回滚
     */
    @PostMapping("/{id}/audit-biz")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditWithBusiness(@PathVariable String id) {
        String actor = getCurrentUserName();

        // ① 抓 before（行锁）
        Map<String, Object> before = jdbc.queryForMap(
            "SELECT * FROM acc_transfers WHERE id = ?::uuid FOR UPDATE", id);
        if (before.isEmpty()) {
            throw ApiException.notFound("转账单不存在: " + id);
        }
        if (!"PENDING".equals(before.get("audit_status"))
            && !"UNAUDITED".equals(before.get("audit_status"))) {
            throw ApiException.badRequest("只有 PENDING/UNAUDITED 状态可以审核");
        }

        BigDecimal amount = (BigDecimal) before.get("amount");
        String verifyStatus = (String) before.get("verify_status");

        // ② 二审门槛：> 10w 必须先二审
        if (amount.compareTo(new BigDecimal("100000")) > 0
            && !"VERIFIED".equals(verifyStatus)) {
            throw ApiException.badRequest("金额 " + amount
                + " > 10w 阈值，须先二审 (verify_status=VERIFIED)");
        }

        String fromId = (String) before.get("from_bank_id");
        String toId   = (String) before.get("to_bank_id");

        if (fromId == null || toId == null) {
            throw ApiException.badRequest("转出/转入账户不能为空");
        }

        // ③ 检查 from 账户余额（行锁）
        BigDecimal fromBalance = jdbc.queryForObject(
            "SELECT balance FROM financial_accounts WHERE id = ?::uuid FOR UPDATE",
            BigDecimal.class, fromId);
        if (fromBalance.compareTo(amount) < 0) {
            throw ApiException.badRequest(
                "转出账户余额不足 (balance=" + fromBalance + ", need=" + amount + ")");
        }

        BigDecimal toBalance = jdbc.queryForObject(
            "SELECT balance FROM financial_accounts WHERE id = ?::uuid FOR UPDATE",
            BigDecimal.class, toId);

        // ④ 改审核状态
        jdbc.update("""
            UPDATE acc_transfers SET
              audit_status = 'AUDITED', audited_at = now(), audit_name = ?
            WHERE id = ?::uuid
            """, actor, id);

        // ⑤ 转出账户：减余额 + 写 OUT ledger
        jdbc.update("UPDATE financial_accounts SET balance = balance - ? WHERE id = ?::uuid",
            amount, fromId);
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, type, amount, balance_before, balance_after,
              source_type, source_id, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, 'OUT', ?, ?, ?,
              'transfer', ?::uuid, ?
            )
            """, fromId, amount, fromBalance, fromBalance.subtract(amount),
            id, "转账给账户 " + toId);

        // ⑥ 转入账户：加余额 + 写 IN ledger
        jdbc.update("UPDATE financial_accounts SET balance = balance + ? WHERE id = ?::uuid",
            amount, toId);
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, type, amount, balance_before, balance_after,
              source_type, source_id, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, 'IN', ?, ?, ?,
              'transfer', ?::uuid, ?
            )
            """, toId, amount, toBalance, toBalance.add(amount),
            id, "转账来自账户 " + fromId);

        // ⑦ 写审计流水
        Map<String, Object> after = jdbc.queryForMap(
            "SELECT * FROM acc_transfers WHERE id = ?::uuid", id);
        auditService.recordEvent(TABLE, id, AuditAction.AUDIT, actor, before, after);

        return Map.of(
            "audited", true,
            "from_balance", fromBalance.subtract(amount),
            "to_balance", toBalance.add(amount)
        );
    }

    private String getCurrentUserName() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.xqt.saas.auth.AuthPrincipal p) {
                return p.username();
            }
        } catch (Exception ignored) { }
        return "system";
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("transferNo", row.get("transfer_no"));
        out.put("fromBank", row.get("from_bank"));
        out.put("toBank", row.get("to_bank"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("theDate", json.value(row.get("the_date")));
        out.put("remark", row.get("remark"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
