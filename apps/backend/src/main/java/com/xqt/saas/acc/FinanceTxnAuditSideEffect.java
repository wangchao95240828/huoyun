package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.framework.audit.AuditSideEffect;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * acc_finance_txns（调账 / 退款 / 返利）审核副作用：审核通过即入账。
 *
 * 复刻 ACC「审核通过 → 调整客户/供应商余额并落流水」：
 *   - 审核通过：按 amount 符号调整 owner 资金账户余额，写一条 balance_ledger
 *     （biz_type = ADJUST / REFUND / REBATE）
 *   - 反审核：写反向流水冲正，余额回退
 *
 * amount 语义统一为「对该 owner 余额的影响」：正数 = 增加(CREDIT)，负数 = 减少(DEBIT)。
 * 找不到 owner 资金账户时静默跳过，不阻断审核（与 AuditSideEffect 约定一致）。
 */
@Component
public class FinanceTxnAuditSideEffect implements AuditSideEffect {
    private final JdbcTemplate jdbc;
    private final com.xqt.saas.finance.FxSnapshotCapture fxCapture;

    public FinanceTxnAuditSideEffect(JdbcTemplate jdbc,
                                      com.xqt.saas.finance.FxSnapshotCapture fxCapture) {
        this.jdbc = jdbc;
        this.fxCapture = fxCapture;
    }

    @Override
    public boolean supports(String table) {
        return "acc_finance_txns".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        applyLedger(entityId, tenantId, actorName, false);
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        applyLedger(entityId, tenantId, actorName, true);
    }

    /**
     * @param reverse true = 反审冲正（金额取反，biz_type=VOID）
     */
    private void applyLedger(String entityId, String tenantId, String actorName, boolean reverse) {
        Map<String, Object> txn;
        try {
            txn = jdbc.queryForMap("""
                SELECT side, txn_type, customer_id::text AS customer_id, partner_id::text AS partner_id,
                       amount, currency, txn_no
                FROM acc_finance_txns WHERE id = ?::uuid
                """, entityId);
        } catch (EmptyResultDataAccessException ex) {
            return;
        }

        String side = (String) txn.get("side");                 // CUSTOMER / SUPPLIER
        String ownerId = "SUPPLIER".equals(side)
            ? (String) txn.get("partner_id") : (String) txn.get("customer_id");
        if (ownerId == null) return;
        String currency = (String) txn.get("currency");
        BigDecimal amount = (BigDecimal) txn.get("amount");
        if (amount == null || amount.signum() == 0) return;

        String accountId = findOwnerAccount(side, ownerId, currency);
        if (accountId == null) return;  // 无资金账户 → 跳过流水，不阻断审核

        // 审核：按 amount 符号入账；反审：取反冲正
        BigDecimal delta = reverse ? amount.negate() : amount;
        BigDecimal before = findAccountBalance(accountId);
        if (before == null) return;
        BigDecimal after = before.add(delta);

        jdbc.update("UPDATE financial_accounts SET balance = balance + ?, last_update = now() WHERE id = ?::uuid",
            delta, accountId);

        String bizType = reverse ? "VOID" : mapBizType((String) txn.get("txn_type"));
        String direction = delta.signum() >= 0 ? "CREDIT" : "DEBIT";
        String remark = (reverse ? "反审冲正：" : "审核入账：") + txn.get("txn_type");
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, owner_type, owner_id, biz_type,
              source_type, source_ref, currency, direction, amount,
              balance_before, balance_after, operator, remark
            ) VALUES (
              ?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type,
              'acc_finance_txns', ?, ?, ?::balance_ledger_direction, ?,
              ?, ?, ?, ?
            )
            """, tenantId, accountId, side, ownerId, bizType,
            txn.get("txn_no"), currency, direction, delta.abs(),
            before, after, actorName, remark);
        // 任务 S6：fx 快照覆盖（审核入账 REFUND/REBATE/VOID/ADJUST 4 类 biz_type）
        fxCapture.captureForLedger(tenantId, currency, bizType, "acc_finance_txns",
            (String) txn.get("txn_no"));
    }

    private String mapBizType(String txnType) {
        return switch (txnType == null ? "" : txnType) {
            case "REFUND" -> "REFUND";
            case "REBATE" -> "REBATE";
            case "SPONSOR" -> "SPONSOR";
            default -> "ADJUST";
        };
    }

    private String findOwnerAccount(String side, String ownerId, String currency) {
        try {
            return jdbc.queryForObject("""
                SELECT id::text FROM financial_accounts
                WHERE owner_type = ? AND owner_id = ?::uuid
                  AND (currency = ? OR ? IS NULL)
                ORDER BY (currency = ?) DESC
                LIMIT 1
                """, String.class, side, ownerId, currency, currency, currency);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private BigDecimal findAccountBalance(String accountId) {
        try {
            return jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
        } catch (DataAccessException ex) {
            return null;
        }
    }
}
