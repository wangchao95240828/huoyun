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

    /** 按渠道编码（channels.code）查 gateway，没找到走 fallback。 */
    public CarrierGateway forChannel(String tenantId, String channelCode) {
        // 1. 显式 metadata 指定
        String explicit = lookupMetadataGateway(tenantId, channelCode);
        if (explicit != null && byKey.containsKey(explicit)) {
            return byKey.get(explicit);
        }
        // 2. last_mile_method 匹配
        String lastMile = lookupLastMile(tenantId, channelCode);
        if (lastMile != null) {
            String key = lastMile.toUpperCase() + "_DEMO";
            if (byKey.containsKey(key)) return byKey.get(key);
        }
        // 3. fallback
        return fallback;
    }

    public List<String> registered() {
        return List.copyOf(byKey.keySet());
    }

    private String lookupMetadataGateway(String tenantId, String channelCode) {
        try {
            // channels 表暂未定义 metadata 列；这里用一个空 SELECT 兜底，
            // 后续如要 metadata.gateway 路由，alter table 加列即可。
            return null;
        } catch (RuntimeException ex) {
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
