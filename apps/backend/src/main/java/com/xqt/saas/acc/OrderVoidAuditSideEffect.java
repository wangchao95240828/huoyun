package com.xqt.saas.acc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xqt.saas.framework.audit.AuditSideEffect;

/**
 * 订单作废审核副作用：对照 ACC 制单中心 → 作废订单 列表。
 *
 * 业务流程：
 *  1. 运营在订单上点「申请作废」→ orders.audit_status='PENDING'，订单进入"作废待审"队列
 *  2. 财务/管理员在"作废订单" tab 点「审核」→ AuditService.audit("orders", id, ...)
 *     → 本 SideEffect 触发 → orders.status='CANCELLED'
 *  3. 误审？点「反审」→ AuditService.undoAudit → 本 SideEffect → orders.status 回退到原值
 *
 * 反审恢复原 status：用 metadata.acc_compat.status_before_void 保存当前 status，
 * 反审时 SELECT 回来。如果没保存，默认恢复 'DRAFT'。
 */
@Component
public class OrderVoidAuditSideEffect implements AuditSideEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderVoidAuditSideEffect.class);

    private final JdbcTemplate jdbc;

    public OrderVoidAuditSideEffect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "orders".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        try {
            // 1) 保存当前 status 到 metadata，反审时恢复
            jdbc.update("""
                UPDATE orders
                SET metadata = jsonb_set(
                  coalesce(metadata, '{}'::jsonb),
                  '{acc_compat,status_before_void}',
                  to_jsonb(status::text)
                )
                WHERE id = ?::uuid AND tenant_id = ?::uuid AND status <> 'VOID'
                """, entityId, tenantId);
            // 1b) ACC 兼容: 清零 metadata.acc_compat 8 字段 (Amount/Charges/Surcharge/
            //     Additional/Discount/Paid/Cost/Profit) 对齐 ACC 老报表口径
            jdbc.update("""
                UPDATE orders
                SET metadata = metadata || jsonb_build_object(
                  'acc_compat', coalesce(metadata->'acc_compat', '{}'::jsonb) || jsonb_build_object(
                    'Amount', 0, 'Charges', 0, 'Surcharge', 0, 'Additional', 0,
                    'Discount', 0, 'Paid', 0, 'Cost', 0, 'Profit', 0,
                    'voided_at', now()::text
                  ))
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, entityId, tenantId);
            // 2) 改 status=VOID
            int n = jdbc.update("""
                UPDATE orders SET status = 'CANCELLED'
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, entityId, tenantId);
            LOGGER.info("order {} → VOID (audit_status=AUDITED), rows={}", entityId, n);

            // 3) 财务联动：未出账的 charges 标 VOID + 反向 ledger CREDIT
            voidUnInvoicedChargesAndReverseLedger(entityId, tenantId);
        } catch (DataAccessException ex) {
            LOGGER.warn("OrderVoidAuditSideEffect.onAudited failed: {}", ex.getMessage());
        }
    }

    /**
     * 订单作废通过 → 本单未出账的 charges 自动 VOID，每条都写一笔反向 ledger。
     * 已出账（关联 customer_invoice_lines）的不动 — 财务需手动反核销 / 退款。
     */
    private void voidUnInvoicedChargesAndReverseLedger(String orderId, String tenantId) {
        java.util.List<java.util.Map<String, Object>> charges = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.currency,
                   ch.customer_id::text AS customer_id, ch.status::text AS status
              FROM charges ch
             WHERE ch.order_id = ?::uuid
               AND ch.side = 'AR'
               AND ch.status IN ('ESTIMATED'::charge_status, 'ADJUSTED'::charge_status)
               AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
            """, orderId);
        if (charges.isEmpty()) return;

        for (java.util.Map<String, Object> ch : charges) {
            jdbc.update("""
                UPDATE charges SET status='VOID'::charge_status, audit_status='UNAUDITED'
                 WHERE id = ?::uuid
                """, ch.get("id"));
            // 反向 ledger（影子账户找/建 + 反向 CREDIT 回退余额）
            try {
                jdbc.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
                java.util.List<String> accIds = jdbc.queryForList("""
                    SELECT id::text FROM financial_accounts
                     WHERE owner_type='CUSTOMER' AND owner_id=?::uuid AND currency=? LIMIT 1
                    """, String.class, ch.get("customer_id"), ch.get("currency"));
                String accountId;
                if (accIds.isEmpty()) {
                    accountId = jdbc.queryForObject("""
                        INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                        account_type, currency, balance, source, is_show)
                        VALUES (?::uuid, 'CUSTOMER', ?::uuid, '客户预扣账户', 'CASH', ?, 0, 'LOCAL', true)
                        RETURNING id::text
                        """, String.class, tenantId, ch.get("customer_id"), ch.get("currency"));
                } else {
                    accountId = accIds.get(0);
                }
                java.math.BigDecimal balBefore = jdbc.queryForObject(
                    "SELECT balance FROM financial_accounts WHERE id=?::uuid",
                    java.math.BigDecimal.class, accountId);
                java.math.BigDecimal amt = (java.math.BigDecimal) ch.get("amount");
                java.math.BigDecimal balAfter = balBefore.add(amt);
                jdbc.update("UPDATE financial_accounts SET balance = ? WHERE id = ?::uuid", balAfter, accountId);
                jdbc.update("""
                    INSERT INTO balance_ledger (
                      account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                      currency, direction, amount, balance_before, balance_after, operator, remark
                    ) VALUES (
                      ?::uuid, 'CUSTOMER', ?::uuid, 'VOID'::balance_ledger_biz_type,
                      'charges', ?::uuid, ?, ?, 'CREDIT'::balance_ledger_direction,
                      ?, ?, ?, current_user, ?
                    )
                    """, accountId, ch.get("customer_id"), ch.get("id"), orderId,
                         ch.get("currency"), amt, balBefore, balAfter,
                         "订单作废自动回退（原状态 " + ch.get("status") + "）");
            } catch (Exception ex) {
                LOGGER.warn("ledger reverse failed for charge {}: {}", ch.get("id"), ex.getMessage());
            }
        }
        LOGGER.info("order {} void: {} charges → VOID + ledger reversed", orderId, charges.size());
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        try {
            // 从 metadata.status_before_void 恢复，没有则 DRAFT
            String prev = jdbc.queryForObject("""
                SELECT coalesce(metadata->'acc_compat'->>'status_before_void', 'DRAFT')
                FROM orders WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, String.class, entityId, tenantId);
            int n = jdbc.update("""
                UPDATE orders SET status = ?::text
                WHERE id = ?::uuid AND tenant_id = ?::uuid AND status = 'CANCELLED'
                """, prev == null ? "DRAFT" : prev, entityId, tenantId);
            // P0 修复: 反向恢复 charges (作废时被标 VOID, 撤销作废时还原 ESTIMATED)
            int restored = jdbc.update("""
                UPDATE charges SET status = 'ESTIMATED'::charge_status,
                                    audit_status = 'PENDING'
                WHERE order_id = ?::uuid AND status = 'VOID'::charge_status
                  AND remark LIKE '订单作废自动回退%'
                """, entityId);
            // 清掉 metadata.acc_compat 8 字段(撤销 → 8 字段值由 charges 实时算)
            jdbc.update("""
                UPDATE orders
                SET metadata = metadata #- '{acc_compat,Amount}'
                              #- '{acc_compat,Charges}'
                              #- '{acc_compat,Surcharge}'
                              #- '{acc_compat,Additional}'
                              #- '{acc_compat,Discount}'
                              #- '{acc_compat,Paid}'
                              #- '{acc_compat,Cost}'
                              #- '{acc_compat,Profit}'
                              #- '{acc_compat,voided_at}'
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, entityId, tenantId);
            LOGGER.info("order {} restored to {} (audit_status=UNAUDITED), rows={}, charges_restored={}",
                entityId, prev, n, restored);
        } catch (DataAccessException ex) {
            LOGGER.warn("OrderVoidAuditSideEffect.onUndone failed: {}", ex.getMessage());
        }
    }
}
