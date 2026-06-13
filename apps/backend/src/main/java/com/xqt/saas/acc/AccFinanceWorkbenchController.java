package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务工作台 — 一票货的财务生命周期 3 阶段视图。
 *
 * 对应用户描述：
 *   阶段 1 「预扣明细」  Submit 制单成功 → charges 按报价扣预扣（charges.status=ESTIMATED, invoice_id=NULL）
 *   阶段 2 「待审核」     实际成本账单出来 → charges.amount 调整 → charges.status=ADJUSTED, audit_status=PENDING
 *   阶段 3 「已出账」     财务审核通过 → 关联 customer_invoices.id → 进入正式应收账单
 *
 * 三个 endpoint 分别返回三个阶段的 charges 列表，前端做 3 tab。
 */
@RestController
@RequestMapping("/api/acc/finance-workbench")
public class AccFinanceWorkbenchController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccFinanceWorkbenchController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 三个 bucket 的汇总条数 + 金额（仪表板顶部 stat card 用）。 */
    @GetMapping("/summary")
    public Map<String, Object> summary(
        @RequestParam(required = false) String customerId,
        @RequestParam(required = false) String currency
    ) {
        String custFilter = customerId == null || customerId.isBlank() ? null : customerId;
        String curFilter = currency == null || currency.isBlank() ? null : currency;

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              CASE
                WHEN invoice_id IS NULL AND status = 'ESTIMATED'::charge_status THEN 'prepay'
                WHEN invoice_id IS NULL AND status = 'ADJUSTED'::charge_status  THEN 'pending'
                WHEN invoice_id IS NOT NULL                                      THEN 'invoiced'
                ELSE 'other'
              END                                              AS bucket,
              count(*)                                         AS row_count,
              coalesce(sum(amount), 0)                         AS total_amount,
              coalesce(sum(amount - paid_amount), 0)           AS unpaid_amount
            FROM charges
            WHERE side = 'AR'
              AND status <> 'VOID'::charge_status
              AND (?::text IS NULL OR customer_id = ?::uuid)
              AND (?::text IS NULL OR currency = ?)
            GROUP BY bucket
            """, custFilter, custFilter, curFilter, curFilter);

        Map<String, Map<String, Object>> byBucket = new java.util.LinkedHashMap<>();
        for (String b : new String[]{"prepay", "pending", "invoiced"}) {
            byBucket.put(b, Map.of("count", 0, "amount", 0, "unpaid", 0));
        }
        for (Map<String, Object> r : rows) {
            String b = (String) r.get("bucket");
            if (b == null || b.equals("other")) continue;
            byBucket.put(b, Map.of(
                "count",  r.get("row_count"),
                "amount", r.get("total_amount"),
                "unpaid", r.get("unpaid_amount")
            ));
        }
        return Map.of("buckets", byBucket);
    }

    /** 预扣明细 — 客户报价已扣，尚未对账。这是用户可下载「扣款明细」的对应表。 */
    @GetMapping("/prepay-details")
    public Map<String, Object> prepayDetails(
        @RequestParam(required = false) String customerId,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        return listByStatus("ESTIMATED", customerId, currency, page, pageSize);
    }

    /** 待审核 — 实际成本已调整，等财务审核出账。 */
    @GetMapping("/pending-audit")
    public Map<String, Object> pendingAudit(
        @RequestParam(required = false) String customerId,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        return listByStatus("ADJUSTED", customerId, currency, page, pageSize);
    }

    /** 已出账 — 已关联 customer_invoices。 */
    @GetMapping("/invoiced")
    public Map<String, Object> invoiced(
        @RequestParam(required = false) String customerId,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String custFilter = customerId == null || customerId.isBlank() ? null : customerId;
        String curFilter = currency == null || currency.isBlank() ? null : currency;

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM charges ch
            WHERE ch.side = 'AR' AND ch.status <> 'VOID'::charge_status
              AND ch.invoice_id IS NOT NULL
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            """, Long.class, custFilter, custFilter, curFilter, curFilter);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ch.id::text          AS id,
              ch.amount,
              ch.paid_amount,
              ch.currency,
              ch.status::text      AS status,
              ch.audit_status,
              ch.settlement_status,
              ch.created_at,
              o.order_no           AS order_no,
              c.code               AS customer_code,
              c.name               AS customer_name,
              ci.invoice_no        AS invoice_no,
              ci.id::text          AS invoice_id
            FROM charges ch
            LEFT JOIN orders o    ON o.id  = ch.order_id
            LEFT JOIN customers c ON c.id  = ch.customer_id
            LEFT JOIN customer_invoices ci ON ci.id = ch.invoice_id
            WHERE ch.side = 'AR' AND ch.status <> 'VOID'::charge_status
              AND ch.invoice_id IS NOT NULL
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            ORDER BY ch.created_at DESC
            LIMIT ? OFFSET ?
            """, custFilter, custFilter, curFilter, curFilter, limit, offset);

        return AccPaging.result(rows, total == null ? 0 : total);
    }

    private Map<String, Object> listByStatus(String status, String customerId, String currency,
                                              Integer page, Integer pageSize) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String custFilter = customerId == null || customerId.isBlank() ? null : customerId;
        String curFilter = currency == null || currency.isBlank() ? null : currency;

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM charges ch
            WHERE ch.side = 'AR' AND ch.status = ?::charge_status
              AND ch.invoice_id IS NULL
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            """, Long.class, status, custFilter, custFilter, curFilter, curFilter);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ch.id::text          AS id,
              ch.amount,
              ch.paid_amount,
              ch.currency,
              ch.status::text      AS status,
              ch.audit_status,
              ch.settlement_status,
              ch.created_at,
              o.order_no           AS order_no,
              c.code               AS customer_code,
              c.name               AS customer_name
            FROM charges ch
            LEFT JOIN orders o    ON o.id  = ch.order_id
            LEFT JOIN customers c ON c.id  = ch.customer_id
            WHERE ch.side = 'AR' AND ch.status = ?::charge_status
              AND ch.invoice_id IS NULL
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            ORDER BY ch.created_at DESC
            LIMIT ? OFFSET ?
            """, status, custFilter, custFilter, curFilter, curFilter, limit, offset);

        return AccPaging.result(rows, total == null ? 0 : total);
    }
}
