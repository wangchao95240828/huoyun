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
        // 副作用空：配载审核入账由其它流程处理
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
