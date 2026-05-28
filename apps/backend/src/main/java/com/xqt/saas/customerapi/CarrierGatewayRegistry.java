package com.xqt.saas.customerapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 渠道适配器注册中心。复刻 ACC `getPlugin($Code)` 模式 ——
 * 按 channels.code（或 channels.metadata->>'gateway'）路由到具体 {@link CarrierGateway} 实现。
 *
 * 路由优先级：
 *   1. channels.metadata->>'gateway' 显式指定（e.g. "UPS_DEMO" / "FEDEX_DEMO" / "NOOP"）
 *   2. channel.last_mile_method 大写匹配（UPS / FEDEX / DHL 等）
 *   3. 兜底用 noopCarrierGateway（生成 NOOP-XXX 占位）
 */
@Component
public class CarrierGatewayRegistry {
    private final Map<String, CarrierGateway> byKey = new LinkedHashMap<>();
    private final CarrierGateway fallback;
    private final JdbcTemplate jdbc;

    /** 生产应为 true：渠道必须经 acc_channel_accounts 显式配置 provider，禁止 Noop/Demo 兜底。 */
    @org.springframework.beans.factory.annotation.Value("${app.carrier.strict-gateway:false}")
    private boolean strictGateway;

    public CarrierGatewayRegistry(List<CarrierGateway> gateways,
                                  NoopCarrierGateway noopCarrierGateway,
                                  JdbcTemplate jdbc) {
        this.fallback = noopCarrierGateway;
        this.jdbc = jdbc;
        for (CarrierGateway gw : gateways) {
            byKey.put(gw.gatewayKey(), gw);
        }
    }

    @PostConstruct
    void logRegistered() {
        // 给运维一个明确的注册结果（不抛日志依赖，避免 logger 配置歧义）
        System.out.println("[CarrierGatewayRegistry] registered gateways: " + byKey.keySet());
    }

    /**
     * 按渠道路由 gateway。优先级：
     *   1. acc_channel_accounts(channel, active).provider_code（数据驱动，复刻 ACC Channel_Account.Code）
     *   2. strict 模式：未显式配置直接抛错（生产禁 Noop/Demo 兜底）
     *   3. 非 strict：last_mile_method + "_DEMO" 推断（dev 联调）
     *   4. 非 strict：Noop 兜底
     */
    public CarrierGateway forChannel(String tenantId, String channelCode) {
        // 1. 数据驱动：acc_channel_accounts.provider_code
        String provider = lookupChannelAccountProvider(tenantId, channelCode);
        if (provider != null && byKey.containsKey(provider)) {
            return byKey.get(provider);
        }

        // 2. strict（生产）：必须显式配置真实 provider，不允许 Demo/Noop 兜底
        if (strictGateway) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "渠道[" + channelCode + "]未配置可用取号接口（strict 模式禁用 Demo/Noop 兜底）");
        }

        // 3. last_mile_method 推断（仅 dev/demo）
        String lastMile = lookupLastMile(tenantId, channelCode);
        if (lastMile != null) {
            String key = lastMile.toUpperCase() + "_DEMO";
            if (byKey.containsKey(key)) return byKey.get(key);
        }
        // 4. Noop 兜底（仅 dev/demo）
        return fallback;
    }

    public List<String> registered() {
        return List.copyOf(byKey.keySet());
    }

    /** 查渠道当前生效账号配置的 provider_code（acc_channel_accounts join channels）。 */
    private String lookupChannelAccountProvider(String tenantId, String channelCode) {
        try {
            return jdbc.queryForObject("""
                SELECT a.provider_code
                FROM acc_channel_accounts a
                JOIN channels ch ON ch.id = a.channel_id
                WHERE a.tenant_id = ?::uuid AND ch.code = ?
                  AND a.is_active = true AND a.provider_code IS NOT NULL
                ORDER BY a.created_at DESC
                LIMIT 1
                """, String.class, tenantId, channelCode);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private String lookupLastMile(String tenantId, String channelCode) {
        try {
            return jdbc.queryForObject(
                "SELECT last_mile_method FROM channels WHERE tenant_id = ?::uuid AND code = ? LIMIT 1",
                String.class, tenantId, channelCode);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }
}
