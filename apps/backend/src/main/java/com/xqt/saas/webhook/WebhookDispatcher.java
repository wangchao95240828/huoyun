package com.xqt.saas.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台调度：每 10s 扫一次 PENDING 且 next_attempt_at <= now() 的事件，
 * 取 webhook_endpoints 拿 url + secret，HMAC SHA-256 签名后 POST。
 *
 * 重试退避：30s / 5min / 30min / 2h / 12h，第 5 次失败转 DEAD。
 */
@Component
public class WebhookDispatcher {
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_SECONDS = {30, 300, 1800, 7200, 43200};
    private static final int BATCH_SIZE = 50;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    public WebhookDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 15_000L)
    public void tick() {
        try {
            List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT e.id::text         AS event_id,
                       e.payload::text    AS payload,
                       e.attempt_count    AS attempts,
                       ep.id::text        AS endpoint_id,
                       ep.url             AS url,
                       ep.secret          AS secret
                FROM webhook_events e
                JOIN webhook_endpoints ep ON ep.id = e.endpoint_id
                WHERE e.status = 'PENDING'
                  AND e.next_attempt_at <= now()
                  AND ep.active = true
                ORDER BY e.next_attempt_at
                LIMIT ?
                """, BATCH_SIZE);
            for (Map<String, Object> evt : events) {
                dispatch(evt);
            }
        } catch (DataAccessException ex) {
            System.err.println("[WebhookDispatcher] tick failed: " + ex.getMessage());
        }
    }

    private void dispatch(Map<String, Object> evt) {
        String eventId = (String) evt.get("event_id");
        String url = (String) evt.get("url");
        String payload = (String) evt.get("payload");
        String secret = (String) evt.get("secret");
        int attempts = ((Number) evt.get("attempts")).intValue();

        String signature = hmacSha256Hex(secret, payload);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", signature)
                .header("X-Webhook-Event-Id", eventId)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            String body = truncate(resp.body(), 500);

            if (code >= 200 && code < 300) {
                jdbc.update("""
                    UPDATE webhook_events SET
                      status = 'SUCCESS',
                      attempt_count = attempt_count + 1,
                      last_response_code = ?,
                      last_response_body = ?,
                      last_error = NULL,
                      delivered_at = now()
                    WHERE id = ?::uuid
                    """, code, body, eventId);
            } else {
                scheduleRetry(eventId, attempts + 1, code, body, null);
            }
        } catch (Exception ex) {
            scheduleRetry(eventId, attempts + 1, null, null,
                truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 500));
        }
    }

    private void scheduleRetry(String eventId, int newAttempts,
                                Integer code, String body, String error) {
        if (newAttempts >= MAX_ATTEMPTS) {
            jdbc.update("""
                UPDATE webhook_events SET
                  status = 'DEAD',
                  attempt_count = ?,
                  last_response_code = ?,
                  last_response_body = ?,
                  last_error = ?
                WHERE id = ?::uuid
                """, newAttempts, code, body, error, eventId);
            return;
        }
        long backoff = BACKOFF_SECONDS[Math.min(newAttempts - 1, BACKOFF_SECONDS.length - 1)];
        jdbc.update("""
            UPDATE webhook_events SET
              status = 'PENDING',
              attempt_count = ?,
              next_attempt_at = now() + (? || ' seconds')::interval,
              last_response_code = ?,
              last_response_body = ?,
              last_error = ?
            WHERE id = ?::uuid
            """, newAttempts, String.valueOf(backoff), code, body, error, eventId);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : sig) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
