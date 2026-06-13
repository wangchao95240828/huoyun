package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.framework.audit.AuditSideEffect;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * shipments 审核前置校验，对齐 ACC Shipment.php Save 流的业务规则：
 *   L1116: 找不到该出货 → AuditService 已 throw notFound
 *   L1119: 已审核入账，无需重复审核 → AuditService 已 throw
 *   L1122: 找不到绑定的出货渠道
 *   L1125: 出货渠道未启用
 *   L1162: 部分出货清单关联的货物不存在
 *   L1168: 部分出货清单关联的货物运输状态不正确
 *   L1220: 出货总单没有包含任何快件信息
 */
@Component
public class ShipmentAuditPreCheck implements AuditSideEffect {
    private final JdbcTemplate jdbc;

    public ShipmentAuditPreCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "shipments".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        // 副作用为空：审核业务逻辑由现有 financeWorkbench / settlementWorkbench 处理
    }

    @Override
    public void beforeAudit(String table, String entityId, String tenantId, String actorName) {
        Map<String, Object> ship;
        try {
            ship = jdbc.queryForMap("""
                SELECT channel_id::text AS channel_id, status::text AS status,
                       shipment_no
                  FROM shipments WHERE id = ?::uuid
                """, entityId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该出货单");
        }
        String channelId = (String) ship.get("channel_id");
        if (channelId == null) {
            throw ApiException.badRequest("该出货单找不到绑定的出货渠道");
        }
        Boolean channelActive = jdbc.queryForObject(
            "SELECT active FROM channels WHERE id = ?::uuid", Boolean.class, channelId);
        if (channelActive == null || !channelActive) {
            throw ApiException.badRequest("该出货单绑定的出货渠道未启用");
        }
        // 出货明细必须存在（cartons or shipment_items）
        Integer cartonCount = jdbc.queryForObject(
            "SELECT count(*) FROM cartons WHERE shipment_id = ?::uuid", Integer.class, entityId);
        if (cartonCount == null || cartonCount == 0) {
            throw ApiException.badRequest("该出货总单没有包含任何快件信息，无法审核");
        }
        // 关联的 order 状态校验：所有 link 的 order 都必须 SUBMITTED+ (不能是 DRAFT 或 CANCELLED)
        List<String> badOrderNos = jdbc.queryForList("""
            SELECT o.order_no FROM orders o
              JOIN shipment_order_links sol ON sol.order_id = o.id
             WHERE sol.shipment_id = ?::uuid
               AND o.status IN ('DRAFT','CANCELLED','EXCEPTION')
            """, String.class, entityId);
        if (!badOrderNos.isEmpty()) {
            throw ApiException.badRequest(
                "部分关联订单状态不正确，无法审核: " + String.join(",", badOrderNos));
        }
    }
}
