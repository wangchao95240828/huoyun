package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/customer-receivables — 应收款项目（按客户+币种聚合欠款）
 *
 * 业务规则：
 *   - 只统计 charges.side='AR' AND settlement_status <> 'VOID'
 *   - 按客户(customer_id) + 币种(currency) 双维度分组
 *   - salesman 角色只能看自己名下客户（customers.salesman_id）
 *   - 已删除客户过滤（customers.deleted_at IS NOT NULL）
 */
@RestController
@RequestMapping("/api/acc/customer-receivables")
public class AccCustomerReceivablesController {

    private final JdbcTemplate jdbc;

    public AccCustomerReceivablesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String currency
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String kw = (keyword == null || keyword.isBlank()) ? null : "%" + keyword + "%";

        // 聚合查询：按客户 + 币种双维度分组
        // 临时额度从 financial_accounts.temporary_credit 取, 余额 = 已付 + 临时额度 - 已用
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              c.code              AS code,
              c.name              AS name,
              c.contacts          AS contact_name,
              c.account_mode      AS settlement_method,
              c.default_currency  AS default_currency,
              c.credit_limit      AS credit_amount,
              ch.currency         AS currency,
              sum(ch.amount)                          AS turnover,
              sum(ch.amount - ch.paid_amount)         AS unpaid_amount,
              coalesce(fa.temporary_credit, 0)        AS temporary_credit,
              coalesce(fa.balance, 0)                 AS account_balance,
              (coalesce(fa.balance, 0) + coalesce(fa.temporary_credit, 0)) AS available_credit,
              sum(ch.amount - ch.paid_amount - coalesce(fa.balance, 0) - coalesce(fa.temporary_credit, 0)) AS estimated_balance,
              rs.last_payment_at  AS last_payment_at,
              e.name              AS salesman_name
            FROM charges ch
            JOIN customers c ON c.id = ch.customer_id AND c.deleted_at IS NULL
            LEFT JOIN financial_accounts fa
              ON fa.owner_type = 'CUSTOMER' AND fa.owner_id = c.id AND fa.currency = ch.currency
            LEFT JOIN acc_employees e ON e.id = c.salesman_id
            LEFT JOIN LATERAL (
              SELECT max(settled_at) AS last_payment_at
              FROM receivable_settlements
              WHERE customer_id = c.id AND status = 'POSTED'
            ) rs ON true
            WHERE ch.side = 'AR' AND ch.settlement_status <> 'VOID'
              AND (?::text IS NULL OR c.name ILIKE ? OR c.code ILIKE ?)
              AND (?::text IS NULL OR ch.currency = ?)
            GROUP BY c.code, c.name, c.contacts, c.account_mode,
                     c.default_currency, c.credit_limit, ch.currency,
                     fa.balance, fa.temporary_credit, rs.last_payment_at, e.name
            ORDER BY unpaid_amount DESC NULLS LAST
            LIMIT ? OFFSET ?
            """, kw, kw, kw, currency, currency, limit, offset);

        // 总数另行统计（聚合后的行数）
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM (
              SELECT 1
              FROM charges ch
              JOIN customers c ON c.id = ch.customer_id AND c.deleted_at IS NULL
              WHERE ch.side = 'AR' AND ch.settlement_status <> 'VOID'
                AND (?::text IS NULL OR c.name ILIKE ? OR c.code ILIKE ?)
                AND (?::text IS NULL OR ch.currency = ?)
              GROUP BY c.id, ch.currency
            ) sub
            """, Long.class, kw, kw, kw, currency, currency);

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    /**
     * 应收款项目明细 - 点击客户行后展示具体 charges 列表
     * GET /api/acc/customer-receivables/details?customerCode=SELLER-DEMO&currency=USD
     */
    @GetMapping("/details")
    public Map<String, Object> details(
        @RequestParam String customerCode,
        @RequestParam String currency
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ch.id::text                AS "chargeId",
              o.order_no                 AS "orderNo",
              o.customer_ref             AS "customerRef",
              o.created_at               AS "orderDate",
              ci.code                    AS "chargeItem",
              ci.name                    AS "chargeItemName",
              ch.amount                  AS "amount",
              ch.paid_amount             AS "paidAmount",
              (ch.amount - ch.paid_amount) AS "unpaid",
              ch.currency                AS "currency",
              ch.status::text            AS "status",
              ch.audit_status            AS "auditStatus",
              ch.settlement_status       AS "settlementStatus",
              ch.created_at              AS "chargedAt",
              ch.audited_at              AS "auditedAt"
            FROM charges ch
            JOIN customers c ON c.id = ch.customer_id
            LEFT JOIN orders o ON o.id = ch.order_id
            LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
            WHERE c.code = ? AND ch.currency = ?
              AND ch.side = 'AR' AND ch.settlement_status <> 'VOID'
            ORDER BY ch.created_at DESC
            LIMIT 200
            """, customerCode, currency);

        java.math.BigDecimal sumUnpaid = rows.stream()
            .map(r -> (java.math.BigDecimal) r.get("unpaid"))
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return Map.of(
            "data", rows,
            "total", rows.size(),
            "sumUnpaid", sumUnpaid,
            "customerCode", customerCode,
            "currency", currency
        );
    }
}
