package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
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

@RestController
@RequestMapping("/api/acc/sold-tos")
public class AccSoldTosController {
    private static final String TABLE = "acc_sold_tos";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccSoldTosController(JdbcTemplate jdbc, JsonSupport json,
                                 CascadeChecker cascadeChecker, FieldGate fieldGate) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_sold_tos WHERE ?::text IS NULL OR contact_name ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT st.id::text AS id, st.customer_id::text AS customer_id,
                       st.contact_name, st.contact_mobile, st.company_name,
                       st.country, st.state, st.city, st.address, st.postcode,
                       st.is_default, st.remark,
                       c.name AS customer_name,
                       st.audit_status, st.audited_at, st.audit_name, st.created_at
                FROM acc_sold_tos st
                LEFT JOIN customers c ON c.id = st.customer_id
                WHERE ?::text IS NULL OR st.contact_name ILIKE ?
                ORDER BY st.contact_name
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_sold_tos WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String contactName = (String) body.get("contactName");
        if (contactName == null || contactName.isBlank()) {
            throw ApiException.badRequest("联系人姓名必填");
        }
        if (body.get("customerId") == null || body.get("customerId").toString().isBlank()) {
            throw ApiException.badRequest("请选择客户");
        }
        Object country = body.get("country");
        if (country != null && !country.toString().isBlank()
            && !country.toString().matches("[A-Z]{2}")) {
            throw ApiException.badRequest("国家代码必须为 ISO alpha-2 两位大写字母");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_sold_tos (tenant_id, customer_id, contact_name, contact_mobile,
                                      company_name, country, state, city, address, postcode,
                                      is_default, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?,
                    ?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text,
                    ?::boolean, ?::text)
            RETURNING id::text
            """, String.class, body.get("customerId"), contactName,
            body.get("contactMobile"), body.get("companyName"),
            body.get("country"), body.get("state"), body.get("city"),
            body.get("address"), body.get("postcode"),
            body.get("isDefault"), body.get("remark"));
        return Map.of("id", id, "contactName", contactName);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_sold_tos WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("收件地址已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_sold_tos SET
              contact_name = coalesce(?, contact_name),
              contact_mobile = coalesce(?::text, contact_mobile),
              company_name = coalesce(?::text, company_name),
              country = coalesce(?::text, country),
              state = coalesce(?::text, state),
              city = coalesce(?::text, city),
              address = coalesce(?::text, address),
              postcode = coalesce(?::text, postcode),
              is_default = coalesce(?::boolean, is_default),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("contactName"), (String) allowed.get("contactMobile"),
            (String) allowed.get("companyName"), (String) allowed.get("country"),
            (String) allowed.get("state"), (String) allowed.get("city"),
            (String) allowed.get("address"), (String) allowed.get("postcode"),
            allowed.get("isDefault"), (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_sold_tos WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("收件地址已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_sold_tos WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("contactName", row.get("contact_name"));
        out.put("contactMobile", row.get("contact_mobile"));
        out.put("companyName", row.get("company_name"));
        out.put("country", row.get("country"));
        out.put("state", row.get("state"));
        out.put("city", row.get("city"));
        out.put("address", row.get("address"));
        out.put("postcode", row.get("postcode"));
        out.put("isDefault", row.get("is_default"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
