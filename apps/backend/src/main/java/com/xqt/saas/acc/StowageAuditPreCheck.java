package com.xqt.saas.acc;

import java.util.List;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.framework.audit.AuditSideEffect;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 配载（stowages）审核前置校验，对齐 ACC Stowage.php：
 *   L1659 找不到配载 → AuditService 已覆盖
 *   L1547/L1662 已审核 → AuditService 已覆盖
 *   L1681 配载下没有任何快件需要操作
 *   L1697 关联快件状态不正确
 *   L2490 配载暂未添加货件，请添加之后再操作
 */
@Component
public class StowageAuditPreCheck implements AuditSideEffect {
    private final JdbcTemplate jdbc;

    public StowageAuditPreCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "stowages".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        // P0-C3 修复 (ACC Stowage.php:1642-1744 doAudit 多表联动):
        try {
            // 1. shipments 推进到 IN_TRANSIT (对齐 ACC EXPRESS_SHIPMENT)
            int shipUpdated = jdbc.update("""
                UPDATE shipments SET status = 'IN_TRANSIT'::shipment_status
                WHERE id IN (SELECT shipment_id FROM cartons WHERE stowage_id = ?::uuid)
                  AND status IN ('IN_WAREHOUSE','MEASURED','BOOKED')
                """, entityId);
            // 2. 关闭 acc_asks 退件类问题件 (ACC L1738-1740: 配载审核自动关闭 Type=14 退件件)
            int asksClosed = jdbc.update("""
                UPDATE acc_asks SET status = 'CLOSED'
                WHERE shipment_id IN (SELECT shipment_id FROM cartons WHERE stowage_id = ?::uuid)
                  AND status IN ('OPEN','PENDING')
                  AND (ask_type ILIKE '%物流退件%' OR ask_type = 'RETURN')
                """, entityId);
            // 3. acc_stowage_steps 写流程节点 (如果表存在)
            try {
                jdbc.update("""
                    INSERT INTO acc_stowage_steps
                      (tenant_id, stowage_id, step_code, operator, remark, created_at)
                    VALUES (?::uuid, ?::uuid, 'AUDIT_OK', ?, ?, now())
                    """, tenantId, entityId, actorName, "配载审核通过");
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 没有 acc_stowage_steps 表就跳过
            }
        } catch (org.springframework.dao.DataAccessException ex) {
            // 副作用失败不阻断审核
        }
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        // P0-C4 修复: 配载撤销审核回退路径 (ACC Stowage.php:1749-1817 doUndo)
        try {
            // 1. shipments 退回 IN_WAREHOUSE
            jdbc.update("""
                UPDATE shipments SET status = 'IN_WAREHOUSE'::shipment_status
                WHERE id IN (SELECT shipment_id FROM cartons WHERE stowage_id = ?::uuid)
                  AND status = 'IN_TRANSIT'
                """, entityId);
            // 2. 写撤销流程
            try {
                jdbc.update("""
                    INSERT INTO acc_stowage_steps
                      (tenant_id, stowage_id, step_code, operator, remark, created_at)
                    VALUES (?::uuid, ?::uuid, 'AUDIT_UNDO', ?, ?, now())
                    """, tenantId, entityId, actorName, "配载反审核");
            } catch (org.springframework.dao.DataAccessException ignored) {}
        } catch (org.springframework.dao.DataAccessException ignored) {}
    }

    @Override
    public void beforeAudit(String table, String entityId, String tenantId, String actorName) {
        // ACC Stowage.php L1116: 配载当前状态不能跳跃到目标状态
        // 状态机：CREATED → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED
        String currentStatus;
        try {
            currentStatus = jdbc.queryForObject(
                "SELECT status::text FROM stowages WHERE id = ?::uuid", String.class, entityId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("找不到该配载");
        }
        // 配载审核要求 status 至少 PICKED_UP（已开始出货）
        if ("CREATED".equals(currentStatus) || "DRAFT".equals(currentStatus)) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "配载当前状态 " + currentStatus + "，未到达可审核阶段");
        }
        // ACC Stowage.php L1116: 状态机不能跳跃（DELIVERED 后不能回退到 IN_TRANSIT）
        if ("DELIVERED".equals(currentStatus) || "COMPLETED".equals(currentStatus)) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "配载状态为 " + currentStatus + " 终态，无法再审核（终态不可逆）");
        }

        // ACC L1681 / L2490：配载下必须挂有 cartons（通过 cartons.stowage_id 关联）
        Integer cartonCount = jdbc.queryForObject(
            "SELECT count(*) FROM cartons WHERE stowage_id = ?::uuid", Integer.class, entityId);
        if (cartonCount == null || cartonCount == 0) {
            throw ApiException.badRequest("该配载下没有任何快件，请添加之后再审核");
        }
        // ACC L1697：关联 shipments 状态必须可配载
        List<String> badShips = jdbc.queryForList("""
            SELECT s.shipment_no FROM cartons ct
              JOIN shipments s ON s.id = ct.shipment_id
             WHERE ct.stowage_id = ?::uuid
               AND s.status NOT IN ('CREATED','PICKED_UP','IN_TRANSIT')
             LIMIT 5
            """, String.class, entityId);
        if (!badShips.isEmpty()) {
            throw ApiException.badRequest(
                "以下快件状态不正确，无法配载: " + String.join(",", badShips));
        }
    }
}
