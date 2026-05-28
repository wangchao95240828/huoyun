package com.xqt.saas.customerapi;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务 S2：Submit 失败业务级补偿。
 *
 * 场景：渠道 provider 已成功取号 → DB 后续 insert（cartons/declarations/charges 等）失败。
 * Spring @Transactional 会回滚 DB 数据，但 provider 那边的子单号还在 ——
 * "幽灵单号"。本 service 做两件事：
 *
 *   1. 调 CarrierGateway.cancel() best-effort 主动作废 provider 单号
 *   2. 不管 cancel 成功与否，都写一条 acc_orphan_tracking_nos 留痕
 *
 * @Transactional(REQUIRES_NEW)：在外层事务回滚时，本 service 的写入仍提交。
 */
@Service
public class SubmitCompensationService {
    private final JdbcTemplate jdbc;
    private final CarrierGatewayRegistry carrierGateways;
    private final JsonSupport json;

    public SubmitCompensationService(JdbcTemplate jdbc,
                                     CarrierGatewayRegistry carrierGateways,
                                     JsonSupport json) {
        this.jdbc = jdbc;
        this.carrierGateways = carrierGateways;
        this.json = json;
    }

    /**
     * 补偿 provider 取号成功 + DB 后续失败的情况。
     * 用 REQUIRES_NEW 隔离外层正在回滚的事务，保证 orphan 表能写进去。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateOrphanTracking(String tenantId, String providerCode,
                                          String masterTrackingNo, String trackingNo,
                                          String channelCode, String orderNo, String customerRef,
                                          String reason,
                                          java.util.Map<String, Object> rawRequest,
                                          java.util.Map<String, Object> rawResponse) {
        // 1. best-effort cancel
        boolean cancelOk = false;
        Exception cancelEx = null;
        try {
            CarrierGateway gateway = carrierGateways.forChannel(tenantId, channelCode);
            cancelOk = gateway.cancel(tenantId, masterTrackingNo);
        } catch (RuntimeException ex) {
            cancelEx = ex;
        }

        // 2. 写 orphan 表，用 service-role 绕 RLS（因为外层事务可能已经 rollback，tenant 上下文丢失）
        try {
            jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
            String reqJson = rawRequest == null ? null : json.toJson(rawRequest);
            String respJson = rawResponse == null ? null : json.toJson(rawResponse);
            String cancelRespJson = cancelEx == null
                ? json.toJson(java.util.Map.of("ok", cancelOk))
                : json.toJson(java.util.Map.of("ok", false, "exception", cancelEx.getMessage()));
            jdbc.update("""
                INSERT INTO acc_orphan_tracking_nos (
                  tenant_id, carrier_master_tracking_no, carrier_tracking_no,
                  provider_code, channel_code, order_no, customer_ref, reason,
                  raw_request, raw_response,
                  cancel_attempted, cancel_success, cancel_response, cancel_attempted_at
                ) VALUES (
                  ?::uuid, ?, ?, ?, ?, ?, ?, ?,
                  coalesce(?::jsonb, '{}'::jsonb), coalesce(?::jsonb, '{}'::jsonb),
                  true, ?, coalesce(?::jsonb, '{}'::jsonb), now()
                )
                """, tenantId, masterTrackingNo, trackingNo,
                providerCode, channelCode, orderNo, customerRef, reason,
                reqJson, respJson, cancelOk, cancelRespJson);
        } catch (DataAccessException ignored) {
            // 写 orphan 表本身失败时不再抛出 —— 至少 cancel 已经尝试过。
        }
    }
}
