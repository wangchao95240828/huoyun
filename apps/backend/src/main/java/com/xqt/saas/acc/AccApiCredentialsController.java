package com.xqt.saas.acc;

import java.security.MessageDigest;
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
 * /api/acc/api-credentials —— 客户 API 凭证管理（对应 ACC CustomerAPI.php API列表）。
 *
 * 字段：access_key（公开） / secret（仅创建/轮换时返回明文一次） / owner_type+owner_id / status / scopes / expires_at / last_used_at。
 * secret_hash 永远不返回明文；轮换返回新明文，立即落 hash。
 */
@RestController
@RequestMapping("/api/acc/api-credentials")
public class AccApiCredentialsController {
    private final JdbcTemplate jdbc;
    private final SecureRandom rng = new SecureRandom();

    public AccApiCredentialsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String ownerType,
        @RequestParam(required = false) String status
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM api_credentials ac
            LEFT JOIN customers c ON c.id = ac.owner_id AND ac.owner_type='CUSTOMER'
            WHERE (?::text IS NULL OR ac.access_key ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
              AND (?::text IS NULL OR ac.owner_type = ?)
              AND (?::text IS NULL OR ac.status = ?)
            """, Long.class, kw, kw, kw, kw, ownerType, ownerType, status, status);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ac.id::text AS id,
                   ac.access_key, ac.owner_type, ac.owner_id::text AS owner_id,
                   c.code AS owner_code, c.name AS owner_name,
                   ac.status, ac.scopes, ac.remark,
                   ac.last_used_at, ac.expires_at, ac.created_at,
                   (SELECT count(*) FROM api_call_logs l WHERE l.credential_id = ac.id) AS call_count
            FROM api_credentials ac
            LEFT JOIN customers c ON c.id = ac.owner_id AND ac.owner_type='CUSTOMER'
            WHERE (?::text IS NULL OR ac.access_key ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
              AND (?::text IS NULL OR ac.owner_type = ?)
              AND (?::text IS NULL OR ac.status = ?)
            ORDER BY ac.created_at DESC
            LIMIT ? OFFSET ?
            """, kw, kw, kw, kw, ownerType, ownerType, status, status, limit, offset);

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    /** 创建凭证：自动生成 access_key + secret，secret 明文仅本次返回。 */
    @PostMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String tenantId = currentTenantId();
        String ownerType = strOr((String) body.get("ownerType"), "CUSTOMER");
        String ownerId = strOrNull(body.get("ownerId"));
        if (!java.util.List.of("CUSTOMER","TENANT","PARTNER").contains(ownerType)) {
            throw ApiException.badRequest("ownerType 必须是 CUSTOMER/TENANT/PARTNER");
        }
        if ("CUSTOMER".equals(ownerType) && ownerId == null) {
            throw ApiException.badRequest("ownerType=CUSTOMER 时 ownerId 必填");
        }
        List<Object> scopes = body.get("scopes") instanceof List<?> l ? (List<Object>) l : List.of();
        String expiresAt = strOrNull(body.get("expiresAt"));
        if (expiresAt != null && !expiresAt.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            throw ApiException.badRequest("过期时间格式应为 ISO 日期/时间");
        }
        String remark = strOrNull(body.get("remark"));

        String accessKey = "ak_" + randomToken(16);
        String secret = "sk_" + randomToken(32);
        String secretHash = sha256(secret);

        String id;
        try {
            id = jdbc.queryForObject("""
                INSERT INTO api_credentials (tenant_id, owner_type, owner_id, access_key, secret_hash, status, scopes, expires_at, remark)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, 'ACTIVE', ?::jsonb, ?::timestamptz, ?)
                RETURNING id::text
                """, String.class,
                tenantId, ownerType, ownerId, accessKey, secretHash,
                scopes.isEmpty() ? "[]" : toJsonArray(scopes),
                expiresAt, remark);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("创建失败：" + ex.getMostSpecificCause().getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("accessKey", accessKey);
        out.put("secret", secret);
        out.put("warning", "secret 明文仅本次返回，请立即保存");
        return out;
    }

    /** 允许改 status / scopes / expires_at / remark。 */
    @PutMapping("/{id}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = strOrNull(body.get("status"));
        String expiresAt = strOrNull(body.get("expiresAt"));
        String remark = strOrNull(body.get("remark"));
        List<Object> scopes = body.get("scopes") instanceof List<?> l ? (List<Object>) l : null;
        int n = jdbc.update("""
            UPDATE api_credentials SET
              status = coalesce(?, status),
              scopes = coalesce(?::jsonb, scopes),
              expires_at = coalesce(?::timestamptz, expires_at),
              remark = coalesce(?, remark)
            WHERE id = ?::uuid
            """, status, scopes == null ? null : toJsonArray(scopes), expiresAt, remark, id);
        if (n == 0) throw ApiException.notFound("凭证不存在");
        return Map.of("id", id, "updated", true);
    }

    /** 轮换 secret：生成新明文，立即落 hash，返回明文一次。 */
    @PostMapping("/{id}/rotate")
    public Map<String, Object> rotate(@PathVariable String id) {
        String secret = "sk_" + randomToken(32);
        String hash = sha256(secret);
        int n = jdbc.update("UPDATE api_credentials SET secret_hash = ? WHERE id = ?::uuid AND status <> 'REVOKED'", hash, id);
        if (n == 0) throw ApiException.badRequest("凭证不存在或已吊销");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("secret", secret);
        out.put("warning", "secret 明文仅本次返回，请立即保存");
        return out;
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT ac.id::text AS id,
                   ac.access_key, ac.owner_type, ac.owner_id::text AS ownerId,
                   c.code AS owner_code, c.name AS owner_name,
                   ac.status, ac.scopes, ac.remark,
                   ac.last_used_at, ac.expires_at, ac.created_at,
                   (SELECT count(*) FROM api_call_logs l WHERE l.credential_id = ac.id) AS call_count
            FROM api_credentials ac
            LEFT JOIN customers c ON c.id = ac.owner_id AND ac.owner_type='CUSTOMER'
            WHERE ac.id = ?::uuid
            """, id);
        return row;
    }

    @PostMapping("/{id}/reset-secret")
    public Map<String, Object> resetSecret(@PathVariable String id) {
        String secret = "sk_" + randomToken(32);
        String hash = sha256(secret);
        int n = jdbc.update("UPDATE api_credentials SET secret_hash = ? WHERE id = ?::uuid AND status <> 'REVOKED'", hash, id);
        if (n == 0) throw ApiException.badRequest("凭证不存在或已吊销");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("secret", secret);
        out.put("warning", "secret 明文仅本次返回，请立即保存");
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> revoke(@PathVariable String id) {
        jdbc.update("UPDATE api_credentials SET status = 'REVOKED' WHERE id = ?::uuid", id);
        return Map.of("id", id, "revoked", true);
    }

    // ───────────────────── helpers ─────────────────────

    private String randomToken(int byteLen) {
        byte[] buf = new byte[byteLen];
        rng.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
    private static String strOr(String v, String def) { return v == null || v.isBlank() ? def : v; }

    private String currentTenantId() {
        return jdbc.queryForObject("SELECT id::text FROM tenants WHERE code='xqt' LIMIT 1", String.class);
    }
}
