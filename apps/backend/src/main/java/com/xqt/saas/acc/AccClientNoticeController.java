package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户公告 / 客户服务工单 — 对齐 ACC ClientNotice.php / ClientService.php。
 *
 *   GET  /api/client-notice/my              查我（客户）的公告
 *   POST /api/client-notice/{id}/read       标已读
 *   POST /api/client-notice/contact-cs      发起客服工单（写到 acc_asks）
 */
@RestController
@RequestMapping("/api/client-notice")
public class AccClientNoticeController {
    private final JdbcTemplate jdbc;

    public AccClientNoticeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String currentCustomerId(Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        try {
            String cid = jdbc.queryForObject(
                "SELECT bound_customer_id::text FROM users WHERE id = ?::uuid",
                String.class, p.userId());
            if (cid == null || cid.isBlank()) {
                throw ApiException.unauthorized("当前账号未绑定客户");
            }
            return cid;
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.unauthorized("账号无效");
        }
    }

    @GetMapping("/my")
    public Map<String, Object> myNotices(Authentication auth) {
        String cid = currentCustomerId(auth);
        // 公告 target_type=CUSTOMER 且 target_id 是自己，或 target_type=ALL
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, title, content, notice_type, target_type, target_id::text,
                   created_at,
                   (metadata->>'read_by_'||?||'_at') AS read_at
              FROM acc_notices
             WHERE (target_type = 'ALL'
                    OR (target_type = 'CUSTOMER' AND target_id = ?::uuid))
             ORDER BY created_at DESC LIMIT 50
            """, cid, cid);
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable String id, Authentication auth) {
        String cid = currentCustomerId(auth);
        int n = jdbc.update("""
            UPDATE acc_notices SET metadata =
                coalesce(metadata, '{}'::jsonb) || jsonb_build_object('read_by_'||?, now()::text)
             WHERE id = ?::uuid
            """, cid, id);
        if (n == 0) throw ApiException.notFound("找不到该公告");
        return Map.of("id", id, "read", true);
    }

    @PostMapping("/contact-cs")
    public Map<String, Object> contactCs(@RequestBody Map<String, Object> body, Authentication auth) {
        String cid = currentCustomerId(auth);
        String content = (String) body.get("content");
        String shipmentId = (String) body.get("shipmentId");
        if (content == null || content.isBlank()) {
            throw ApiException.badRequest("问题内容必填");
        }
        // 转 acc_asks
        String id = jdbc.queryForObject("""
            INSERT INTO acc_asks (
              tenant_id, shipment_id, customer_ref, content, source, ask_type, status, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, ?, ?, 'CUSTOMER_PORTAL', 'GENERAL', 'OPEN', ?
            )
            RETURNING id::text
            """, String.class,
            shipmentId == null || shipmentId.isBlank() ? null : shipmentId,
            cid, content, "customer-portal-" + cid);
        return Map.of("id", id, "submitted", true,
            "message", "工单已提交，客服会在 24 小时内联系您");
    }
}
