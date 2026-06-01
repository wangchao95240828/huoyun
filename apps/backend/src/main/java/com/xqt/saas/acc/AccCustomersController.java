package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
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

/**
 * /api/acc/customers — 对应前端 ACC tab "customers"。读 customers 表，
 * 把 DB 字段映射到前端列名（contact/mobile/balance/settlement/branch/group/salesman
 * 都是新平台没建模的字段，先返回空值）。
 */
@RestController
@RequestMapping("/api/acc/customers")
public class AccCustomersController {
    private static final String TABLE = "customers";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccCustomersController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
            this.branchAccess = branchAccess;
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

            var access = branchAccess.forCustomers("c");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(search, search, search));
            countParams.addAll(access.params());
            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM customers c"
                + " WHERE (?::text IS NULL OR (c.code ILIKE ? OR c.name ILIKE ?))"
                + access.sql(),
                Long.class, countParams.toArray())) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id::text AS id, c.code, c.name, c.default_currency, c.account_mode,
                       c.credit_limit, c.created_at,
                       c.login_no, c.api_key,
                       c.audit_status, c.audited_at, c.audit_name,
                       org.name AS branch_name,
                       grp.name AS group_name,
                       u.display_name AS salesman_name,
                       -- 主联系人（is_primary 优先；否则取第一条）
                       (
                         SELECT cc.name FROM customer_contacts cc
                         WHERE cc.customer_id = c.id
                         ORDER BY cc.is_primary DESC, cc.created_at
                         LIMIT 1
                       ) AS contact_name,
                       (
                         SELECT cc.phone FROM customer_contacts cc
                         WHERE cc.customer_id = c.id AND cc.phone IS NOT NULL
                         ORDER BY cc.is_primary DESC, cc.created_at
                         LIMIT 1
                       ) AS contact_phone,
                       -- 结算方式：customer_settlement_profiles.pay_type 当下有效项
                       (
                         SELECT sp.pay_type FROM customer_settlement_profiles sp
                         WHERE sp.customer_id = c.id
                           AND sp.effective_from <= current_date
                           AND (sp.effective_to IS NULL OR sp.effective_to >= current_date)
                         ORDER BY sp.effective_from DESC LIMIT 1
                       ) AS settlement_paytype,
                       -- 余额：财务账户 owner_type='CUSTOMER' owner_id = customer.id 累加
                       (
                         SELECT coalesce(sum(fa.balance), 0) FROM financial_accounts fa
                         WHERE fa.owner_type = 'CUSTOMER' AND fa.owner_id = c.id
                       ) AS balance_amount
                FROM customers c
                LEFT JOIN organizations org ON org.id = c.branch_id
                LEFT JOIN customer_groups grp ON grp.id = c.customer_group_id
                LEFT JOIN users u ON u.id = c.salesman_user_id
                WHERE 1=1
                """
                + " AND (?::text IS NULL OR (c.code ILIKE ? OR c.name ILIKE ?))"
                + access.sql()
                + " ORDER BY c.code LIMIT ? OFFSET ?",
                buildCustomersListParams(search, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM customers WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String id = jdbc.queryForObject("""
            INSERT INTO customers (tenant_id, code, name)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?)
            RETURNING id::text
            """, String.class, code, name);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM customers WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("客户已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE customers SET
              code = coalesce(?, code),
              name = coalesce(?, name)
            WHERE id = ?::uuid
            """, (String) allowed.get("code"), (String) allowed.get("name"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM customers WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("客户已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM customers WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private static Object[] buildCustomersListParams(String search,
                                                       BranchAccessFilter.AccessClause access,
                                                       int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(search, search, search));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        // 前端列：contact, mobile, balance, credits, settlement, branch, group, salesman
        out.put("contact", row.get("contact_name") == null ? "" : row.get("contact_name"));
        out.put("mobile", row.get("contact_phone") == null ? "" : row.get("contact_phone"));
        out.put("balance", row.get("balance_amount"));
        out.put("credits", row.get("credit_limit"));
        // 结算方式优先用 customer_settlement_profiles.pay_type，回退到 customers.account_mode
        out.put("settlement", row.get("settlement_paytype") == null
            ? row.get("account_mode") : row.get("settlement_paytype"));
        out.put("branch", row.get("branch_name") == null ? "" : row.get("branch_name"));
        out.put("group", row.get("group_name") == null ? "" : row.get("group_name"));
        out.put("salesman", row.get("salesman_name") == null ? "" : row.get("salesman_name"));
        out.put("loginNo", row.get("login_no"));
        out.put("apiKey", row.get("api_key"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
