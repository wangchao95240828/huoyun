package com.xqt.saas.framework.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 注：明确 bean 名避免与 com.xqt.saas.common.AuditService（旧泛用审计日志写入服务）冲突。

/**
 * 复刻 ACC 的审核流。
 *
 * ACC 行为参照：
 *   - 业务单据建好后 audit_status = 'PENDING'
 *   - 主管点 "审核"     → 写 auditName=管理员名 / auditTime=now()，audit_status='AUDITED'
 *   - 主管点 "反审核"   → 反向，audit_status='UNAUDITED'，再编辑后回到 'PENDING'
 *   - 批量审核（订单/账单/收款常用）：传 ids 数组一次性 AUDIT
 *   - 所有动作都落 audit_events 不可变日志，便于追溯谁、何时、对哪条做了什么
 *
 * 业务层调用模式：
 *   auditService.audit("charges", chargeId, principal);
 *   auditService.undoAudit("charges", chargeId, principal);
 *   auditService.batchAudit("charges", ids, principal);
 */
@Service("accAuditService")
public class AuditService {
    private static final List<String> AUDITABLE_ENTITIES = List.of(
        "customer_invoices", "charges", "payments", "partner_payments",
        "orders", "shipments",
        "customers", "channels", "finance_currency", "partners",
        "remote_zones", "fuel_surcharge_rates", "charge_items", "organizations",
        // 022 主数据批次
        "countries", "postcodes", "hs_codes", "bank_names", "districts",
        "customer_groups", "warehouses", "return_orders",
        // 023 异常流 + 财务扩展
        "acc_collects", "acc_detains", "acc_asks", "acc_reparations",
        "acc_fees", "acc_fines",
        // 024 财务流水 + 字典
        "acc_finance_txns", "acc_expense_categories", "acc_fee_item_types",
        // 025 资金管理（banks 复用 financial_accounts）
        "acc_expenses", "financial_accounts", "acc_transfers", "acc_dividends",
        "acc_borrowings", "acc_assets", "acc_cycles", "acc_received_sms",
        // 026 HR 人事 + 提成
        "acc_employees", "acc_attendances", "acc_wages", "acc_commission_rules",
        "acc_commissions", "acc_socials", "acc_social_persons", "acc_funds", "acc_fund_persons",
        // 027 物流扩展（stowages/stowage_categories/stowage_ports 复用 020）
        "stowages", "stowage_categories", "stowage_ports",
        "acc_stowage_steps", "acc_transits", "acc_dispatches", "acc_forecasts", "acc_track_items",
        // 028 客户产品 + 系统杂项
        "acc_channel_accounts", "acc_product_items", "acc_sold_tos", "acc_potentials",
        "acc_notices", "acc_logistics_interfaces", "acc_scheduled_tasks", "acc_message_templates"
    );

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final List<AuditSideEffect> sideEffects;

    public AuditService(JdbcTemplate jdbc, JsonSupport json, List<AuditSideEffect> sideEffects) {
        this.jdbc = jdbc;
        this.json = json;
        this.sideEffects = sideEffects == null ? List.of() : sideEffects;
    }

    /** 审核/反审成功后触发关心该表的副作用（同事务，异常回滚审核）。 */
    private void fireSideEffects(String table, String entityId, String tenantId,
                                 String actorName, boolean audited) {
        for (AuditSideEffect effect : sideEffects) {
            if (effect.supports(table)) {
                if (audited) {
                    effect.onAudited(table, entityId, tenantId, actorName);
                } else {
                    effect.onUndone(table, entityId, tenantId, actorName);
                }
            }
        }
    }

    public boolean isAuditable(String table) {
        return AUDITABLE_ENTITIES.contains(table);
    }

    /** 与其它 service 一致：进入事务后第一件事是设租户上下文。 */
    private void setTenant(String tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",
            String.class, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(String table, String entityId, String tenantId, String actorName) {
        ensureAuditable(table);
        setTenant(tenantId);
        Map<String, Object> before = snapshot(table, entityId);
        if (before.isEmpty()) {
            throw ApiException.notFound("找不到该" + table + ": " + entityId);
        }
        String current = (String) before.get("audit_status");
        if ("AUDITED".equals(current)) {
            throw ApiException.badRequest("该单已经审核入账，无需重复审核");
        }
        // ACC 审核前置校验：渠道激活 / 出货明细 / 币种 / 余额等业务规则
        for (AuditSideEffect effect : sideEffects) {
            if (effect.supports(table)) {
                effect.beforeAudit(table, entityId, tenantId, actorName);
            }
        }
        int rows = jdbc.update(
            "UPDATE " + table
                + " SET audit_status = 'AUDITED', audited_at = now(), audit_name = ?"
                + " WHERE id::text = ? AND audit_status <> 'AUDITED'",
            actorName, entityId);
        if (rows == 0) {
            throw ApiException.badRequest("审核状态被并发修改，请刷新重试");
        }
        Map<String, Object> after = snapshot(table, entityId);
        recordEvent(table, entityId, AuditAction.AUDIT, actorName, before, after);
        fireSideEffects(table, entityId, tenantId, actorName, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void undoAudit(String table, String entityId, String tenantId, String actorName) {
        ensureAuditable(table);
        setTenant(tenantId);
        Map<String, Object> before = snapshot(table, entityId);
        if (before.isEmpty()) {
            throw ApiException.notFound("找不到该" + table + ": " + entityId);
        }
        String current = (String) before.get("audit_status");
        if (!"AUDITED".equals(current)) {
            throw ApiException.badRequest("该单未审核入账，无需撤销");
        }
        int rows = jdbc.update(
            "UPDATE " + table
                + " SET audit_status = 'UNAUDITED', audited_at = null, audit_name = ?"
                + " WHERE id::text = ? AND audit_status = 'AUDITED'",
            actorName, entityId);
        if (rows == 0) {
            throw ApiException.badRequest("反审核状态被并发修改，请刷新重试");
        }
        Map<String, Object> after = snapshot(table, entityId);
        recordEvent(table, entityId, AuditAction.UNDO_AUDIT, actorName, before, after);
        fireSideEffects(table, entityId, tenantId, actorName, false);
    }

    public BatchResult batchAudit(String table, List<String> entityIds, String tenantId, String actorName) {
        ensureAuditable(table);
        int success = 0;
        int skipped = 0;
        for (String id : entityIds) {
            try {
                audit(table, id, tenantId, actorName);
                success++;
            } catch (ApiException ex) {
                skipped++;
            }
        }
        return new BatchResult(entityIds.size(), success, skipped);
    }

    public List<Map<String, Object>> history(String table, String entityId, int limit) {
        return jdbc.queryForList("""
            SELECT id::text AS id, action, actor_name, occurred_at, remark, before_state, after_state
            FROM audit_events
            WHERE entity_type = ? AND entity_id = ?
            ORDER BY occurred_at DESC
            LIMIT ?
            """, table, entityId, Math.min(limit, 200));
    }

    public void recordEvent(String table, String entityId, AuditAction action,
                            String actorName, Map<String, Object> before, Map<String, Object> after) {
        // 注：tenant 上下文应在调用此方法的事务里已设。这里只 INSERT，不再读 current_setting。
        try {
            jdbc.update("""
                INSERT INTO audit_events (
                  tenant_id, entity_type, entity_id, action, actor_name,
                  before_state, after_state
                ) VALUES (
                  nullif(current_setting('app.current_tenant_id', true), '')::uuid,
                  ?, ?, ?, ?, ?::jsonb, ?::jsonb
                )
                """, table, entityId, action.name(), actorName,
                json.toJson(before), json.toJson(after));
        } catch (DataAccessException ex) {
            // 审计日志失败不阻塞业务（与 ACC 一致：审核成功是关键，日志缺失会人工补）
        }
    }

    private void ensureAuditable(String table) {
        if (!isAuditable(table)) {
            throw ApiException.badRequest("entity not auditable: " + table);
        }
    }

    private Map<String, Object> snapshot(String table, String entityId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id::text = ? LIMIT 1", entityId);
            return rows.isEmpty() ? Map.of() : new HashMap<>(rows.get(0));
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    public record BatchResult(int total, int audited, int skipped) {
    }
}
