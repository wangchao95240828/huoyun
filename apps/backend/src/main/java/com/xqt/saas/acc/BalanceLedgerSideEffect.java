package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.framework.audit.AuditSideEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P0-B2 修复: charges / payments / partner_payments 审核时联动客户/供应商余额.
 *
 * ACC 对齐: Charge.php / Pay.php / Received.php 审核都调 updateBalance(side, ownerId, amount, currency).
 * 之前 xqt-saas 只 ChargeAuditGlSideEffect (GL 凭证) 和 FinanceAuditGlSideEffect
 * (customer_invoices/partner_payments GL 凭证), 都不动 balance_ledger / financial_accounts.balance.
 * 结果客户欠款查询 (AccCustomerReceivablesController) 跟实际审核状态错位.
 *
 * 规则:
 *   - charges side=AR 审核 → 客户欠款 +amount (DEBIT 客户预扣账户余额)
 *     反审 → 客户欠款 -amount (CREDIT 回退)
 *   - charges side=AP 审核 → 供应商欠款 +amount (DEBIT 供应商预扣账户)
 *     反审 → 反向
 *   - payments (客户收款) 审核 → 客户欠款 -amount (CREDIT 客户预扣账户)
 *     + 银行账户 +amount
 *   - partner_payments (供应商付款) 审核 → 供应商欠款 -amount
 *     + 银行账户 -amount
 *
 * 找不到 owner / financial_account 时静默跳过, 不阻断审核.
 */
@Component
public class BalanceLedgerSideEffect implements AuditSideEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceLedgerSideEffect.class);
    private final JdbcTemplate jdbc;

    public BalanceLedgerSideEffect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "charges".equals(table) || "payments".equals(table) || "partner_payments".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        apply(table, entityId, tenantId, actorName, false);
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        apply(table, entityId, tenantId, actorName, true);
    }

    private void apply(String table, String entityId, String tenantId, String actorName, boolean reverse) {
        try {
            if ("charges".equals(table)) applyCharge(entityId, tenantId, actorName, reverse);
            else if ("payments".equals(table)) applyCustomerReceived(entityId, tenantId, actorName, reverse);
            else if ("partner_payments".equals(table)) applySupplierPaid(entityId, tenantId, actorName, reverse);
        } catch (Exception ex) {
            // 不阻断审核, 只警告
            LOGGER.warn("BalanceLedgerSideEffect failed for {}/{}: {}", table, entityId, ex.getMessage());
        }
    }

    /** AR/AP charge 审核 → 增加客户/供应商欠款 (本质是 owner 应收/应付增加). */
    private void applyCharge(String chargeId, String tenantId, String actorName, boolean reverse) {
        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT c.side::text AS side, c.amount, c.currency, c.shipment_id::text AS shipment_id,
                       s.customer_id::text AS customer_id, s.channel_id::text AS channel_id
                  FROM charges c
                  LEFT JOIN shipments s ON s.id = c.shipment_id
                 WHERE c.id = ?::uuid
                """, chargeId);
        } catch (EmptyResultDataAccessException ex) { return; }
        BigDecimal amount = (BigDecimal) ch.get("amount");
        if (amount == null || amount.signum() <= 0) return;
        String currency = (String) ch.get("currency");
        String side = (String) ch.get("side");
        String customerId = (String) ch.get("customer_id");
        if (customerId == null) return;

        // AR: 客户欠款增加 (债权增加) → balance_ledger.direction=DEBIT 削减客户预扣账户
        // AP: 我们欠物流商 — 物流商账户 CREDIT 增加
        if ("AR".equals(side)) {
            // 客户 owner, 欠款增加 = 客户预扣账户余额 DEBIT (减少)
            // 反审 = CREDIT 回退
            writeLedger("CUSTOMER", customerId, currency,
                reverse ? amount : amount.negate(), reverse ? "VOID" : "ADJUST",
                "charges", chargeId, tenantId, actorName,
                reverse ? "反审应收: " + chargeId : "应收审核: 客户欠款 +" + amount);
        } else if ("AP".equals(side)) {
            // 找供应商 id (从 shipment.channel_id → channels.partner_id)
            String partnerId = findPartnerFromChannel((String) ch.get("channel_id"));
            if (partnerId == null) return;
            writeLedger("SUPPLIER", partnerId, currency,
                reverse ? amount.negate() : amount, reverse ? "VOID" : "ADJUST",
                "charges", chargeId, tenantId, actorName,
                reverse ? "反审应付: " + chargeId : "应付审核: 应付物流商 +" + amount);
        }
    }

    /** 客户收款 payments 审核 → 客户欠款减少 + 银行账户增加 */
    private void applyCustomerReceived(String pid, String tenantId, String actorName, boolean reverse) {
        Map<String, Object> p;
        try {
            p = jdbc.queryForMap("""
                SELECT customer_id::text AS customer_id, amount, currency, reference_no,
                       financial_account_id::text AS financial_account_id
                  FROM payments WHERE id = ?::uuid
                """, pid);
        } catch (EmptyResultDataAccessException ex) { return; }
        BigDecimal amount = (BigDecimal) p.get("amount");
        if (amount == null || amount.signum() <= 0) return;
        String currency = (String) p.get("currency");
        String customerId = (String) p.get("customer_id");
        String bankId = (String) p.get("financial_account_id");

        // 客户欠款减少 = 客户预扣账户 CREDIT
        if (customerId != null) {
            writeLedger("CUSTOMER", customerId, currency,
                reverse ? amount.negate() : amount, reverse ? "VOID" : "RECEIPT",
                "payments", pid, tenantId, actorName,
                reverse ? "反审收款" : "收款审核: 客户欠款 -" + amount);
        }
        // 银行账户增加
        if (bankId != null) {
            updateBankBalance(bankId, reverse ? amount.negate() : amount, tenantId,
                "payments", (String) p.get("reference_no"), actorName,
                reverse ? "反审收款" : "客户收款入账");
        }
    }

    /** 供应商付款 partner_payments 审核 → 供应商欠款减少 + 银行账户减少 */
    private void applySupplierPaid(String pid, String tenantId, String actorName, boolean reverse) {
        Map<String, Object> p;
        try {
            p = jdbc.queryForMap("""
                SELECT partner_id::text AS partner_id, amount, currency, reference_no,
                       financial_account_id::text AS financial_account_id
                  FROM partner_payments WHERE id = ?::uuid
                """, pid);
        } catch (EmptyResultDataAccessException ex) { return; }
        BigDecimal amount = (BigDecimal) p.get("amount");
        if (amount == null || amount.signum() <= 0) return;
        String currency = (String) p.get("currency");
        String partnerId = (String) p.get("partner_id");
        String bankId = (String) p.get("financial_account_id");

        if (partnerId != null) {
            writeLedger("SUPPLIER", partnerId, currency,
                reverse ? amount : amount.negate(), reverse ? "VOID" : "PAYMENT",
                "partner_payments", pid, tenantId, actorName,
                reverse ? "反审付款" : "付款审核: 应付物流商 -" + amount);
        }
        if (bankId != null) {
            updateBankBalance(bankId, reverse ? amount : amount.negate(), tenantId,
                "partner_payments", (String) p.get("reference_no"), actorName,
                reverse ? "反审付款" : "供应商付款出账");
        }
    }

    /** 找/建 owner 的影子预扣账户并写 balance_ledger. */
    private void writeLedger(String ownerType, String ownerId, String currency,
                              BigDecimal delta, String bizType,
                              String sourceType, String sourceId,
                              String tenantId, String actorName, String remark) {
        try {
            jdbc.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
            List<String> accIds = jdbc.queryForList("""
                SELECT id::text FROM financial_accounts
                 WHERE owner_type = ? AND owner_id = ?::uuid AND currency = ? LIMIT 1
                """, String.class, ownerType, ownerId, currency);
            String accountId;
            if (accIds.isEmpty()) {
                accountId = jdbc.queryForObject("""
                    INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                    account_type, currency, balance, source, is_show)
                    VALUES (?::uuid, ?, ?::uuid, ?, 'CASH', ?, 0, 'LOCAL', true)
                    RETURNING id::text
                    """, String.class, tenantId, ownerType, ownerId,
                    ownerType.equals("CUSTOMER") ? "客户预扣账户" : "供应商预扣账户", currency);
            } else {
                accountId = accIds.get(0);
            }
            BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid",
                BigDecimal.class, accountId);
            BigDecimal after = (before == null ? BigDecimal.ZERO : before).add(delta);
            jdbc.update("UPDATE financial_accounts SET balance = ?, last_update = now() WHERE id = ?::uuid",
                after, accountId);
            jdbc.update("""
                INSERT INTO balance_ledger
                  (tenant_id, account_id, owner_type, owner_id, biz_type, source_type, source_ref,
                   currency, direction, amount, balance_before, balance_after, operator, remark)
                VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type, ?, ?,
                        ?, ?::balance_ledger_direction, ?, ?, ?, ?, ?)
                """, tenantId, accountId, ownerType, ownerId, bizType,
                sourceType, sourceId, currency,
                delta.signum() >= 0 ? "CREDIT" : "DEBIT", delta.abs(),
                before == null ? BigDecimal.ZERO : before, after, actorName, remark);
        } catch (DataAccessException ex) {
            LOGGER.warn("writeLedger {} {} failed: {}", ownerType, ownerId, ex.getMessage());
        }
    }

    /** 银行账户余额联动 (无 owner 概念). */
    private void updateBankBalance(String bankId, BigDecimal delta, String tenantId,
                                    String sourceType, String sourceRef, String actorName, String remark) {
        try {
            jdbc.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
            BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid",
                BigDecimal.class, bankId);
            if (before == null) return;
            BigDecimal after = before.add(delta);
            jdbc.update("UPDATE financial_accounts SET balance = ?, last_update = now() WHERE id = ?::uuid",
                after, bankId);
            // 银行账户 owner_type 通常是 COMPANY, 余额流水 owner_id 留 NULL 或 company tenant_id
            String ownerType = jdbc.queryForObject(
                "SELECT owner_type FROM financial_accounts WHERE id = ?::uuid", String.class, bankId);
            jdbc.update("""
                INSERT INTO balance_ledger
                  (tenant_id, account_id, owner_type, owner_id, biz_type, source_type, source_ref,
                   currency, direction, amount, balance_before, balance_after, operator, remark)
                VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type, ?, ?,
                        (SELECT currency FROM financial_accounts WHERE id = ?::uuid),
                        ?::balance_ledger_direction, ?, ?, ?, ?, ?)
                """, tenantId, bankId, ownerType, tenantId, "PAY",
                sourceType, sourceRef, bankId,
                delta.signum() >= 0 ? "CREDIT" : "DEBIT", delta.abs(),
                before, after, actorName, remark);
        } catch (DataAccessException ex) {
            LOGGER.warn("updateBankBalance {} failed: {}", bankId, ex.getMessage());
        }
    }

    /** charges.AP 找供应商: 经由 shipments.channel_id → channels.bound_supplier_id */
    private String findPartnerFromChannel(String channelId) {
        if (channelId == null) return null;
        try {
            return jdbc.queryForObject(
                "SELECT bound_supplier_id::text FROM channels WHERE id = ?::uuid",
                String.class, channelId);
        } catch (DataAccessException ex) { return null; }
    }
}
