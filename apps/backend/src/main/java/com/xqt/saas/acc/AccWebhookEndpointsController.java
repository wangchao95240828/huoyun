package com.xqt.saas.acc;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/webhook-endpoints —— 出站回调地址管理（对应 ACC OnlineAPI 接口管理）。
 *
 * secret：创建时自动生成并明文返回一次；后续仅显示掩码。
 */
@RestController
@RequestMapping("/api/acc/webhook-endpoints")
public class AccWebhookEndpointsController {
    private final JdbcTemplate jdbc;
    private final SecureRandom rng = new SecureRandom();

    public AccWebhookEndpointsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM webhook_endpoints ep
            LEFT JOIN customers c ON c.id = ep.customer_id
            WHERE (?::text IS NULL OR ep.url ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
            """, Long.class, kw, kw, kw, kw);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ep.id::text AS id,
                   ep.url, ep.customer_id::text AS customer_id,
                   c.code AS customer_code, c.name AS customer_name,
                   ep.event_types, ep.active, ep.description,
                   ep.created_at,
                   '****' || right(ep.secret, 4) AS secret_masked,
                   (SELECT count(*) FROM webhook_events WHERE endpoint_id = ep.id) AS total_events,
                   (SELECT count(*) FROM webhook_events WHERE endpoint_id = ep.id AND status='DEAD') AS dead_events
            FROM webhook_endpoints ep
            LEFT JOIN customers c ON c.id = ep.customer_id
            WHERE (?::text IS NULL OR ep.url ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
            ORDER BY ep.created_at DESC
            LIMIT ? OFFSET ?
            """, kw, kw, kw, kw, limit, offset);

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String url = strOrNull(body.get("url"));
        if (url == null) throw ApiException.badRequest("Webhook URL 必填");
        if (!url.matches("https?://.+")) {
            throw ApiException.badRequest("URL 必须以 http:// 或 https:// 开头");
        }
        if (url.length() > 500) {
            throw ApiException.badRequest("URL 长度不能超过 500 字符");
        }
        String customerId = strOrNull(body.get("customerId"));
        String description = strOrNull(body.get("description"));
        List<Object> eventTypes = body.get("eventTypes") instanceof List<?> l ? (List<Object>) l
            : List.of("order.submitted", "order.cancelled", "order.tracking.updated");
        if (eventTypes.isEmpty()) {
            throw ApiException.badRequest("至少选择一个事件类型");
        }

        String secret = "whsec_" + randomToken(32);
        String id;
        try {
            id = jdbc.queryForObject("""
                INSERT INTO webhook_endpoints (tenant_id, customer_id, url, secret, event_types, description)
                SELECT t.id, ?::uuid, ?, ?, ?::jsonb, ?
                FROM tenants t WHERE t.code='xqt'
                RETURNING id::text
                """, String.class, customerId, url, secret, toJsonArray(eventTypes), description);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("创建失败：" + ex.getMostSpecificCause().getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("url", url);
        out.put("secret", secret);
        out.put("warning", "secret 明文仅本次返回，请立即保存（HMAC SHA-256 签名 X-Webhook-Signature 用）");
        return out;
    }

    @PutMapping("/{id}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String url = strOrNull(body.get("url"));
        Boolean active = body.get("active") instanceof Boolean b ? b : null;
        String description = strOrNull(body.get("description"));
        List<Object> eventTypes = body.get("eventTypes") instanceof List<?> l ? (List<Object>) l : null;
        int n = jdbc.update("""
            UPDATE webhook_endpoints SET
              url = coalesce(?, url),
              active = coalesce(?, active),
              description = coalesce(?, description),
              event_types = coalesce(?::jsonb, event_types)
            WHERE id = ?::uuid
            """, url, active, description,
            eventTypes == null ? null : toJsonArray(eventTypes), id);
        if (n == 0) throw ApiException.notFound("回调地址不存在");
        return Map.of("id", id, "updated", true);
    }

    @PostMapping("/{id}/rotate")
    public Map<String, Object> rotate(@PathVariable String id) {
        String secret = "whsec_" + randomToken(32);
        int n = jdbc.update("UPDATE webhook_endpoints SET secret = ? WHERE id = ?::uuid", secret, id);
        if (n == 0) throw ApiException.notFound("回调地址不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("secret", secret);
        out.put("warning", "secret 明文仅本次返回，请立即保存");
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM webhook_endpoints WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private String randomToken(int byteLen) {
        byte[] buf = new byte[byteLen];
        rng.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
    private static String toJsonArray(List<?> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"').append(items.get(i).toString().replace("\"", "\\\"")).append('"');
        }
        return sb.append("]").toString();
    }
    private static String strOrNull(Object o) {
        return o == null ? null : o.toString().isBlank() ? null : o.toString();
    }
}
