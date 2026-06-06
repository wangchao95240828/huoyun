package com.xqt.saas.webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 业务发布 webhook 事件：
 *   - 找该客户下所有 active endpoint
 *   - 过滤 event_types 匹配（jsonb 数组 contains）
 *   - 每个 endpoint 入队一条 webhook_events（status=PENDING）
 *   - 后台 WebhookDispatcher 异步发货 + 重试
 *
 * 失败永远不会冒泡到业务事务（webhook 是辅助通道）。
 */
@Service
public class WebhookService {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public WebhookService(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 业务侧调用：发布事件到客户回调。失败仅打日志，不抛。 */
    public void publish(String tenantId, String customerId, String eventType,
                        Map<String, Object> payload) {
        try {
            List<Map<String, Object>> endpoints = jdbc.queryForList("""
                SELECT id::text AS id
                FROM webhook_endpoints
                WHERE tenant_id = ?::uuid
                  AND active = true
                  AND (customer_id IS NULL OR customer_id = ?::uuid)
                  AND event_types @> ?::jsonb
                """, tenantId, customerId, "[\"" + eventType + "\"]");

            if (endpoints.isEmpty()) return;
            String payloadJson = json.toJson(envelope(eventType, payload));
            for (Map<String, Object> ep : endpoints) {
                jdbc.update("""
                    INSERT INTO webhook_events
                        (tenant_id, endpoint_id, event_type, payload)
                    VALUES (?::uuid, ?::uuid, ?, ?::jsonb)
                    """, tenantId, ep.get("id"), eventType, payloadJson);
            }
        } catch (RuntimeException ex) {
            // webhook 发布失败永远不阻塞主流程
            System.err.println("[WebhookService] publish failed for event=" + eventType
                + " tenant=" + tenantId + " cause=" + ex.getMessage());
        }
    }

    private static Map<String, Object> envelope(String eventType, Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("event", eventType);
        env.put("timestamp", java.time.Instant.now().toString());
        env.put("data", payload == null ? Map.of() : payload);
        return env;
    }
}
