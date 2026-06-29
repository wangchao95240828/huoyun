package com.xqt.saas.acc;

import java.math.BigDecimal;
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
 * /api/acc/bills — 前端列：no / customerName / settlement / theDate / amount / paid / unpay
 * / quantity / status / salesman
 *
 * 来源：customer_invoices + customers join。paid 由 payments.amount 聚合（按 reference_no=invoice_no）。
 */
@RestController
@RequestMapping("/api/acc/bills")
public class AccBillsController {
    private static final String TABLE = "customer_invoices";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final com.xqt.saas.documentcharges.DocumentChargeService docService;

        private final BranchAccessFilter branchAccess;

public AccBillsController(JdbcTemplate jdbc, JsonSupport json,
                              CascadeChecker cascadeChecker, FieldGate fieldGate,
                              com.xqt.saas.documentcharges.DocumentChargeService docService,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.docService = docService;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        // 多条件多选筛选
        @RequestParam(required = false) String customerIds,
        @RequestParam(required = false) String currencies,
        @RequestParam(required = false) String statuses,
        @RequestParam(required = false) String auditStatuses,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        @RequestParam(required = false) String amountFrom,
        @RequestParam(required = false) String amountTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            // 多值 adv-filter
            StringBuilder advFilter = new StringBuilder();
            java.util.List<Object> advParams = new java.util.ArrayList<>();
            java.util.List<String> cidList = AccOrdersController.splitCsv(customerIds);
            java.util.List<String> curList = AccOrdersController.splitCsv(currencies);
            java.util.List<String> stList  = AccOrdersController.splitCsv(statuses);
            java.util.List<String> auList  = AccOrdersController.splitCsv(auditStatuses);
            if (!cidList.isEmpty()) {
                advFilter.append(" AND i.customer_id::text IN (").append(AccOrdersController.qMarks(cidList.size())).append(")");
                advParams.addAll(cidList);
            }
            if (!curList.isEmpty()) {
                advFilter.append(" AND i.currency IN (").append(AccOrdersController.qMarks(curList.size())).append(")");
                advParams.addAll(curList);
            }
            if (!stList.isEmpty()) {
                advFilter.append(" AND i.status IN (").append(AccOrdersController.qMarks(stList.size())).append(")");
                advParams.addAll(stList);
            }
            if (!auList.isEmpty()) {
                advFilter.append(" AND i.audit_status IN (").append(AccOrdersController.qMarks(auList.size())).append(")");
                advParams.addAll(auList);
            }
            if (createdFrom != null && !createdFrom.isBlank()) { advFilter.append(" AND i.issued_at >= ?::date"); advParams.add(createdFrom); }
            if (createdTo   != null && !createdTo.isBlank())   { advFilter.append(" AND i.issued_at < (?::date + 1)"); advParams.add(createdTo); }
            try {
                if (amountFrom != null && !amountFrom.isBlank()) { advFilter.append(" AND i.total_amount >= ?"); advParams.add(new java.math.BigDecimal(amountFrom)); }
                if (amountTo   != null && !amountTo.isBlank())   { advFilter.append(" AND i.total_amount <= ?"); advParams.add(new java.math.BigDecimal(amountTo)); }
            } catch (NumberFormatException ignored) {}
            String advFilterSql = advFilter.toString();

            var access = branchAccess.forCurrent("i");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(advParams);
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM customer_invoices i"
                + " WHERE (?::text IS NULL OR i.invoice_no ILIKE ?)"
                + "   AND (?::date IS NULL OR i.issued_at >= ?::date)"
                + "   AND (?::date IS NULL OR i.issued_at < (?::date + 1))"
                + advFilterSql
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  i.id::text       AS id,
                  i.invoice_no,
                  i.template_code,
                  i.currency,
                  i.total_amount,
                  i.status,
                  i.issued_at,
                  i.audit_status,
                  i.audited_at,
                  i.audit_name,
                  c.name           AS customer_name,
                  c.account_mode   AS settlement,
                  u.display_name   AS salesman_name,
                  (
                    SELECT coalesce(sum(p.amount), 0) FROM payments p
                    WHERE p.tenant_id = i.tenant_id AND p.reference_no = i.invoice_no
                  ) AS paid_amount,
                  (
                    SELECT count(*) FROM customer_invoice_lines l WHERE l.invoice_id = i.id
                  ) AS line_count
                FROM customer_invoices i
                LEFT JOIN customers c ON c.id = i.customer_id
                LEFT JOIN users u ON u.id = c.salesman_user_id
                WHERE 1=1
                """
                + " AND (?::text IS NULL OR i.invoice_no ILIKE ?)"
                + " AND (?::date IS NULL OR i.issued_at >= ?::date)"
                + " AND (?::date IS NULL OR i.issued_at < (?::date + 1))"
                + advFilterSql
                + access.sql()
                + " ORDER BY i.issued_at DESC NULLS LAST, i.invoice_no LIMIT ? OFFSET ?",
                buildBillsListParamsWithAdv(search, dateFrom, dateTo, advParams, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM customer_invoices WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    /** 对应前端 "查看账单明细" — bills/{id}/items 拉 customer_invoice_lines + 关联 charges + orders. */
    @GetMapping("/{id}/items")
    public List<Map<String, Object>> items(@PathVariable String id) {
        try {
            // customer_invoice_lines schema: id/invoice_id/shipment_id/charge_id/amount (无 line_no/description)
            return jdbc.queryForList("""
                SELECT
                  cil.id::text                           AS "id",
                  o.order_no                             AS "expressNo",
                  cu.name                                AS "customerName",
                  ci.name                                AS "lineType",
                  cil.amount                             AS "amount",
                  ch.paid_amount                         AS "paid",
                  to_char(ch.created_at, 'YYYY-MM-DD')   AS "theDate"
                FROM customer_invoice_lines cil
                LEFT JOIN charges ch ON ch.id = cil.charge_id
                LEFT JOIN orders o ON o.id = ch.order_id
                LEFT JOIN customers cu ON cu.id = ch.customer_id
                LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
                WHERE cil.invoice_id = ?::uuid
                ORDER BY ch.created_at
                """, id);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String invoiceNo = (String) body.get("invoice_no");
        Object customerId = body.get("customer_id");
        String currency = (String) body.getOrDefault("currency", "CNY");
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw ApiException.badRequest("账单号必填");
        }
        if (customerId == null || customerId.toString().isBlank()) {
            throw ApiException.badRequest("请选择客户");
        }
        if (currency.length() != 3) {
            throw ApiException.badRequest("币种代码必须为 3 字母");
        }
        Integer dup = jdbc.queryForObject(
            "SELECT count(*) FROM customer_invoices WHERE invoice_no = ?", Integer.class, invoiceNo);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("账单号已存在: " + invoiceNo);
        }
        String id = jdbc.queryForObject("""
            INSERT INTO customer_invoices (
              tenant_id, customer_id, invoice_no, currency, status
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, 'DRAFT'
            )
            RETURNING id::text
            """, String.class, customerId == null ? null : customerId.toString(), invoiceNo, currency);
        return Map.of("id", id, "invoice_no", invoiceNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 1) 字段闸：审核后金额/客户/币种锁定
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM customer_invoices WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("账单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE customer_invoices SET
              invoice_no   = coalesce(?, invoice_no),
              status       = coalesce(?, status),
              total_amount = coalesce(?, total_amount)
            WHERE id = ?::uuid
            """, (String) allowed.get("invoice_no"),
            (String) allowed.get("status"),
            allowed.get("total_amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        // 级联校验：已关联收款的账单不允许删
        cascadeChecker.checkBeforeDelete(TABLE, id);
        // P0-B5 修复 (ACC CBill.php:1647-1654): 账单删除时, 必须把所有指向该账单的费用单
        // 释放回"未结算"状态. ACC 同步回写 7 张表的 Bill=0; 新模型走 charges +
        // customer_invoice_lines 桥接, 把 settlement_status 改回 UNSETTLED.
        int releasedCharges = jdbc.update("""
            UPDATE charges SET settlement_status = 'UNSETTLED'
            WHERE id IN (SELECT charge_id FROM customer_invoice_lines WHERE invoice_id = ?::uuid)
              AND settlement_status = 'SETTLED'
            """, id);
        // 删桥接行 (FK 不级联自动删, 显式清)
        jdbc.update("DELETE FROM customer_invoice_lines WHERE invoice_id = ?::uuid", id);
        jdbc.update("DELETE FROM customer_invoices WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true, "releasedCharges", releasedCharges);
    }

    /**
     * 前端 "生成账单"。统一走 documentcharges service（消除双轨：以前前端调这个但后端没实现）。
     * 入参兼容旧前端 { customerId, dateFrom, dateTo, currency? }，UUID 与 integer 都接受。
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String customerId = resolveCustomerId(body.get("customerId"));
        if (customerId == null || customerId.isBlank()) {
            return Map.of("ok", false, "error", "customerId 必填");
        }
        java.time.LocalDate from = parseDate(body.get("dateFrom"));
        java.time.LocalDate to = parseDate(body.get("dateTo"));
        // 默认: 没传日期 = 最近 30 天
        if (from == null) from = java.time.LocalDate.now().minusDays(30);
        if (to == null) to = java.time.LocalDate.now();
        String currency = (String) body.getOrDefault("currency", "CNY");
        @SuppressWarnings("unchecked")
        List<String> chargeIds = body.get("chargeIds") instanceof List ? (List<String>) body.get("chargeIds") : null;
        com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice req =
            new com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice(
                customerId, from, to, currency, "STANDARD", chargeIds);
        try {
            com.xqt.saas.documentcharges.DocumentChargeResponses.InvoiceResult r =
                docService.generateCustomerInvoice(currentPrincipal(), req);
            return Map.of("ok", true, "billId", r.invoiceId(), "invoiceNo", r.invoiceNo(),
                "lineCount", r.lineCount(), "totalAmount", r.totalAmount(),
                "dateFrom", from.toString(), "dateTo", to.toString());
        } catch (com.xqt.saas.common.ApiException ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    /**
     * 客户已生成账单状态查询 — 用于"标记已生成"显示。
     * GET /api/acc/bills/customer-billed-status?customerId=
     * 返回该客户每个币种最近一次出账日期 + 累计账单数。
     */
    @GetMapping("/customer-billed-status")
    public Map<String, Object> customerBilledStatus(
        @org.springframework.web.bind.annotation.RequestParam(required = false) String customerId,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String customerCode
    ) {
        String cust = resolveCustomerId(customerId);
        if (cust == null && customerCode != null) {
            try {
                cust = jdbc.queryForObject(
                    "SELECT id::text FROM customers WHERE code = ?", String.class, customerCode);
            } catch (DataAccessException ex) {
                cust = null;
            }
        }
        if (cust == null) {
            throw com.xqt.saas.common.ApiException.badRequest("customerId 或 customerCode 必填");
        }
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ci.currency,
                   COUNT(*)                       AS invoice_count,
                   MAX(ci.invoice_date)           AS last_billed_at,
                   MIN(ci.bill_period_from)       AS earliest_period_from,
                   MAX(ci.bill_period_to)         AS latest_period_to,
                   SUM(ci.total_amount)           AS billed_total
              FROM customer_invoices ci
             WHERE ci.customer_id = ?::uuid
               AND ci.status <> 'VOID'
             GROUP BY ci.currency
             ORDER BY ci.currency
            """, cust);
        return Map.of("data", rows);
    }

    /**
     * R-10: 账单生成前预览 — 列出所有 charges, 标"可开账/已开账/已作废", 让用户精选.
     * GET /api/acc/bills/billable-preview?customerId=&currency=&dateFrom=&dateTo=&orderNo=
     */
    @GetMapping("/billable-preview")
    public Map<String, Object> billablePreview(
        @org.springframework.web.bind.annotation.RequestParam(required = false) String customerId,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String currency,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String dateFrom,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String dateTo,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String orderNo
    ) {
        String cust = resolveCustomerId(customerId);
        if (cust == null || cust.isBlank()) {
            throw com.xqt.saas.common.ApiException.badRequest("customerId 必填");
        }
        java.time.LocalDate from = parseDate(dateFrom);
        java.time.LocalDate to = parseDate(dateTo);
        if (from == null) from = java.time.LocalDate.now().minusDays(30);
        if (to == null) to = java.time.LocalDate.now();
        String curr = (currency == null || currency.isBlank()) ? null : currency;
        String orderPat = (orderNo == null || orderNo.isBlank()) ? null : "%" + orderNo + "%";

        // 返每笔 AR charge + 状态标 (BILLABLE/BILLED/VOIDED/UNAUDITED)
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ch.id::text                                        AS "chargeId",
              o.order_no                                         AS "orderNo",
              o.customer_ref                                     AS "customerRef",
              ci.code                                            AS "chargeItemCode",
              ci.name                                            AS "chargeItemName",
              ch.amount                                          AS "amount",
              ch.currency                                        AS "currency",
              ch.audit_status                                    AS "auditStatus",
              ch.status::text                                    AS "status",
              ch.settlement_status                               AS "settlementStatus",
              to_char(ch.created_at, 'YYYY-MM-DD HH24:MI')       AS "chargedAt",
              -- 已开账时返关联的 invoice_no
              (SELECT ci2.invoice_no FROM customer_invoice_lines cil
                 JOIN customer_invoices ci2 ON ci2.id = cil.invoice_id
                 WHERE cil.charge_id = ch.id AND ci2.status <> 'VOID' LIMIT 1) AS "invoiceNo",
              CASE
                WHEN ch.status::text = 'VOID' THEN 'VOIDED'
                WHEN ch.audit_status <> 'AUDITED' THEN 'UNAUDITED'
                WHEN EXISTS (SELECT 1 FROM customer_invoice_lines cil
                  JOIN customer_invoices ci2 ON ci2.id = cil.invoice_id
                  WHERE cil.charge_id = ch.id AND ci2.status <> 'VOID')
                THEN 'BILLED'
                WHEN ch.settlement_status = 'SETTLED' THEN 'BILLED'
                ELSE 'BILLABLE'
              END                                                AS "billStatus"
            FROM charges ch
            LEFT JOIN orders o ON o.id = ch.order_id
            LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
            WHERE ch.customer_id = ?::uuid
              AND ch.side = 'AR'
              AND (?::char(3) IS NULL OR ch.currency = ?::char(3))
              AND ch.created_at >= ?::date
              AND ch.created_at < (?::date + interval '1 day')
              AND (?::text IS NULL OR o.order_no ILIKE ?::text OR o.customer_ref ILIKE ?::text)
            ORDER BY ch.created_at DESC, o.order_no
            LIMIT 500
            """, cust, curr, curr, from, to, orderPat, orderPat, orderPat);

        // 汇总
        java.math.BigDecimal billableTotal = java.math.BigDecimal.ZERO;
        int billableCount = 0, billedCount = 0, voidedCount = 0, unauditedCount = 0;
        for (Map<String, Object> r : rows) {
            String st = String.valueOf(r.get("billStatus"));
            switch (st) {
                case "BILLABLE" -> {
                    billableCount++;
                    java.math.BigDecimal amt = (java.math.BigDecimal) r.get("amount");
                    if (amt != null) billableTotal = billableTotal.add(amt);
                }
                case "BILLED" -> billedCount++;
                case "VOIDED" -> voidedCount++;
                case "UNAUDITED" -> unauditedCount++;
            }
        }

        return Map.of(
            "data", rows,
            "total", rows.size(),
            "billableCount", billableCount,
            "billableTotal", billableTotal,
            "billedCount", billedCount,
            "voidedCount", voidedCount,
            "unauditedCount", unauditedCount,
            "dateFrom", from.toString(),
            "dateTo", to.toString(),
            "currency", curr == null ? "" : curr
        );
    }

    /**
     * 前端 "账单重算"：作废原账单 + 用同客户/日期段重新生成。
     * 旧前端只传 { id }，从原账单读出客户和日期段。
     */
    @PostMapping("/reload")
    public Map<String, Object> reload(@RequestBody Map<String, Object> body) {
        String oldId = body.get("id") == null ? null : body.get("id").toString();
        if (oldId == null || oldId.isBlank()) {
            return Map.of("ok", false, "error", "id is required");
        }
        Map<String, Object> old;
        try {
            old = jdbc.queryForMap("""
                SELECT customer_id::text AS customer_id, currency,
                       coalesce(invoice_date::date, created_at::date) AS date_from,
                       coalesce(invoice_date::date, current_date) AS date_to
                FROM customer_invoices WHERE id = ?::uuid
                """, oldId);
        } catch (org.springframework.dao.DataAccessException ex) {
            return Map.of("ok", false, "error", "账单不存在");
        }
        java.time.LocalDate from = (java.time.LocalDate)
            ((java.sql.Date) old.get("date_from")).toLocalDate();
        java.time.LocalDate to = (java.time.LocalDate)
            ((java.sql.Date) old.get("date_to")).toLocalDate();
        // 作废旧账单：解除 customer_invoice_lines + 置状态 VOID，这样新 generate 能重新聚合费用
        jdbc.update("UPDATE customer_invoice_lines SET charge_id = NULL WHERE invoice_id = ?::uuid", oldId);
        jdbc.update("UPDATE customer_invoices SET status = 'VOID', writeoff_status = 'VOID' WHERE id = ?::uuid", oldId);

        com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice req =
            new com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice(
                (String) old.get("customer_id"), from, to, (String) old.get("currency"),
                "STANDARD", null);
        try {
            com.xqt.saas.documentcharges.DocumentChargeResponses.InvoiceResult r =
                docService.generateCustomerInvoice(currentPrincipal(), req);
            return Map.of("ok", true, "billId", r.invoiceId(), "invoiceNo", r.invoiceNo(),
                "lineCount", r.lineCount(), "totalAmount", r.totalAmount(),
                "voidedBillId", oldId);
        } catch (com.xqt.saas.common.ApiException ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    private static String resolveCustomerId(Object v) {
        // 兼容旧前端：customerId 可能是 UUID 字符串、也可能是被 Number() 转换后的数字（导致 NaN）
        if (v == null) return null;
        String s = v.toString();
        if (s.isBlank() || "NaN".equals(s) || "0".equals(s)) return null;
        return s;
    }

    private static java.time.LocalDate parseDate(Object v) {
        if (v == null) return null;
        try {
            return java.time.LocalDate.parse(v.toString());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private com.xqt.saas.auth.AuthPrincipal currentPrincipal() {
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.xqt.saas.auth.AuthPrincipal p)) {
            throw com.xqt.saas.common.ApiException.unauthorized("authentication required");
        }
        return p;
    }

    private static Object[] buildBillsListParams(String search, String dateFrom, String dateTo,
                                                   BranchAccessFilter.AccessClause access,
                                                   int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private static Object[] buildBillsListParamsWithAdv(String search, String dateFrom, String dateTo,
                                                         java.util.List<Object> advParams,
                                                         BranchAccessFilter.AccessClause access,
                                                         int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(advParams);
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal amount = (BigDecimal) row.get("total_amount");
        BigDecimal paid = (BigDecimal) row.get("paid_amount");
        BigDecimal unpay = (amount == null ? BigDecimal.ZERO : amount)
            .subtract(paid == null ? BigDecimal.ZERO : paid);
        out.put("id", row.get("id"));
        out.put("no", row.get("invoice_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("settlement", row.get("settlement"));
        out.put("theDate", json.value(row.get("issued_at")));
        out.put("amount", amount);
        out.put("paid", paid);
        out.put("unpay", unpay);
        out.put("quantity", row.get("line_count"));
        out.put("status", row.get("status"));
        out.put("salesman", row.get("salesman_name") == null ? "" : row.get("salesman_name"));
        out.put("currency", row.get("currency"));
        // 审核流字段：前端用来显示"已审核"红色标记 + 决定能否点删除/反审按钮
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
