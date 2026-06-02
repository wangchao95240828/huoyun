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

/**
 * /api/acc/suppliers — 前端列：name / contact / mobile / phone / product / balance / settlement
 * 来源：partners WHERE partner_type='SUPPLIER'。contact/mobile/phone/product/balance 新模型未建模，先空值。
 */
@RestController
@RequestMapping("/api/acc/suppliers")
public class AccSuppliersController {
    private static final String TABLE = "partners";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccSuppliersController(JdbcTemplate jdbc, JsonSupport json,
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

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM partners
                WHERE partner_type = 'SUPPLIER'
                  AND (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id::text AS id, p.code, p.name, p.settlement_currency, p.status,
                       p.audit_status, p.audited_at, p.audit_name,
                       p.contact_name, p.contact_mobile, p.contact_phone,
                       p.invoice_title, p.invoice_tax_no, p.bank_info_text,
                       p.contacts, p.mobile, p.fax, p.email, p.qq,
                       p.main_product, p.address, p.grade, p.credits,
                       p.settlement_type, p.date_type, p.formula_date, p.formula_bill, p.formula_type,
                       p.amount_1, p.amount_2, p.amount_3, p.amount_4, p.amount_5, p.amount_6, p.amount_7,
                       -- 关联渠道名（拼接 string_agg），从 channel_cost_policies 找承运
                       (
                         SELECT string_agg(distinct ch.name, ', ')
                         FROM channel_cost_policies ccp
                         JOIN channels ch ON ch.id = ccp.channel_id
                         JOIN carriers car ON car.id = ccp.carrier_id
                         WHERE car.id = p.carrier_id
                           AND (ccp.effective_to IS NULL OR ccp.effective_to >= current_date)
                       ) AS product_names,
                       -- AP 余额：未付的应付费用 - 已付款（partner_payments），方向上越正越欠他
                       (
                         SELECT coalesce(sum(ch.unpaid_amount), 0)
                         FROM charges ch
                         JOIN channels c2 ON c2.id IN (
                           SELECT channel_id FROM channel_cost_policies WHERE carrier_id = p.carrier_id
                         )
                         WHERE ch.side = 'AP' AND ch.audit_status = 'AUDITED'
                           AND ch.settlement_status <> 'VOID'
                       ) AS unpaid_balance
                FROM partners p
                WHERE p.partner_type = 'SUPPLIER'
                  AND (?::text IS NULL OR p.code ILIKE ? OR p.name ILIKE ?)
                ORDER BY p.code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM partners WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String contact = (String) (body.get("contact_name") != null ? body.get("contact_name") : body.get("contact"));
        String mobile = (String) (body.get("contact_mobile") != null ? body.get("contact_mobile") : body.get("mobile"));
        String phone = (String) (body.get("contact_phone") != null ? body.get("contact_phone") : body.get("phone"));
        String id = jdbc.queryForObject("""
            INSERT INTO partners (tenant_id, code, name, partner_type,
                                  contact_name, contact_mobile, contact_phone)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, 'SUPPLIER',
                    ?, ?, ?)
            RETURNING id::text
            """, String.class, code, name, contact, mobile, phone);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM partners WHERE id = ?::uuid AND partner_type='SUPPLIER'",
            String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("物流商已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE partners SET
              code           = coalesce(?, code),
              name           = coalesce(?, name),
              contact_name   = coalesce(?, contact_name),
              contact_mobile = coalesce(?, contact_mobile),
              contact_phone  = coalesce(?, contact_phone)
            WHERE id = ?::uuid AND partner_type='SUPPLIER'
            """,
            (String) allowed.get("code"),
            (String) allowed.get("name"),
            (String) (allowed.get("contact_name") != null ? allowed.get("contact_name") : allowed.get("contact")),
            (String) (allowed.get("contact_mobile") != null ? allowed.get("contact_mobile") : allowed.get("mobile")),
            (String) (allowed.get("contact_phone") != null ? allowed.get("contact_phone") : allowed.get("phone")),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM partners WHERE id = ?::uuid AND partner_type='SUPPLIER'",
            String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("物流商已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM partners WHERE id = ?::uuid AND partner_type='SUPPLIER'", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        out.put("contact", row.get("contact_name") == null ? "" : row.get("contact_name"));
        out.put("mobile", row.get("contact_mobile") == null ? "" : row.get("contact_mobile"));
        out.put("phone", row.get("contact_phone") == null ? "" : row.get("contact_phone"));
        out.put("product", row.get("product_names") == null ? "" : row.get("product_names"));
        out.put("balance", row.get("unpaid_balance"));
        out.put("settlement", row.get("settlement_currency"));
        out.put("invoiceTitle", row.get("invoice_title"));
        out.put("invoiceTaxNo", row.get("invoice_tax_no"));
        out.put("bankInfo", row.get("bank_info_text"));
        // ACC 通用 + 物流商 credits
        out.put("contacts", row.get("contacts"));
        out.put("companyMobile", row.get("mobile"));
        out.put("fax", row.get("fax"));
        out.put("email", row.get("email"));
        out.put("qq", row.get("qq"));
        out.put("mainProduct", row.get("main_product"));
        out.put("companyAddress", row.get("address"));
        out.put("grade", row.get("grade"));
        out.put("credits", row.get("credits"));
        out.put("settlementType", row.get("settlement_type"));
        out.put("dateType", row.get("date_type"));
        out.put("formulaDate", row.get("formula_date"));
        out.put("formulaBill", row.get("formula_bill"));
        out.put("formulaType", row.get("formula_type"));
        out.put("amount1", row.get("amount_1"));
        out.put("amount2", row.get("amount_2"));
        out.put("amount3", row.get("amount_3"));
        out.put("amount4", row.get("amount_4"));
        out.put("amount5", row.get("amount_5"));
        out.put("amount6", row.get("amount_6"));
        out.put("amount7", row.get("amount_7"));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
