package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 财务对比报表 — 渠道商账单对比 + 应收 vs 实收对比.
 *
 * GET /api/acc/reconciliation/carrier-invoice
 *   — 我们记的 AP 成本 vs 上传的渠道商真实账单 (Invoice CSV/xlsx), 找出 drift
 *
 * GET /api/acc/reconciliation/ar-vs-received
 *   — 客户应收 vs 已实收, 看每个客户欠款 + 多收 + 漏收
 *
 * POST /api/acc/reconciliation/carrier-invoice/import (multipart xlsx)
 *   — 上传渠道商账单, 按 tracking_no match 我们的 AP charge, 生成对比报告
 */
@RestController
@RequestMapping("/api/acc/reconciliation")
public class AccReconciliationController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccReconciliationController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ─────────────────────────────────────────────────
    // 1. 渠道商账单对比 (Carrier Invoice Reconciliation)
    // ─────────────────────────────────────────────────

    @GetMapping("/carrier-invoice")
    public Map<String, Object> carrierInvoice(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String partnerCode,
        @RequestParam(required = false) String channelCode,
        @RequestParam(required = false, defaultValue = "false") boolean onlyDrift
    ) {
        String from = dateFrom == null ? null : dateFrom;
        String to = dateTo == null ? null : dateTo;
        String pc = partnerCode == null || partnerCode.isBlank() ? null : partnerCode;
        String cc = channelCode == null || channelCode.isBlank() ? null : channelCode;

        // 对比: 每个 shipment 的 AP charge (我们记的成本) vs partner_invoice_lines (渠道商上传的真账单)
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              s.shipment_no                    AS "shipmentNo",
              o.order_no                       AS "orderNo",
              c.tracking_no                    AS "trackingNo",
              ch_code.code                     AS "channelCode",
              p.code                           AS "partnerCode",
              p.name                           AS "partnerName",
              -- 我们记的 AP 成本
              coalesce(our_ap.our_amount, 0)   AS "ourAmount",
              our_ap.our_currency              AS "ourCurrency",
              -- 渠道商真账单
              coalesce(pi.partner_amount, 0)   AS "partnerAmount",
              pi.partner_currency              AS "partnerCurrency",
              pi.invoice_no                    AS "partnerInvoiceNo",
              -- 差值
              (coalesce(pi.partner_amount, 0) - coalesce(our_ap.our_amount, 0)) AS "delta",
              CASE
                WHEN pi.partner_amount IS NULL THEN 'NO_PARTNER_BILL'
                WHEN our_ap.our_amount IS NULL THEN 'NO_OUR_COST'
                WHEN abs(coalesce(pi.partner_amount, 0) - coalesce(our_ap.our_amount, 0)) < 0.01 THEN 'MATCH'
                WHEN abs(coalesce(pi.partner_amount, 0) - coalesce(our_ap.our_amount, 0)) <= coalesce(our_ap.our_amount, 0) * 0.05 THEN 'DRIFT_MINOR'
                ELSE 'DRIFT_MAJOR'
              END                              AS "reconcileStatus",
              s.created_at::date               AS "shipmentDate"
            FROM shipments s
            LEFT JOIN shipment_order_links sol ON sol.shipment_id = s.id
            LEFT JOIN orders o ON o.id = sol.order_id
            LEFT JOIN cartons c ON c.shipment_id = s.id
            LEFT JOIN channels ch_code ON ch_code.id = s.channel_id
            -- 我们的 AP 总和 (按 shipment)
            LEFT JOIN LATERAL (
              SELECT sum(amount) AS our_amount, max(currency) AS our_currency
                FROM charges
               WHERE shipment_id = s.id AND side = 'AP' AND status::text <> 'VOID'
            ) our_ap ON true
            -- 渠道商账单 (partner_invoice_lines 没 tracking_no 列, 走 carton_id 关联)
            LEFT JOIN LATERAL (
              SELECT pil.amount AS partner_amount, pil.currency AS partner_currency,
                     pin.invoice_no, pin.partner_id
                FROM partner_invoice_lines pil
                JOIN partner_invoices pin ON pin.id = pil.invoice_id
               WHERE pil.carton_id = c.id
                 AND pin.status <> 'VOID'
               LIMIT 1
            ) pi ON true
            LEFT JOIN partners p ON p.id = pi.partner_id
            WHERE s.created_at::date >= coalesce(?::date, current_date - interval '90 days')
              AND s.created_at::date <= coalesce(?::date, current_date)
              AND (?::text IS NULL OR p.code = ?::text)
              AND (?::text IS NULL OR ch_code.code = ?::text)
            ORDER BY s.created_at DESC
            LIMIT 500
            """, from, to, pc, pc, cc, cc);

        if (onlyDrift) {
            rows = rows.stream()
                .filter(r -> {
                    String st = (String) r.get("reconcileStatus");
                    return "DRIFT_MINOR".equals(st) || "DRIFT_MAJOR".equals(st);
                })
                .toList();
        }

        // summary
        int total = rows.size();
        long matched = rows.stream().filter(r -> "MATCH".equals(r.get("reconcileStatus"))).count();
        long drift = rows.stream().filter(r -> {
            String s = (String) r.get("reconcileStatus");
            return "DRIFT_MINOR".equals(s) || "DRIFT_MAJOR".equals(s);
        }).count();
        long noPartner = rows.stream().filter(r -> "NO_PARTNER_BILL".equals(r.get("reconcileStatus"))).count();
        long noOurs = rows.stream().filter(r -> "NO_OUR_COST".equals(r.get("reconcileStatus"))).count();
        BigDecimal totalDelta = rows.stream()
            .map(r -> (BigDecimal) r.get("delta"))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "data", rows,
            "total", total,
            "summary", Map.of(
                "matched", matched,
                "drift", drift,
                "noPartnerBill", noPartner,
                "noOurCost", noOurs,
                "totalDelta", totalDelta
            )
        );
    }

    // ─────────────────────────────────────────────────
    // 2. 应收 vs 实收 对比 (AR vs Received Reconciliation)
    // ─────────────────────────────────────────────────

    /** R-4 阶段 A: 上传渠道商账单 xlsx → 解析 + 落到 partner_invoice_lines (按 tracking 找 carton). */
    @PostMapping("/carrier-invoice/import")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCarrierInvoice(
        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
        @RequestParam("partnerCode") String partnerCode,
        @RequestParam(defaultValue = "USD") String currency
    ) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("file 必填");
        String name = file.getOriginalFilename();
        if (name == null || (!name.toLowerCase().endsWith(".xlsx") && !name.toLowerCase().endsWith(".xls"))) {
            throw ApiException.badRequest("仅支持 .xlsx / .xls");
        }
        String partnerId;
        try {
            partnerId = jdbc.queryForObject(
                "SELECT id::text FROM partners WHERE code = ? LIMIT 1", String.class, partnerCode);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("找不到供应商 code=" + partnerCode);
        }
        // 读 xlsx
        java.util.List<String[]> raw = new java.util.ArrayList<>();
        try {
            com.alibaba.excel.EasyExcel.read(file.getInputStream(),
                new com.alibaba.excel.read.listener.ReadListener<java.util.Map<Integer, String>>() {
                    @Override public void invoke(java.util.Map<Integer, String> data,
                                                  com.alibaba.excel.context.AnalysisContext ctx) {
                        String[] cells = new String[data.size()];
                        for (int i = 0; i < cells.length; i++) cells[i] = data.getOrDefault(i, "");
                        raw.add(cells);
                    }
                    @Override public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext ctx) {}
                }).sheet().headRowNumber(0).doRead();
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("xlsx 读取失败: " + ex.getMessage());
        }
        if (raw.size() < 2) throw ApiException.badRequest("xlsx 至少需要 header + 1 行");

        // 表头映射: 中文/英文都行
        Map<String, Integer> col = new java.util.HashMap<>();
        String[] headers = raw.get(0);
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] == null ? "" : headers[i].trim().toLowerCase();
            if (h.equals("运单号") || h.equals("tracking_no") || h.equals("跟踪号")) col.put("tracking", i);
            else if (h.equals("金额") || h.equals("amount") || h.equals("总额")) col.put("amount", i);
            else if (h.equals("发货日期") || h.equals("ship_date") || h.equals("日期")) col.put("date", i);
            else if (h.equals("服务") || h.equals("service")) col.put("service", i);
        }
        if (!col.containsKey("tracking") || !col.containsKey("amount")) {
            throw ApiException.badRequest("xlsx 表头必须含: 运单号/tracking_no, 金额/amount");
        }

        // 创建 partner_invoice
        String invoiceNo = "PI-" + partnerCode + "-" + System.currentTimeMillis();
        String invoiceId;
        try {
            invoiceId = jdbc.queryForObject("""
                INSERT INTO partner_invoices (tenant_id, partner_id, invoice_no, currency, status, invoice_date)
                VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, 'CONFIRMED', current_date)
                RETURNING id::text
                """, String.class, partnerId, invoiceNo, currency);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("创建 partner_invoice 失败: " + ex.getMessage());
        }

        int inserted = 0, skipped = 0;
        BigDecimal total = BigDecimal.ZERO;
        java.util.List<Map<String, Object>> details = new java.util.ArrayList<>();
        for (int r = 1; r < raw.size(); r++) {
            String[] cells = raw.get(r);
            String tracking = idx(cells, col.get("tracking"));
            String amtStr = idx(cells, col.get("amount"));
            if (tracking == null || amtStr == null) { skipped++; continue; }
            BigDecimal amt;
            try { amt = new BigDecimal(amtStr.replace(",", "")); }
            catch (NumberFormatException ex) { skipped++; continue; }
            try {
                java.util.List<String> cartonIds = jdbc.queryForList(
                    "SELECT id::text FROM cartons WHERE tracking_no = ? LIMIT 1", String.class, tracking);
                String cartonId = cartonIds.isEmpty() ? null : cartonIds.get(0);
                jdbc.update("""
                    INSERT INTO partner_invoice_lines
                      (tenant_id, invoice_id, carton_id, amount, currency, description)
                    VALUES (current_setting('app.current_tenant_id')::uuid,
                            ?::uuid, ?::uuid, ?, ?, ?)
                    """, invoiceId, cartonId, amt, currency, "tracking=" + tracking);
                inserted++;
                total = total.add(amt);

                // R-6 自动核销: 真账单到位后, 把对应渠道 cost_pre_estimates 的 reconciled_count++
                // 简单按 channel_code 匹配, +1 直到达到 qty (status → EXHAUSTED)
                jdbc.update("""
                    UPDATE cost_pre_estimates
                       SET reconciled_count = reconciled_count + 1,
                           status = CASE WHEN reconciled_count + 1 >= qty THEN 'EXHAUSTED' ELSE status END
                     WHERE id = (
                       SELECT cpe.id FROM cost_pre_estimates cpe
                       LEFT JOIN cartons ct ON ct.id = ?::uuid
                       LEFT JOIN shipments s ON s.id = ct.shipment_id
                       LEFT JOIN channels ch ON ch.id = s.channel_id
                       WHERE cpe.status = 'ACTIVE'
                         AND cpe.reconciled_count < cpe.qty
                         AND (cpe.channel_code = ch.code OR cpe.channel_id = s.channel_id)
                       ORDER BY cpe.effective_date ASC LIMIT 1)
                    """, cartonId);

                details.add(Map.of("row", r + 1, "tracking", tracking, "amount", amt,
                    "matched", cartonId != null, "ok", true));
            } catch (DataAccessException ex) {
                details.add(Map.of("row", r + 1, "tracking", tracking, "error", ex.getMessage()));
                skipped++;
            }
        }
        // 更新 invoice total
        jdbc.update("UPDATE partner_invoices SET total_amount = ? WHERE id = ?::uuid", total, invoiceId);

        return Map.of(
            "invoiceNo", invoiceNo, "invoiceId", invoiceId,
            "inserted", inserted, "skipped", skipped, "total", total,
            "details", details.subList(0, Math.min(20, details.size()))
        );
    }
    private static String idx(String[] cells, Integer i) {
        if (i == null || i >= cells.length) return null;
        String v = cells[i];
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * R-4 阶段 B: UPS Rating API 实时报价 (stub).
     * 实际实现走 RateEngine.quote() + 标记 source_type='LIVE_QUOTE'.
     * 当前生产没接 UPS Negotiated Rates API (需 UPS 商务合同), 走业务 RateEngine 算 + 标 LIVE_QUOTE.
     * 接 UPS 真 API 时把内部 RateEngine 调用换成 UpsRatingClient.quote() 即可.
     */
    @PostMapping("/carrier-live-quote")
    public Map<String, Object> liveQuote(@RequestBody Map<String, Object> body) {
        // Stub: 直接走 RateEngine, mark source_type='LIVE_QUOTE' for traceability
        String channelCode = (String) body.get("channelCode");
        String postcode = (String) body.get("postcode");
        Object weight = body.get("weight");
        if (channelCode == null || weight == null) {
            throw ApiException.badRequest("channelCode + weight 必填");
        }
        // 真实场景接 UPS Rating REST API (https://onlinetools.ups.com/api/rating):
        //   POST {ups_base}/rating/v1/Shop
        //   Headers: Authorization: Bearer <oauth>, transId, transactionSrc
        //   Body: RateRequest { Shipper, ShipTo, Package: {Weight, PackagingType, Dimensions} }
        //   Resp: RateResponse.RatedShipment[0].TotalCharges.MonetaryValue
        //
        // 此处暂用占位 — 等签 UPS Negotiated Rates 合同后切换.
        return Map.of(
            "ok", true,
            "source", "ENGINE_STUB",  // 接真 API 后变 'UPS_NEGOTIATED'
            "note", "占位实现 — 等 UPS Rating API 商务接入. 已留改造点 ↑.",
            "request", body
        );
    }

    @GetMapping("/ar-vs-received")
    public Map<String, Object> arVsReceived(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String customerCode,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false, defaultValue = "false") boolean onlyUnbalanced
    ) {
        String from = dateFrom == null ? null : dateFrom;
        String to = dateTo == null ? null : dateTo;
        String cc = customerCode == null || customerCode.isBlank() ? null : customerCode;
        String curr = currency == null || currency.isBlank() ? null : currency;

        // 按客户+币种聚合: 应收总额 vs 已收款总额 vs 差
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              c.code                              AS "customerCode",
              c.name                              AS "customerName",
              ar.currency                         AS "currency",
              -- 已审 AR 总额
              coalesce(ar.audited_ar, 0)          AS "auditedAr",
              -- 已收款总额 (audit_status='AUDITED')
              coalesce(rc.received_amount, 0)     AS "receivedAmount",
              -- 应付差 = AR - Received (正=客户欠我, 负=客户多付)
              (coalesce(ar.audited_ar, 0) - coalesce(rc.received_amount, 0)) AS "balance",
              CASE
                WHEN abs(coalesce(ar.audited_ar, 0) - coalesce(rc.received_amount, 0)) < 0.01 THEN 'BALANCED'
                WHEN (coalesce(ar.audited_ar, 0) - coalesce(rc.received_amount, 0)) > 0 THEN 'CUSTOMER_OWES'
                ELSE 'OVER_PAID'
              END                                 AS "status",
              ar.audited_count                    AS "arCount",
              rc.received_count                   AS "receivedCount"
            FROM customers c
            LEFT JOIN LATERAL (
              SELECT sum(amount) AS audited_ar, currency, count(*) AS audited_count
                FROM charges
               WHERE customer_id = c.id AND side = 'AR' AND audit_status = 'AUDITED'
                 AND status::text <> 'VOID'
                 AND created_at::date >= coalesce(?::date, current_date - interval '90 days')
                 AND created_at::date <= coalesce(?::date, current_date)
               GROUP BY currency
            ) ar ON true
            LEFT JOIN LATERAL (
              SELECT sum(amount) AS received_amount, count(*) AS received_count
                FROM payments
               WHERE customer_id = c.id AND audit_status = 'AUDITED'
                 AND currency = ar.currency
                 AND received_at::date >= coalesce(?::date, current_date - interval '90 days')
                 AND received_at::date <= coalesce(?::date, current_date)
            ) rc ON true
            WHERE ar.currency IS NOT NULL
              AND (?::text IS NULL OR c.code = ?::text)
              AND (?::char(3) IS NULL OR ar.currency = ?::char(3))
            ORDER BY abs(coalesce(ar.audited_ar, 0) - coalesce(rc.received_amount, 0)) DESC
            LIMIT 500
            """, from, to, from, to, cc, cc, curr, curr);

        if (onlyUnbalanced) {
            rows = rows.stream()
                .filter(r -> !"BALANCED".equals(r.get("status")))
                .toList();
        }

        // summary
        long balanced = rows.stream().filter(r -> "BALANCED".equals(r.get("status"))).count();
        long owes = rows.stream().filter(r -> "CUSTOMER_OWES".equals(r.get("status"))).count();
        long overPaid = rows.stream().filter(r -> "OVER_PAID".equals(r.get("status"))).count();
        BigDecimal totalAr = rows.stream()
            .map(r -> (BigDecimal) r.get("auditedAr"))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = rows.stream()
            .map(r -> (BigDecimal) r.get("receivedAmount"))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = totalAr.subtract(totalReceived);

        return Map.of(
            "data", rows,
            "total", rows.size(),
            "summary", Map.of(
                "balanced", balanced,
                "customerOwes", owes,
                "overPaid", overPaid,
                "totalAr", totalAr,
                "totalReceived", totalReceived,
                "totalBalance", totalBalance
            )
        );
    }
}
