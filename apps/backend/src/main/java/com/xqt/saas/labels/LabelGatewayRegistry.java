package com.xqt.saas.labels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 面单 provider 注册中心，按渠道路由到具体 {@link LabelGateway} 实现。
 *
 * 路由优先级（与 CarrierGatewayRegistry 对齐，复刻 ACC Channel_Account.Code → 插件）：
 *   1. acc_channel_accounts(channel, active).provider_code（数据驱动）
 *   2. strict 模式：未配置直接抛错（生产禁 Noop 兜底）
 *   3. 非 strict：Noop 兜底
 *
 * 实例化时把所有 LabelGateway bean 收入 byKey，路由后调 print()，不动业务调用方。
 */
@Component
public class LabelGatewayRegistry {
    private final Map<String, LabelGateway> byKey = new LinkedHashMap<>();
    private final LabelGateway fallback;
    private final JdbcTemplate jdbc;

    /** 生产应为 true：渠道必须显式配置 label provider，禁止 Noop 兜底。 */
    @org.springframework.beans.factory.annotation.Value("${app.carrier.strict-gateway:false}")
    private boolean strictGateway;

    public LabelGatewayRegistry(List<LabelGateway> gateways,
                                NoopLabelGateway noop,
                                JdbcTemplate jdbc) {
        this.fallback = noop;
        this.jdbc = jdbc;
        for (LabelGateway gw : gateways) {
            byKey.put(gatewayKeyOf(gw), gw);
        }
    }

    @PostConstruct
    void logRegistered() {
        System.out.println("[LabelGatewayRegistry] registered providers: " + byKey.keySet());
    }

    /** 按渠道编码返回 label provider；与 carrier 路由保持同一份 acc_channel_accounts 配置源。 */
    public LabelGateway forChannel(String tenantId, String channelCode) {
        String provider = lookupProvider(tenantId, channelCode);
        if (provider != null && byKey.containsKey(provider)) {
            return byKey.get(provider);
        }
        if (strictGateway) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "渠道[" + channelCode + "]未配置可用面单接口（strict 模式禁用 Noop 兜底）");
        }
        return fallback;
    }

    public List<String> registered() {
        return List.copyOf(byKey.keySet());
    }

    /** 同名规则：bean 类名去掉 LabelGateway 后缀大写。SandboxLabelGateway → SANDBOX、NoopLabelGateway → NOOP。 */
    private String gatewayKeyOf(LabelGateway gw) {
        String n = gw.getClass().getSimpleName();
        if (n.endsWith("LabelGateway")) n = n.substring(0, n.length() - "LabelGateway".length());
        return n.toUpperCase();
    }

    private String lookupProvider(String tenantId, String channelCode) {
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
}
