package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.audit.AuditAction;
import com.xqt.saas.framework.audit.AuditService;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
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
@RequestMapping("/api/acc/customer-adjusts")
public class AccCustomerAdjustsController extends AccFinanceTxnsBase {

    private final AuditService auditService;

    public AccCustomerAdjustsController(JdbcTemplate jdbc, JsonSupport json,
                                        CascadeChecker cascadeChecker, FieldGate fieldGate,
                                        MoneySnapshotService moneySnapshotService,
                                        AuditService auditService) {
        super(jdbc, json, cascadeChecker, fieldGate, moneySnapshotService);
        this.auditService = auditService;
    }
    @Override protected String side()    { return "CUSTOMER"; }
    @Override protected String txnType() { return "ADJUST"; }
    @Override protected String labelCn() { return "客户调账"; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required=false) Integer page,
                                    @RequestParam(required=false) Integer pageSize,
                                    @RequestParam(required=false) String keyword,
                                    @RequestParam(required=false) String dateFrom,
                                    @RequestParam(required=false) String dateTo) {
        return listImpl(page, pageSize, keyword, dateFrom, dateTo);
    }
    @GetMapping("/{id}/raw") public Map<String, Object> raw(@PathVariable String id) { return rawImpl(id); }
    @PostMapping public Map<String, Object> create(@RequestBody Map<String, Object> body) { return createImpl(body); }
    @PutMapping("/{id}") public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) { return updateImpl(id, body); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(@PathVariable String id) { return deleteImpl(id); }

    /**
     * B1 — 客户调账审核（带业务联动）
     *
     * 与通用 AccAuditController.audit 的区别：
     *   通用版只改 audit_status 字段，不联动余额。
     *   此端点额外：① 生成 ADJUST 类型 charge ② 更新客户余额 ③ 写 balance_ledger
     *
     * 业务规则：
     *   - direction=IN（客户给我）=减客户欠款（余额+）
     *   - direction=OUT（我给客户）=加客户欠款（余额-）
     *   - 金额 > 1000 必须先二审 (verify_status=VERIFIED)
     *   - 全流程单事务
     */
    @PostMapping("/{id}/audit-biz")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditWithBusiness(@PathVariable String id) {
        String actor = getCurrentUserName();

        // ① 抓 before
        Map<String, Object> before = rawImpl(id);
        if (before.isEmpty()) {
            throw ApiException.notFound("调账单不存在: " + id);
        }
        if (!"PENDING".equals(before.get("audit_status")) && !"UNAUDITED".equals(before.get("audit_status"))) {
            throw ApiException.badRequest("只有 PENDING/UNAUDITED 状态可以审核");
        }

        BigDecimal amount = before.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String direction = (String) before.get("direction");  // IN / OUT
        String customerId = (String) before.get("customer_id");
        String currency = (String) before.get("currency");

        // ② 二审门槛: 金额 > 1000 必须先二审
        String verifyStatus = (String) before.get("verify_status");
        if (amount.compareTo(new BigDecimal("1000")) > 0
            && !"VERIFIED".equals(verifyStatus)) {
            throw ApiException.badRequest("金额 > 1000，须先二审通过 (verify_status=VERIFIED)");
        }

        // ③ 改状态
        jdbc.update("""
            UPDATE acc_finance_txns SET
              audit_status = 'AUDITED', audited_at = now(), audit_name = ?
            WHERE id = ?::uuid AND side = ? AND txn_type = ?
            """, actor, id, side(), txnType());

        // ④ 生成 ADJUST 类型 charge
        String chargeItemId = jdbc.queryForObject(
            "SELECT id::text FROM charge_items WHERE code = 'ADJUST' LIMIT 1", String.class);
        BigDecimal signedAmount = "OUT".equals(direction) ? amount.negate() : amount;
        String chargeId = jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, charge_item_id, side, amount, currency, customer_id,
              charge_source_type, charge_source_id, evidence,
              audit_status, settlement_status
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, 'AR', ?, ?, ?::uuid,
              'adjust', ?::uuid,
              ?::jsonb, 'AUDITED', 'UNSETTLED'
            )
            RETURNING id::text
            """, String.class, chargeItemId, signedAmount, currency, customerId,
            id, json.toJson(Map.of("source", "adjust", "adjust_id", id,
                "reason", before.get("reason"))));

        // ⑤ 更新客户余额
        String balanceAccountId = ensureBalanceAccount(customerId, currency);
        BigDecimal balBefore = getBalance(balanceAccountId);
        // IN=客户给我=减客户欠款(余额正增加), OUT=我给客户=增客户欠款(余额减少)
        BigDecimal delta = "IN".equals(direction) ? amount : amount.negate();
        jdbc.update("UPDATE customer_balance_accounts SET balance = balance + ?, updated_at = now() WHERE id = ?::uuid",
            delta, balanceAccountId);

        // ⑥ 写 balance_ledger
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, type, amount, balance_before, balance_after,
              source_type, source_id, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, ?, ?, ?, ?,
              'adjust', ?::uuid, ?
            )
            """, balanceAccountId,
            "IN".equals(direction) ? "CREDIT" : "DEBIT",
            amount, balBefore, balBefore.add(delta), id,
            String.format("客户调账 %s ¥%s", direction, amount));

        // ⑦ 写审计流水
        Map<String, Object> after = rawImpl(id);
        auditService.recordEvent(TABLE, id, AuditAction.AUDIT, actor, before, after);
        auditService.recordEvent("charges", chargeId, AuditAction.CREATE, actor,
            Map.of(), Map.of("source", "adjust", "amount", signedAmount));

        return Map.of(
            "audited", true,
            "charge_id", chargeId,
            "balance_before", balBefore,
            "balance_after", balBefore.add(delta)
        );
    }

    /** 获取或创建客户余额账户 */
    private String ensureBalanceAccount(String customerId, String currency) {
        var existing = jdbc.queryForList("""
            SELECT id::text AS id FROM customer_balance_accounts
            WHERE customer_id = ?::uuid AND currency = ?
            """, customerId, currency);
        if (!existing.isEmpty()) return (String) existing.get(0).get("id");
        return jdbc.queryForObject("""
            INSERT INTO customer_balance_accounts (tenant_id, customer_id, currency, balance)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, 0)
            RETURNING id::text
            """, String.class, customerId, currency);
    }

    private BigDecimal getBalance(String accountId) {
        var bal = jdbc.queryForObject(
            "SELECT balance FROM customer_balance_accounts WHERE id = ?::uuid",
            BigDecimal.class, accountId);
        return bal == null ? BigDecimal.ZERO : bal;
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
}
