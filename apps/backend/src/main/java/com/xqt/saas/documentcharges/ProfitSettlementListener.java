package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.stowage.ShipmentDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务收尾：监听 {@link ShipmentDeliveredEvent} 做利润结算。
 *
 * 业务语义对照 ACC `getProfitDetail.php`：
 *   gross_profit = SUM(AR charges) - SUM(AP charges)
 *   ar_amount    = SUM(charges where side='AR' and settlement_status<>'VOID')
 *   ap_amount    = SUM(charges where side='AP' and settlement_status<>'VOID')
 *
 * 结果落 {@code profit_snapshots}（migration 010 早已建表，本 listener 是真正接入业务）。
 *
 * 设计要点：
 *   1. {@code @Transactional(REQUIRES_NEW)}：与 dispatch 状态更新事务隔离；
 *      listener 失败不回滚 shipments.status=DELIVERED 的核心动作
 *   2. 已有 profit_snapshot 时不重复写（按 tenant_id + shipment_id 去重）；
 *      重派送 / 状态机重放都不会产生重复行
 *   3. DataAccessException 静默吞掉，确保配载状态机端到端不被利润计算阻断
 */
@Component
public class ProfitSettlementListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfitSettlementListener.class);

    private final JdbcTemplate jdbc;

    public ProfitSettlementListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        try {
            // 幂等：已结算过的 shipment 跳过（重派送 / 状态机重放保护）
            Integer existing = jdbc.queryForObject("""
                SELECT count(*)::int FROM profit_snapshots
                WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid
                """, Integer.class, event.tenantId(), event.shipmentId());
            if (existing != null && existing > 0) {
                LOGGER.debug("profit_snapshot already exists for shipment {}, skip", event.shipmentNo());
                return;
            }

            Map<String, Object> sums = sumCharges(event.tenantId(), event.shipmentId());
            BigDecimal ar = toBigDecimal(sums.get("ar_amount"));
            BigDecimal ap = toBigDecimal(sums.get("ap_amount"));
            BigDecimal commission = toBigDecimal(sums.get("commission_amount"));
            BigDecimal sellerCost = toBigDecimal(sums.get("seller_cost"));
            String currency = (String) sums.getOrDefault("currency", "CNY");

            BigDecimal gross = ar.subtract(ap).subtract(commission).subtract(sellerCost);

            jdbc.update("""
                INSERT INTO profit_snapshots (
                  tenant_id, shipment_id, ar_amount, ap_amount,
                  seller_cost_amount, commission_amount, gross_profit, currency,
                  metadata
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?,
                  jsonb_build_object(
                    'trigger', 'ShipmentDeliveredEvent',
                    'shipment_no', ?::text,
                    'delivered_at', ?::text
                  )
                )
                """, event.tenantId(), event.shipmentId(),
                ar, ap, sellerCost, commission, gross, currency,
                event.shipmentNo(), event.deliveredAt().toString());

            LOGGER.info("profit settled shipment={} ar={} ap={} gross={}",
                event.shipmentNo(), ar, ap, gross);
        } catch (DataAccessException ex) {
            LOGGER.warn("profit settlement failed for shipment {}: {}",
                event.shipmentNo(), ex.getMessage());
            // 静默：利润结算失败不阻断配载状态推进
        }
    }

    /**
     * 聚合 charges 行：AR / AP / commission / seller_cost。
     *   commission_amount 来自 charges.evidence -> commission（FREIGHT AR 行存放佣金证据，不独立落行）
     *   seller_cost 暂未独立维护，先 0；ACC `Sellr_Cost` 字段后续接入
     */
    private Map<String, Object> sumCharges(String tenantId, String shipmentId) {
        return jdbc.queryForMap("""
            SELECT
              coalesce(sum(case when side = 'AR' and settlement_status <> 'VOID' then amount else 0 end), 0)
                AS ar_amount,
              coalesce(sum(case when side = 'AP' and settlement_status <> 'VOID' then amount else 0 end), 0)
                AS ap_amount,
              coalesce(sum(case when side = 'AR' and settlement_status <> 'VOID'
                               and evidence ? 'commission'
                          then (evidence->>'commission')::numeric else 0 end), 0)
                AS commission_amount,
              0::numeric AS seller_cost,
              coalesce(max(currency), 'CNY') AS currency
            FROM charges
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid
            """, tenantId, shipmentId);
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(o.toString());
    }
}
