package com.xqt.saas.acc;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.PasswordHasher;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
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
 * /api/acc/customer-logins — ACC CustomerLogin.php 对齐
 *
 * 客户用 username + password 登录 customer-portal.html, 自己查快件/账单.
 * 跟 admin users 不同 — 此 tab 由内部 admin 维护客户登录账号.
 *
 * 创建/重置密码: 库里只存 BCrypt hash, 明文密码一次性返回, 用户必须立即抄走.
 */
@RestController
@RequestMapping("/api/acc/customer-logins")
public class AccCustomerLoginsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final PasswordHasher passwordHasher;
    private static final SecureRandom RNG = new SecureRandom();

    public AccCustomerLoginsController(JdbcTemplate jdbc, JsonSupport json, PasswordHasher passwordHasher) {
        this.jdbc = jdbc;
        this.json = json;
        this.passwordHasher = passwordHasher;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String statusFilter = (status == null || status.isBlank()) ? null : status;
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM customer_logins l
                LEFT JOIN customers c ON c.id = l.customer_id
                WHERE (?::text IS NULL OR l.username ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
                  AND (?::text IS NULL OR l.status = ?)
                """, Long.class, search, search, search, search, statusFilter, statusFilter);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.id::text AS id,
                       l.username, l.status,
                       l.customer_id::text AS customer_id,
                       c.code AS customer_code,
                       c.name AS customer_name,
                       l.last_login_at, l.last_login_ip,
                       l.remark, l.created_at,
                       l.audit_status, l.audited_at, l.audit_name
                FROM customer_logins l
                LEFT JOIN customers c ON c.id = l.customer_id
                WHERE (?::text IS NULL OR l.username ILIKE ? OR c.name ILIKE ? OR c.code ILIKE ?)
                  AND (?::text IS NULL OR l.status = ?)
                ORDER BY l.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, search, statusFilter, statusFilter, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, username, status, customer_id, last_login_at, last_login_ip, remark, created_at FROM customer_logins WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    /** body = { customerId, username, remark? }. 创建并返回一次性明文密码. */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String customerId = strOrNull(body.getOrDefault("customer_id", body.get("customerId")));
        if (customerId == null) throw ApiException.badRequest("customer_id 必填");
        String username = strOrNull(body.get("username"));
        if (username == null || username.length() < 3) {
            throw ApiException.badRequest("用户名至少 3 个字符");
        }
        Integer custOk = jdbc.queryForObject(
            "SELECT count(*) FROM customers WHERE id = ?::uuid", Integer.class, customerId);
        if (custOk == null || custOk == 0) {
            throw ApiException.badRequest("找不到指定的客户: " + customerId);
        }
        Integer dup = jdbc.queryForObject(
            "SELECT count(*) FROM customer_logins WHERE username = ?", Integer.class, username);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("用户名已存在: " + username);
        }
        String plain = generatePassword();
        String hash = passwordHasher.hash(plain);
        String id = jdbc.queryForObject("""
            INSERT INTO customer_logins (tenant_id, customer_id, username, password_hash, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?)
            RETURNING id::text
            """, String.class, customerId, username, hash, strOrNull(body.get("remark")));
        // 一次性返回明文密码, 调用方必须当场抄走
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("username", username);
        out.put("password", plain);
        out.put("warning", "明文密码仅此一次返回, 请立即交给客户. 后续重置见 /reset-password.");
        return out;
    }

    /** 编辑: 只能改 remark 和 status (ACTIVE/LOCKED/DISABLED) */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = strOrNull(body.get("status"));
        if (status != null && !List.of("ACTIVE","LOCKED","DISABLED").contains(status)) {
            throw ApiException.badRequest("status 必须是 ACTIVE/LOCKED/DISABLED");
        }
        jdbc.update("""
            UPDATE customer_logins SET
              remark = coalesce(?, remark),
              status = coalesce(?, status),
              updated_at = now()
            WHERE id = ?::uuid
            """,
            strOrNull(body.get("remark")), status, id);
        return Map.of("id", id);
    }

    /** 重置密码: 生成新明文 + hash, 返回明文一次. */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable String id) {
        Integer exists = jdbc.queryForObject(
            "SELECT count(*) FROM customer_logins WHERE id = ?::uuid", Integer.class, id);
        if (exists == null || exists == 0) {
            throw ApiException.badRequest("登陆号不存在: " + id);
        }
        String plain = generatePassword();
        String hash = passwordHasher.hash(plain);
        jdbc.update(
            "UPDATE customer_logins SET password_hash = ?, status = 'ACTIVE', updated_at = now() WHERE id = ?::uuid",
            hash, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("password", plain);
        out.put("warning", "新密码仅此一次返回, 请立即交给客户.");
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM customer_logins WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /** 生成 12 位 URL-safe base64 默认密码 (~72 bit entropy) */
    private static String generatePassword() {
        byte[] bytes = new byte[9];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String strOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("username", row.get("username"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerCode", row.get("customer_code"));
        out.put("customerName", row.get("customer_name"));
        out.put("status", row.get("status"));
        out.put("lastLoginAt", json.value(row.get("last_login_at")));
        out.put("lastLoginIp", row.get("last_login_ip"));
        out.put("remark", row.get("remark"));
        out.put("createdAt", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
