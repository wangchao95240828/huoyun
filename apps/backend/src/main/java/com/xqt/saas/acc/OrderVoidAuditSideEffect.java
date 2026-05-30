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
            // 2) 改 status=VOID
            int n = jdbc.update("""
                UPDATE orders SET status = 'CANCELLED'
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, entityId, tenantId);
            LOGGER.info("order {} → VOID (audit_status=AUDITED), rows={}", entityId, n);
        } catch (DataAccessException ex) {
            LOGGER.warn("OrderVoidAuditSideEffect.onAudited failed: {}", ex.getMessage());
        }
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
            LOGGER.info("order {} restored to {} (audit_status=UNAUDITED), rows={}", entityId, prev, n);
        } catch (DataAccessException ex) {
            LOGGER.warn("OrderVoidAuditSideEffect.onUndone failed: {}", ex.getMessage());
        }
    }
}
