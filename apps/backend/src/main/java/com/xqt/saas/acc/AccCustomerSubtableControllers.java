package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * P0-D2 + P0-D3 修复:
 *   /api/acc/customer-sites      — 客户多网址 (D2.1)
 *   /api/acc/customer-contacts   — 客户多联系人 (D2.2)
 *   /api/acc/customer-salesmen   — 客户↔业务员桥 (D3)
 *
 * 把 3 个 controller 合在一个文件里, 因为它们 schema/字段对齐, 重复样板减少 200 行.
 */
public class AccCustomerSubtableControllers {

    @RestController
    @RequestMapping("/api/acc/customer-sites")
    public static class Sites {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public Sites(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(@RequestParam(required = false) String customerId,
                                         @RequestParam(required = false, defaultValue = "1") Integer page,
                                         @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
            int limit = Math.max(1, Math.min(200, pageSize));
            int offset = Math.max(0, (page - 1) * limit);
            String cust = (customerId == null || customerId.isBlank()) ? null : customerId;
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM customer_sites WHERE (?::uuid IS NULL OR customer_id = ?::uuid)",
                Long.class, cust, cust);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id::text, s.customer_id::text AS customer_id,
                       c.code AS customer_code, c.name AS customer_name,
                       s.site_type, s.site_name, s.url, s.region, s.remark, s.created_at
                FROM customer_sites s
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE (?::uuid IS NULL OR s.customer_id = ?::uuid)
                ORDER BY s.created_at DESC LIMIT ? OFFSET ?
                """, cust, cust, limit, offset);
            return Map.of("data", rows.stream().map(this::project).toList(), "total", total == null ? 0 : total);
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            String customerId = str(body.getOrDefault("customer_id", body.get("customerId")));
            String url = str(body.get("url"));
            String siteType = str(body.getOrDefault("site_type", body.get("siteType")));
            if (customerId == null) throw ApiException.badRequest("customer_id 必填");
            if (url == null) throw ApiException.badRequest("url 必填");
            if (siteType == null) siteType = "OTHER";
            String id = jdbc.queryForObject("""
                INSERT INTO customer_sites (customer_id, site_type, site_name, url, region, remark)
                VALUES (?::uuid, ?, ?, ?, ?, ?) RETURNING id::text
                """, String.class, customerId, siteType,
                str(body.getOrDefault("site_name", body.get("siteName"))),
                url, str(body.get("region")), str(body.get("remark")));
            return Map.of("id", id);
        }

        @PutMapping("/{id}")
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            jdbc.update("""
                UPDATE customer_sites SET
                  site_type = coalesce(?, site_type),
                  site_name = coalesce(?, site_name),
                  url = coalesce(?, url),
                  region = coalesce(?, region),
                  remark = coalesce(?, remark)
                WHERE id = ?::uuid
                """,
                str(body.getOrDefault("site_type", body.get("siteType"))),
                str(body.getOrDefault("site_name", body.get("siteName"))),
                str(body.get("url")), str(body.get("region")), str(body.get("remark")), id);
            return Map.of("id", id);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            jdbc.update("DELETE FROM customer_sites WHERE id = ?::uuid", id);
            return Map.of("id", id, "deleted", true);
        }

        private Map<String, Object> project(Map<String, Object> r) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.get("id"));
            o.put("customerId", r.get("customer_id"));
            o.put("customerCode", r.get("customer_code"));
            o.put("customerName", r.get("customer_name"));
            o.put("siteType", r.get("site_type"));
            o.put("siteName", r.get("site_name"));
            o.put("url", r.get("url"));
            o.put("region", r.get("region"));
            o.put("remark", r.get("remark"));
            o.put("createdAt", json.value(r.get("created_at")));
            return o;
        }
    }

    @RestController
    @RequestMapping("/api/acc/customer-contacts")
    public static class Contacts {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public Contacts(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(@RequestParam(required = false) String customerId,
                                         @RequestParam(required = false, defaultValue = "1") Integer page,
                                         @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
            int limit = Math.max(1, Math.min(200, pageSize));
            int offset = Math.max(0, (page - 1) * limit);
            String cust = (customerId == null || customerId.isBlank()) ? null : customerId;
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM customer_contacts WHERE (?::uuid IS NULL OR customer_id = ?::uuid)",
                Long.class, cust, cust);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cc.id::text, cc.customer_id::text AS customer_id,
                       c.code AS customer_code, c.name AS customer_name,
                       cc.role, cc.name AS contact_name, cc.mobile, cc.phone, cc.email,
                       cc.wechat, cc.qq, cc.whatsapp, cc.skype, cc.is_default, cc.created_at
                FROM customer_contacts cc
                LEFT JOIN customers c ON c.id = cc.customer_id
                WHERE (?::uuid IS NULL OR cc.customer_id = ?::uuid)
                ORDER BY cc.is_default DESC, cc.created_at DESC LIMIT ? OFFSET ?
                """, cust, cust, limit, offset);
            return Map.of("data", rows.stream().map(this::project).toList(), "total", total == null ? 0 : total);
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            String customerId = str(body.getOrDefault("customer_id", body.get("customerId")));
            String name = str(body.get("name"));
            String role = str(body.get("role"));
            if (customerId == null) throw ApiException.badRequest("customer_id 必填");
            if (name == null) throw ApiException.badRequest("name 必填");
            if (role == null) role = "PRIMARY";
            Object isDefRaw = body.get("isDefault");
            boolean isDef = isDefRaw instanceof Boolean b && b;
            String id = jdbc.queryForObject("""
                INSERT INTO customer_contacts
                  (customer_id, role, name, mobile, phone, email, wechat, qq, whatsapp, skype, is_default)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id::text
                """, String.class, customerId, role, name,
                str(body.get("mobile")), str(body.get("phone")), str(body.get("email")),
                str(body.get("wechat")), str(body.get("qq")), str(body.get("whatsapp")),
                str(body.get("skype")), isDef);
            return Map.of("id", id);
        }

        @PutMapping("/{id}")
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            Object isDefRaw = body.get("isDefault");
            Boolean isDef = isDefRaw instanceof Boolean b ? b : null;
            jdbc.update("""
                UPDATE customer_contacts SET
                  role = coalesce(?, role), name = coalesce(?, name),
                  mobile = coalesce(?, mobile), phone = coalesce(?, phone),
                  email = coalesce(?, email), wechat = coalesce(?, wechat),
                  qq = coalesce(?, qq), whatsapp = coalesce(?, whatsapp),
                  skype = coalesce(?, skype), is_default = coalesce(?, is_default)
                WHERE id = ?::uuid
                """, str(body.get("role")), str(body.get("name")),
                str(body.get("mobile")), str(body.get("phone")), str(body.get("email")),
                str(body.get("wechat")), str(body.get("qq")), str(body.get("whatsapp")),
                str(body.get("skype")), isDef, id);
            return Map.of("id", id);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            jdbc.update("DELETE FROM customer_contacts WHERE id = ?::uuid", id);
            return Map.of("id", id, "deleted", true);
        }

        private Map<String, Object> project(Map<String, Object> r) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.get("id"));
            o.put("customerId", r.get("customer_id"));
            o.put("customerCode", r.get("customer_code"));
            o.put("customerName", r.get("customer_name"));
            o.put("role", r.get("role"));
            o.put("name", r.get("contact_name"));
            o.put("mobile", r.get("mobile"));
            o.put("phone", r.get("phone"));
            o.put("email", r.get("email"));
            o.put("wechat", r.get("wechat"));
            o.put("qq", r.get("qq"));
            o.put("whatsapp", r.get("whatsapp"));
            o.put("skype", r.get("skype"));
            o.put("isDefault", r.get("is_default"));
            o.put("createdAt", json.value(r.get("created_at")));
            return o;
        }
    }

    @RestController
    @RequestMapping("/api/acc/customer-salesmen")
    public static class Salesmen {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public Salesmen(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(@RequestParam(required = false) String customerId,
                                         @RequestParam(required = false) String employeeId,
                                         @RequestParam(required = false, defaultValue = "1") Integer page,
                                         @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
            int limit = Math.max(1, Math.min(200, pageSize));
            int offset = Math.max(0, (page - 1) * limit);
            String cust = (customerId == null || customerId.isBlank()) ? null : customerId;
            String emp = (employeeId == null || employeeId.isBlank()) ? null : employeeId;
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM customer_salesmen
                WHERE (?::uuid IS NULL OR customer_id = ?::uuid)
                  AND (?::uuid IS NULL OR employee_id = ?::uuid)
                """, Long.class, cust, cust, emp, emp);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cs.id::text, cs.customer_id::text AS customer_id,
                       c.code AS customer_code, c.name AS customer_name,
                       cs.user_id::text AS user_id, cs.employee_id::text AS employee_id,
                       e.name AS employee_name,
                       cs.role, cs.is_default, cs.start_date, cs.end_date,
                       cs.commission_share, cs.remark, cs.created_at
                FROM customer_salesmen cs
                LEFT JOIN customers c ON c.id = cs.customer_id
                LEFT JOIN acc_employees e ON e.id = cs.employee_id
                WHERE (?::uuid IS NULL OR cs.customer_id = ?::uuid)
                  AND (?::uuid IS NULL OR cs.employee_id = ?::uuid)
                ORDER BY cs.is_default DESC, cs.start_date DESC NULLS LAST
                LIMIT ? OFFSET ?
                """, cust, cust, emp, emp, limit, offset);
            return Map.of("data", rows.stream().map(this::project).toList(), "total", total == null ? 0 : total);
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            String customerId = str(body.getOrDefault("customer_id", body.get("customerId")));
            String employeeId = str(body.getOrDefault("employee_id", body.get("employeeId")));
            String userId = str(body.getOrDefault("user_id", body.get("userId")));
            String role = str(body.get("role"));
            if (customerId == null) throw ApiException.badRequest("customer_id 必填");
            if (employeeId == null && userId == null) throw ApiException.badRequest("employee_id 或 user_id 必填一个");
            if (role == null) role = "SALESMAN";
            Object isDefRaw = body.get("isDefault");
            boolean isDef = isDefRaw instanceof Boolean b && b;
            java.math.BigDecimal share = body.get("commissionShare") instanceof Number n
                ? new java.math.BigDecimal(n.toString()) : new java.math.BigDecimal("100.0");
            try {
                String id = jdbc.queryForObject("""
                    INSERT INTO customer_salesmen
                      (customer_id, user_id, employee_id, role, is_default,
                       start_date, end_date, commission_share, remark)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?::date, ?::date, ?, ?)
                    RETURNING id::text
                    """, String.class, customerId, userId, employeeId, role, isDef,
                    str(body.getOrDefault("start_date", body.get("startDate"))),
                    str(body.getOrDefault("end_date", body.get("endDate"))),
                    share, str(body.get("remark")));
                return Map.of("id", id);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                throw ApiException.badRequest("该客户已绑定此员工的同 role, 请先解绑或改时间窗");
            }
        }

        @PutMapping("/{id}")
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            Object isDefRaw = body.get("isDefault");
            Boolean isDef = isDefRaw instanceof Boolean b ? b : null;
            java.math.BigDecimal share = body.get("commissionShare") instanceof Number n
                ? new java.math.BigDecimal(n.toString()) : null;
            jdbc.update("""
                UPDATE customer_salesmen SET
                  role = coalesce(?, role), is_default = coalesce(?, is_default),
                  start_date = coalesce(?::date, start_date),
                  end_date = coalesce(?::date, end_date),
                  commission_share = coalesce(?, commission_share),
                  remark = coalesce(?, remark)
                WHERE id = ?::uuid
                """, str(body.get("role")), isDef,
                str(body.getOrDefault("start_date", body.get("startDate"))),
                str(body.getOrDefault("end_date", body.get("endDate"))),
                share, str(body.get("remark")), id);
            return Map.of("id", id);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            jdbc.update("DELETE FROM customer_salesmen WHERE id = ?::uuid", id);
            return Map.of("id", id, "deleted", true);
        }

        private Map<String, Object> project(Map<String, Object> r) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.get("id"));
            o.put("customerId", r.get("customer_id"));
            o.put("customerCode", r.get("customer_code"));
            o.put("customerName", r.get("customer_name"));
            o.put("employeeId", r.get("employee_id"));
            o.put("employeeName", r.get("employee_name"));
            o.put("userId", r.get("user_id"));
            o.put("role", r.get("role"));
            o.put("isDefault", r.get("is_default"));
            o.put("startDate", json.value(r.get("start_date")));
            o.put("endDate", json.value(r.get("end_date")));
            o.put("commissionShare", r.get("commission_share"));
            o.put("remark", r.get("remark"));
            o.put("createdAt", json.value(r.get("created_at")));
            return o;
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
