package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.framework.audit.AuditSideEffect;
import com.xqt.saas.finance.FxSnapshotCapture;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * acc_fines 罚款审核副作用：审核通过 → 写一条 FINE 流水到对方账户。
 *
 * CUSTOMER 侧（客户被我们罚）：客户预扣账户 DEBIT 该笔金额（账户余额减少 = 客户欠我们更多）
 * SUPPLIER 侧（我们罚供应商）：PARTNER 应付账户 DEBIT 该笔金额（我们应付减少）
 *
 * 反审写一条 VOID CREDIT 反向流水回滚。
 * 资金账户不存在则静默跳过，与 FinanceTxnAuditSideEffect 约定一致。
 */
@Component
public class FinesAuditSideEffect implements AuditSideEffect {
    private final JdbcTemplate jdbc;
    private final FxSnapshotCapture fxCapture;

    public FinesAuditSideEffect(JdbcTemplate jdbc, FxSnapshotCapture fxCapture) {
        this.jdbc = jdbc;
        this.fxCapture = fxCapture;
    }

    @Override
    public boolean supports(String table) {
        return "acc_fines".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        applyLedger(entityId, tenantId, actorName, false);
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        applyLedger(entityId, tenantId, actorName, true);
    }

    private void applyLedger(String entityId, String tenantId, String actorName, boolean reverse) {
        Map<String, Object> fine;
        try {
            fine = jdbc.queryForMap("""
                SELECT side, customer_id::text AS customer_id, partner_id::text AS partner_id,
                       amount, currency, fine_no
                  FROM acc_fines WHERE id = ?::uuid
                """, entityId);
        } catch (EmptyResultDataAccessException ex) { return; }

        String side = (String) fine.get("side");
        String ownerType = "SUPPLIER".equals(side) ? "PARTNER" : "CUSTOMER";
        String ownerId = "SUPPLIER".equals(side)
            ? (String) fine.get("partner_id") : (String) fine.get("customer_id");
        if (ownerId == null) return;
        String currency = (String) fine.get("currency");
        BigDecimal amount = (BigDecimal) fine.get("amount");
        if (amount == null || amount.signum() <= 0) return;

        String accountId = findOwnerAccount(ownerType, ownerId, currency);
        if (accountId == null) return;

        BigDecimal before;
        try {
            before = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
        } catch (DataAccessException ex) { return; }
        if (before == null) return;

        // 罚款一律 DEBIT（对方账户余额减少）；反审则 CREDIT 反向
        String direction = reverse ? "CREDIT" : "DEBIT";
        BigDecimal delta = reverse ? amount : amount.negate();
        BigDecimal after = before.add(delta);

        jdbc.update("UPDATE financial_accounts SET balance = balance + ?, last_update = now() WHERE id = ?::uuid",
            delta, accountId);

        String bizType = reverse ? "VOID" : "FINE";
        String remark = (reverse ? "反审罚款：" : "罚款审核入账：") + fine.get("fine_no");
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, owner_type, owner_id, biz_type,
              source_type, source_ref, currency, direction, amount,
              balance_before, balance_after, operator, remark
            ) VALUES (
              ?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type,
              'acc_fines', ?, ?, ?::balance_ledger_direction, ?,
              ?, ?, ?, ?
            )
            """, tenantId, accountId, ownerType, ownerId, bizType,
            fine.get("fine_no"), currency, direction, amount,
            before, after, actorName, remark);
        fxCapture.captureForLedger(tenantId, currency, bizType, "acc_fines",
            (String) fine.get("fine_no"));
    }

    private String findOwnerAccount(String ownerType, String ownerId, String currency) {
        try {
            return jdbc.queryForObject("""
                SELECT id::text FROM financial_accounts
                 WHERE owner_type = ? AND owner_id = ?::uuid
                   AND (currency = ? OR ? IS NULL)
                 ORDER BY (currency = ?) DESC
                 LIMIT 1
                """, String.class, ownerType, ownerId, currency, currency, currency);
        } catch (DataAccessException ex) { return null; }
    }
}
