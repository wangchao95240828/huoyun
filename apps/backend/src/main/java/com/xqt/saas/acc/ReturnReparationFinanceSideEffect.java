package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.framework.audit.AuditSideEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 退件 / 赔偿 审核通过 → 财务联动。
 *
 * 退件 (return_orders.audit_status='AUDITED')：
 *   - 找到关联 shipment_id 对应客户 + 原始 charges
 *   - 写 balance_ledger REFUND CREDIT，金额 = refund_amount
 *   - 若 compensate_amount > 0，再写 COMPENSATE 一笔
 *
 * 赔偿 (acc_reparations.audit_status='AUDITED')：
 *   - 写 balance_ledger COMPENSATE CREDIT，金额 = apply_amount
 *
 * 反审则反向冲账。
 */
@Component
public class ReturnReparationFinanceSideEffect implements AuditSideEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReturnReparationFinanceSideEffect.class);
    private final JdbcTemplate jdbc;

    public ReturnReparationFinanceSideEffect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "return_orders".equals(table) || "acc_reparations".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        apply(table, entityId, tenantId, false);
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        apply(table, entityId, tenantId, true);
    }

    private void apply(String table, String entityId, String tenantId, boolean reverse) {
        try {
            if ("return_orders".equals(table)) {
                applyReturn(entityId, tenantId, reverse);
            } else if ("acc_reparations".equals(table)) {
                applyReparation(entityId, tenantId, reverse);
            }
        } catch (DataAccessException ex) {
            LOGGER.warn("{} side effect failed ({}): {}", table, reverse ? "undo" : "audit", ex.getMessage());
        }
    }

    private void applyReturn(String returnId, String tenantId, boolean reverse) {
        Map<String, Object> ret;
        try {
            ret = jdbc.queryForMap("""
                SELECT r.refund_amount, r.compensate_amount, r.currency, r.return_no,
                       s.customer_id::text AS customer_id
                  FROM return_orders r
                  JOIN shipments s ON s.id = r.original_shipment_id
                 WHERE r.id = ?::uuid AND r.tenant_id = ?::uuid
                """, returnId, tenantId);
        } catch (DataAccessException ex) {
            LOGGER.warn("return_orders not found or missing shipment: {}", returnId);
            return;
        }
        String customerId = (String) ret.get("customer_id");
        if (customerId == null) return;
        String currency = (String) ret.get("currency");
        BigDecimal refund = (BigDecimal) ret.get("refund_amount");
        BigDecimal compensate = (BigDecimal) ret.get("compensate_amount");

        if (refund != null && refund.signum() > 0) {
            writeLedger(customerId, currency,
                refund, reverse ? "DEBIT" : "CREDIT",
                reverse ? "VOID" : "REFUND",
                "return_orders", returnId, (String) ret.get("return_no"), tenantId,
                (reverse ? "反审退件退款" : "退件审核通过退款") + " " + ret.get("return_no"));
        }
        if (compensate != null && compensate.signum() > 0) {
            writeLedger(customerId, currency,
                compensate, reverse ? "DEBIT" : "CREDIT",
                reverse ? "VOID" : "COMPENSATE",
                "return_orders", returnId, (String) ret.get("return_no"), tenantId,
                (reverse ? "反审退件补偿" : "退件审核通过补偿") + " " + ret.get("return_no"));
        }
    }

    private void applyReparation(String repId, String tenantId, boolean reverse) {
        Map<String, Object> rep;
        try {
            rep = jdbc.queryForMap("""
                SELECT r.apply_amount, r.currency, r.customer_ref,
                       s.customer_id::text AS customer_id
                  FROM acc_reparations r
                  LEFT JOIN shipments s ON s.id = r.shipment_id
                 WHERE r.id = ?::uuid AND r.tenant_id = ?::uuid
                """, repId, tenantId);
        } catch (DataAccessException ex) {
            LOGGER.warn("acc_reparations not found: {}", repId);
            return;
        }
        String customerId = (String) rep.get("customer_id");
        if (customerId == null) {
            // 兜底：找不到 shipment 关联客户，跳过
            LOGGER.warn("reparation {} missing shipment customer_id", repId);
            return;
        }
        BigDecimal amount = (BigDecimal) rep.get("apply_amount");
        if (amount == null || amount.signum() <= 0) return;

        writeLedger(customerId, (String) rep.get("currency"),
            amount, reverse ? "DEBIT" : "CREDIT",
            reverse ? "VOID" : "COMPENSATE",
            "acc_reparations", repId, (String) rep.get("customer_ref"), tenantId,
            (reverse ? "反审赔偿" : "赔偿审核通过"));
    }

    /** 简化版的 writeLedger：自动找/建影子账户 + 写 balance_ledger。 */
    private void writeLedger(String customerId, String currency, BigDecimal amount,
                              String direction, String bizType,
                              String sourceType, String sourceId, String sourceRef,
                              String tenantId, String remark) {
        try {
            jdbc.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
            List<String> accIds = jdbc.queryForList("""
                SELECT id::text FROM financial_accounts
                 WHERE owner_type='CUSTOMER' AND owner_id=?::uuid AND currency=? LIMIT 1
                """, String.class, customerId, currency);
            String accountId;
            if (accIds.isEmpty()) {
                accountId = jdbc.queryForObject("""
                    INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                    account_type, currency, balance, source, is_show)
                    VALUES (?::uuid, 'CUSTOMER', ?::uuid, '客户预扣账户', 'CASH', ?, 0, 'LOCAL', true)
                    RETURNING id::text
                    """, String.class, tenantId, customerId, currency);
            } else {
                accountId = accIds.get(0);
            }
            BigDecimal balBefore = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id=?::uuid", BigDecimal.class, accountId);
            BigDecimal signed = "DEBIT".equals(direction) ? amount.negate() : amount;
            BigDecimal balAfter = balBefore.add(signed);
            jdbc.update("UPDATE financial_accounts SET balance = ? WHERE id = ?::uuid", balAfter, accountId);
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'CUSTOMER', ?::uuid, ?::balance_ledger_biz_type,
                  ?, ?::uuid, ?, ?, ?::balance_ledger_direction,
                  ?, ?, ?, current_user, ?
                )
                """, accountId, customerId, bizType, sourceType, sourceId, sourceRef,
                     currency, direction, amount, balBefore, balAfter, remark);
        } catch (Exception ex) {
            LOGGER.warn("writeLedger failed: {}", ex.getMessage());
        }
    }
}
