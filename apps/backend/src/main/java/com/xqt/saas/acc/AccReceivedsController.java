package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.ArrayList;
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

/**
 * /api/acc/receiveds — 前端列：no / customerName / bankName / amount / theDate / auditName / remark
 * AR 收款，来源 payments + customers。
 */
@RestController
@RequestMapping("/api/acc/receiveds")
public class AccReceivedsController {
    private static final String TABLE = "payments";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;
    private final AuditService auditService;

    public AccReceivedsController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM payments p
                WHERE (?::text IS NULL OR p.reference_no ILIKE ?)
                  AND (?::date IS NULL OR p.received_at >= ?::date)
                  AND (?::date IS NULL OR p.received_at < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  p.id::text       AS id,
                  p.reference_no,
                  p.currency,
                  p.amount,
                  p.received_at,
                  p.audit_status,
                  p.audited_at,
                  p.audit_name,
                  c.name           AS customer_name
                FROM payments p
                LEFT JOIN customers c ON c.id = p.customer_id
                WHERE (?::text IS NULL OR p.reference_no ILIKE ?)
                  AND (?::date IS NULL OR p.received_at >= ?::date)
                  AND (?::date IS NULL OR p.received_at < (?::date + 1))
                ORDER BY p.received_at DESC
                LIMIT ? OFFSET ?
                """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM payments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object customerId = body.get("customer_id");
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String referenceNo = (String) body.getOrDefault("reference_no", body.get("no"));
        String id = jdbc.queryForObject("""
            INSERT INTO payments (
              tenant_id, customer_id, currency, amount, received_at, reference_no
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, now(), ?
            )
            RETURNING id::text
            """, String.class,
            customerId == null ? null : customerId.toString(),
            currency, amount, referenceNo);
        // AR 收款的汇率快照
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("收款已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE payments SET
              reference_no = coalesce(?, reference_no),
              amount       = coalesce(?, amount)
            WHERE id = ?::uuid
            """, (String) allowed.get("reference_no"),
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        // 改了金额刷新汇率快照
        if (allowed.containsKey("amount") && allowed.get("amount") instanceof Number n) {
            String currency = jdbc.queryForObject(
                "SELECT currency FROM payments WHERE id = ?::uuid", String.class, id);
            moneySnapshotService.snapshot(TABLE, id, new BigDecimal(n.toString()), currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("收款已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM payments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /** 对应前端 "快速收款"。 */
    @PostMapping("/quick")
    public Map<String, Object> quick(@RequestBody Map<String, Object> body) {
        return create(body);
    }

    /**
     * A5 — 客户收款 FIFO 自动核销
     *
     * 流程：录收款金额 → FIFO 遍历未付账单 → 按比例分摊到 charges
     * → 更新账户余额 → 多余部分进客户预付余额
     *
     * 业务规则：
     *   1. 按 invoice_date ASC 遍历未付账单 (unpaid_amount > 0)
     *   2. 每张按 min(剩余付款, 账单未付) 核销
     *   3. 按比例分摊到账单下各 charges: share = apply × (charge.amount / 账单总额)
     *   4. charges 付清 → SETTLED；部分付 → PARTIAL
     *   5. 账单 unpaid=0 → PAID
     *   6. 银行余额联动: financial_accounts.balance += payment
     *   7. 多余部分 → customer_balance_accounts
     *   8. 全流程单事务，行锁防并发
     */
    @PostMapping("/settle")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settle(@RequestBody Map<String, Object> body) {
        String customerId     = (String) body.get("customer_id");
        String currency       = (String) body.get("currency");
        BigDecimal payment    = new BigDecimal(body.get("settled_amount").toString());
        String bankAccountId  = (String) body.get("bank_account_id");
        String remark         = (String) body.get("remark");

        if (payment.signum() <= 0) {
            throw ApiException.badRequest("settled_amount 必须 > 0");
        }

        String actor = getCurrentUserName();

        // ① 建收款单主表
        String settleNo = "RECV-" + java.time.LocalDate.now().toString()
            .substring(0, 7).replace("-", "") + "-" + String.format("%04d", nextRecvSeq());
        String settleId = jdbc.queryForObject("""
            INSERT INTO receivable_settlements (
              tenant_id, settlement_no, customer_id, currency,
              settled_amount, status, bank_account_id, settled_at
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, ?, ?, 'POSTED', ?::uuid, now()
            )
            RETURNING id::text
            """, String.class, settleNo, customerId, currency, payment, bankAccountId);

        // ② FIFO 遍历未付账单（加行锁防并发）
        List<Map<String, Object>> invoices = jdbc.queryForList("""
            SELECT id::text AS id, total_amount, unpaid_amount
            FROM customer_invoices
            WHERE customer_id = ?::uuid AND currency = ?
              AND unpaid_amount > 0 AND status <> 'VOID'
            ORDER BY invoice_date
            FOR UPDATE
            """, customerId, currency);

        BigDecimal remaining = payment;
        List<Map<String, Object>> matched = new ArrayList<>();

        for (Map<String, Object> inv : invoices) {
            if (remaining.signum() <= 0) break;
            BigDecimal unpaid = (BigDecimal) inv.get("unpaid_amount");
            BigDecimal totalAmt = (BigDecimal) inv.get("total_amount");
            BigDecimal apply = remaining.min(unpaid);
            String invId = (String) inv.get("id");

            // 写核销明细
            jdbc.update("""
                INSERT INTO receivable_settlement_lines (
                  tenant_id, settlement_id, invoice_id, amount
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid,
                  ?::uuid, ?::uuid, ?
                )
                """, settleId, invId, apply);

            // 按比例分摊到 charges
            settleChargesOfInvoice(invId, apply, totalAmt, actor);

            // 更新账单
            BigDecimal newPaid = unpaid.subtract(apply);
            jdbc.update("""
                UPDATE customer_invoices SET
                  paid_amount = paid_amount + ?,
                  unpaid_amount = unpaid_amount - ?,
                  last_payment_at = now(),
                  status = CASE WHEN unpaid_amount - ? <= 0 THEN 'PAID' ELSE status END
                WHERE id = ?::uuid
                """, apply, apply, apply, invId);

            matched.add(Map.of(
                "invoice_id", invId,
                "applied", apply,
                "remaining_unpaid", newPaid,
                "status_after", apply.compareTo(unpaid) >= 0 ? "PAID" : "PARTIAL"
            ));

            remaining = remaining.subtract(apply);
        }

        // ③ 银行账户余额联动
        if (bankAccountId != null) {
            BigDecimal balBefore = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid FOR UPDATE",
                BigDecimal.class, bankAccountId);
            jdbc.update("UPDATE financial_accounts SET balance = balance + ? WHERE id = ?::uuid",
                payment, bankAccountId);
            jdbc.update("""
                INSERT INTO balance_ledger (
                  tenant_id, account_id, type, amount, balance_before, balance_after,
                  source_type, source_id, remark
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid,
                  ?::uuid, 'IN', ?, ?, ?,
                  'receivable_settlement', ?::uuid, ?
                )
                """, bankAccountId, payment, balBefore, balBefore.add(payment), settleId, remark);
        }

        // ④ 多余部分进客户预付余额
        if (remaining.signum() > 0) {
            String balAcct = ensureBalanceAccount(customerId, currency);
            BigDecimal bal = getBalance(balAcct);
            jdbc.update("UPDATE customer_balance_accounts SET balance = balance + ?, updated_at = now() WHERE id = ?::uuid",
                remaining, balAcct);
            if (bankAccountId != null) {
                jdbc.update("""
                    INSERT INTO balance_ledger (
                      tenant_id, account_id, type, amount, balance_before, balance_after,
                      source_type, source_id, remark
                    ) VALUES (
                      current_setting('app.current_tenant_id')::uuid,
                      ?::uuid, 'CREDIT', ?, ?, ?,
                      'receivable_settlement', ?::uuid, '客户多付，进预付余额'
                    )
                    """, bankAccountId, remaining, bal, bal.add(remaining), settleId);
            }
        }

        // ⑤ 写审计事件
        auditService.recordEvent("receivable_settlements", settleId, AuditAction.CREATE,
            actor, Map.of(), Map.of(
                "settlement_no", settleNo,
                "customer_id", customerId,
                "currency", currency,
                "settled_amount", payment,
                "matched_count", matched.size(),
                "unmatched_amount", remaining
            ));

        return Map.of(
            "settlement_id", settleId,
            "settlement_no", settleNo,
            "matched_invoices", matched,
            "total_matched", matched.size(),
            "unmatched_amount", remaining
        );
    }

    /**
     * 按比例将收款分摊到账单下的各 charges
     */
    private void settleChargesOfInvoice(String invoiceId, BigDecimal apply,
                                         BigDecimal totalAmt, String actor) {
        List<Map<String, Object>> chs = jdbc.queryForList("""
            SELECT id::text AS id, amount, paid_amount, settlement_status
            FROM charges WHERE invoice_id = ?::uuid
            """, invoiceId);

        // charges 总额为零或 apply 为零，跳过
        if (totalAmt.signum() == 0 || apply.signum() == 0) return;

        for (Map<String, Object> ch : chs) {
            BigDecimal chargeAmt = (BigDecimal) ch.get("amount");
            // 按金额比例分摊
            BigDecimal share = apply.multiply(chargeAmt)
                .divide(totalAmt, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal oldPaid = (BigDecimal) ch.get("paid_amount");
            BigDecimal newPaid = oldPaid.add(share);
            boolean full = newPaid.compareTo(chargeAmt) >= 0;
            String newStatus = full ? "SETTLED" : "PARTIAL";

            Map<String, Object> before = Map.of(
                "paid_amount", oldPaid,
                "settlement_status", ch.get("settlement_status"));

            jdbc.update(
                "UPDATE charges SET paid_amount = ?, settlement_status = ? WHERE id = ?::uuid",
                newPaid, newStatus, ch.get("id"));

            auditService.recordEvent("charges", (String) ch.get("id"),
                full ? AuditAction.AUDIT : AuditAction.UPDATE,
                actor, before,
                Map.of("paid_amount", newPaid, "settlement_status", newStatus));
        }
    }

    /** 获取或创建客户余额账户 */
    private String ensureBalanceAccount(String customerId, String currency) {
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT id::text AS id FROM customer_balance_accounts
            WHERE customer_id = ?::uuid AND currency = ?
            """, customerId, currency);
        if (!existing.isEmpty()) {
            return (String) existing.get(0).get("id");
        }
        return jdbc.queryForObject("""
            INSERT INTO customer_balance_accounts (tenant_id, customer_id, currency, balance)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, 0)
            RETURNING id::text
            """, String.class, customerId, currency);
    }

    /** 获取客户余额 */
    private BigDecimal getBalance(String accountId) {
        BigDecimal bal = jdbc.queryForObject(
            "SELECT balance FROM customer_balance_accounts WHERE id = ?::uuid",
            BigDecimal.class, accountId);
        return bal == null ? BigDecimal.ZERO : bal;
    }

    /** 收款序列号 */
    private int nextRecvSeq() {
        return jdbc.queryForObject("SELECT nextval('cinv_invoice_seq')", Integer.class);
    }

    /** 获取当前登录用户名 */
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
        out.put("no", row.get("reference_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("bankName", "");                       // 新模型未建模
        out.put("amount", row.get("amount"));
        out.put("theDate", json.value(row.get("received_at")));
        out.put("auditName", "");
        out.put("remark", "");
        out.put("currency", row.get("currency"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
