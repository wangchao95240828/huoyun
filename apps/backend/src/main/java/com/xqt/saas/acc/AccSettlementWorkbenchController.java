package com.xqt.saas.acc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 核算工作台（AP 侧 + 利润）。结构对应财务工作台（AR 侧），但操作对象是供应商成本。
 *
 *   阶段 ①「待核成本」     Submit 自动写 charges (AP, ESTIMATED)
 *   阶段 ②「待付成本」     UPS/FedEx 实际账单 → 调金额 → AUDITED → 进入待付队列
 *   阶段 ③「已付成本」     财务付款给供应商 → balance_ledger PAYMENT + SETTLED
 *
 *   利润 = sum(AR) - sum(AP) per shipment / order
 */
@RestController
@RequestMapping("/api/acc/settlement-workbench")
public class AccSettlementWorkbenchController {
    private static final String SINGLE_TENANT = "2bda8c16-7b19-4ce6-ab71-9584f5a140ed";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccSettlementWorkbenchController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 三个 bucket 的汇总（AP 侧）。 */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              CASE
                WHEN status='ESTIMATED'::charge_status AND audit_status='PENDING'  THEN 'cost-pending'
                WHEN status='ADJUSTED'::charge_status  AND audit_status='AUDITED' AND settlement_status='UNSETTLED' THEN 'pending-pay'
                WHEN settlement_status='SETTLED'                                   THEN 'paid'
                ELSE 'other'
              END AS bucket,
              count(*) AS row_count,
              coalesce(sum(amount), 0) AS total_amount
            FROM charges
            WHERE side='AP' AND status<>'VOID'::charge_status
            GROUP BY bucket
            """);
        Map<String, Map<String, Object>> byBucket = new LinkedHashMap<>();
        for (String b : new String[]{"cost-pending", "pending-pay", "paid"}) {
            byBucket.put(b, Map.of("count", 0, "amount", 0));
        }
        for (Map<String, Object> r : rows) {
            String b = (String) r.get("bucket");
            if (b == null || b.equals("other")) continue;
            byBucket.put(b, Map.of("count", r.get("row_count"), "amount", r.get("total_amount")));
        }
        return Map.of("buckets", byBucket);
    }

    /** 阶段 ① 待核成本（AP ESTIMATED 未审）。 */
    @GetMapping("/cost-pending")
    public Map<String, Object> costPending(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        return listByAuditStatus("ESTIMATED", "PENDING", "UNSETTLED", page, pageSize);
    }

    /** 阶段 ② 待付成本（AP ADJUSTED + AUDITED + UNSETTLED）。 */
    @GetMapping("/pending-pay")
    public Map<String, Object> pendingPay(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        return listByAuditStatus("ADJUSTED", "AUDITED", "UNSETTLED", page, pageSize);
    }

    /** 阶段 ③ 已付成本。 */
    @GetMapping("/paid")
    public Map<String, Object> paid(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM charges
             WHERE side='AP' AND status<>'VOID'::charge_status AND settlement_status='SETTLED'
            """, Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.paid_amount, ch.currency,
                   ch.status::text AS status, ch.audit_status, ch.settlement_status,
                   ch.created_at,
                   o.order_no AS order_no,
                   ct.tracking_no AS tracking_no,
                   c.code AS channel_code, c.name AS channel_name
              FROM charges ch
              LEFT JOIN orders o ON o.id = ch.order_id
              LEFT JOIN shipments s ON s.id = ch.shipment_id
              LEFT JOIN cartons ct ON ct.shipment_id = ch.shipment_id
              LEFT JOIN channels c ON c.id = s.channel_id
             WHERE ch.side='AP' AND ch.status<>'VOID'::charge_status AND ch.settlement_status='SETTLED'
             ORDER BY ch.created_at DESC
             LIMIT ? OFFSET ?
            """, limit, offset);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    private Map<String, Object> listByAuditStatus(String chargeStatus, String auditStatus,
                                                   String settleStatus, Integer page, Integer pageSize) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM charges
             WHERE side='AP' AND status=?::charge_status
               AND audit_status=? AND settlement_status=?
            """, Long.class, chargeStatus, auditStatus, settleStatus);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.paid_amount, ch.currency,
                   ch.status::text AS status, ch.audit_status, ch.settlement_status,
                   ch.created_at,
                   o.order_no AS order_no,
                   ct.tracking_no AS tracking_no,
                   c.code AS channel_code, c.name AS channel_name
              FROM charges ch
              LEFT JOIN orders o ON o.id = ch.order_id
              LEFT JOIN shipments s ON s.id = ch.shipment_id
              LEFT JOIN cartons ct ON ct.shipment_id = ch.shipment_id
              LEFT JOIN channels c ON c.id = s.channel_id
             WHERE ch.side='AP' AND ch.status=?::charge_status
               AND ch.audit_status=? AND ch.settlement_status=?
             ORDER BY ch.created_at DESC
             LIMIT ? OFFSET ?
            """, chargeStatus, auditStatus, settleStatus, limit, offset);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  阶段 ① → ② 单条调整 AP 成本
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/charges/{id}/adjust-cost")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adjustCost(@PathVariable String id, @RequestBody Map<String, Object> body) {
        BigDecimal newAmount;
        try { newAmount = new BigDecimal(body.get("amount").toString()); }
        catch (Exception ex) { throw ApiException.badRequest("amount 必填且为数字"); }
        String reason = body.get("reason") == null ? null : body.get("reason").toString();

        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT amount, status::text AS status, side, settlement_status FROM charges WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) { throw ApiException.notFound("charge 不存在"); }

        if (!"AP".equals(ch.get("side"))) throw ApiException.badRequest("仅 AP charge 可在核算调整");
        if ("VOID".equals(ch.get("status"))) throw ApiException.badRequest("已作废的不能调整");
        if ("SETTLED".equals(ch.get("settlement_status"))) {
            throw ApiException.badRequest("已付款的成本不能调整");
        }
        BigDecimal oldAmount = (BigDecimal) ch.get("amount");
        jdbc.update("""
            UPDATE charges SET amount=?, status='ADJUSTED'::charge_status, audit_status='PENDING'
             WHERE id = ?::uuid
            """, newAmount, id);
        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'charges', ?, 'UPDATE', current_user,
                    jsonb_build_object('amount', ?, 'status', ?, 'side', 'AP'),
                    jsonb_build_object('amount', ?, 'status', 'ADJUSTED', 'side', 'AP'),
                    ?)
            """, id, oldAmount, ch.get("status"), newAmount,
                 "AP 调整" + (reason == null ? "" : ": " + reason));
        return Map.of(
            "id", id, "oldAmount", oldAmount, "newAmount", newAmount,
            "diff", newAmount.subtract(oldAmount), "status", "ADJUSTED"
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UPS/FedEx 实际账单 CSV → 批量调 AP 成本
    //  CSV: tracking_no,actual_cost,currency
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/import-actual-cost")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importActualCost(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("文件必填");
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw ApiException.badRequest("CSV 空文件");
            String[] cols = header.toLowerCase().split(",");
            int idxTrack = -1, idxAmount = -1;
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i].trim();
                if (c.equals("tracking_no") || c.equals("tracking")) idxTrack = i;
                else if (c.equals("actual_cost") || c.equals("actual_amount") || c.equals("amount")) idxAmount = i;
            }
            if (idxTrack < 0 || idxAmount < 0) {
                throw ApiException.badRequest("CSV 必须包含 tracking_no,actual_cost 列");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                String trackingNo = parts[idxTrack].trim();
                BigDecimal newAmount;
                try { newAmount = new BigDecimal(parts[idxAmount].trim()); }
                catch (Exception ex) {
                    skipped.add(Map.of("trackingNo", trackingNo, "reason", "金额格式错: " + parts[idxAmount]));
                    continue;
                }
                List<Map<String, Object>> chRows = jdbc.queryForList("""
                    SELECT ch.id::text AS id, ch.amount FROM charges ch
                      JOIN cartons ct ON ct.shipment_id = ch.shipment_id
                     WHERE ct.tracking_no = ? AND ch.side='AP'
                       AND ch.status IN ('ESTIMATED'::charge_status, 'ADJUSTED'::charge_status)
                       AND ch.settlement_status='UNSETTLED'
                    """, trackingNo);
                if (chRows.isEmpty()) {
                    skipped.add(Map.of("trackingNo", trackingNo, "reason", "无匹配 AP charge"));
                    continue;
                }
                // 多 AP charge 按原值比例分配
                BigDecimal oldTotal = chRows.stream()
                    .map(r -> (BigDecimal) r.get("amount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                List<String> updated = new ArrayList<>();
                if (oldTotal.signum() == 0) {
                    jdbc.update("UPDATE charges SET amount=?, status='ADJUSTED'::charge_status, audit_status='PENDING' WHERE id=?::uuid",
                        newAmount, chRows.get(0).get("id"));
                    updated.add((String) chRows.get(0).get("id"));
                } else {
                    BigDecimal allocated = BigDecimal.ZERO;
                    for (int i = 0; i < chRows.size(); i++) {
                        Map<String, Object> r = chRows.get(i);
                        BigDecimal oldA = (BigDecimal) r.get("amount");
                        BigDecimal newA = (i == chRows.size() - 1)
                            ? newAmount.subtract(allocated)
                            : newAmount.multiply(oldA).divide(oldTotal, 2, java.math.RoundingMode.HALF_UP);
                        if (i < chRows.size() - 1) allocated = allocated.add(newA);
                        jdbc.update("UPDATE charges SET amount=?, status='ADJUSTED'::charge_status, audit_status='PENDING' WHERE id=?::uuid",
                            newA, r.get("id"));
                        updated.add((String) r.get("id"));
                    }
                }
                matched.add(Map.of(
                    "trackingNo", trackingNo, "chargeIds", updated,
                    "oldAmount", oldTotal, "newAmount", newAmount,
                    "diff", newAmount.subtract(oldTotal)
                ));
            }
        }
        return Map.of("matched", matched.size(), "skipped", skipped.size(),
                      "matchedDetails", matched, "skippedDetails", skipped);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  审核成本（一审）— AP charges 一审通过
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/audit-cost-charges")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> auditCostCharges(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        int n = jdbc.update("""
            UPDATE charges SET audit_status='AUDITED', audited_at=now()
             WHERE id = ANY(?::uuid[]) AND side='AP'
               AND audit_status='PENDING' AND status<>'VOID'::charge_status
            """, (Object) ids.toArray(new String[0]));
        return Map.of("auditedCount", n);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  付款核销 — 给供应商付款，charges SETTLED + balance_ledger PAYMENT
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/pay-supplier")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> paySupplier(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        String remark = body.get("remark") == null ? "付供应商" : body.get("remark").toString();

        // 校验：全部 AP + AUDITED + UNSETTLED
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.currency,
                   ch.side::text AS side, ch.audit_status, ch.settlement_status,
                   c.code AS channel_code, c.name AS channel_name
              FROM charges ch
              LEFT JOIN shipments s ON s.id = ch.shipment_id
              LEFT JOIN channels c ON c.id = s.channel_id
             WHERE ch.id = ANY(?::uuid[])
            """, (Object) ids.toArray(new String[0]));
        if (rows.size() != ids.size()) throw ApiException.badRequest("部分 charge 不存在");
        for (Map<String, Object> r : rows) {
            if (!"AP".equals(r.get("side"))) throw ApiException.badRequest("含非 AP charge: " + r.get("id"));
            if (!"AUDITED".equals(r.get("audit_status"))) throw ApiException.badRequest("含未审核 AP: " + r.get("id"));
            if (!"UNSETTLED".equals(r.get("settlement_status"))) throw ApiException.badRequest("含已付 AP: " + r.get("id"));
        }

        // 标 SETTLED
        jdbc.update("""
            UPDATE charges SET settlement_status='SETTLED', paid_amount=amount
             WHERE id = ANY(?::uuid[])
            """, (Object) ids.toArray(new String[0]));

        // 按 currency + channel 分组，每组写一条 PAYMENT ledger（owner_type='PARTNER' 占位）
        Map<String, BigDecimal> byCur = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = r.get("currency") + "|" + (r.get("channel_code") == null ? "?" : r.get("channel_code"));
            byCur.merge(key, (BigDecimal) r.get("amount"), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : byCur.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            String currency = parts[0];
            String channelCode = parts[1];
            jdbc.execute("SELECT set_config('app.current_tenant_id', '" + SINGLE_TENANT + "', true)");
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  '00000000-0000-0000-0000-000000000000'::uuid,
                  'PARTNER', NULL,
                  'PAYMENT'::balance_ledger_biz_type,
                  'charges', NULL, ?,
                  ?, 'DEBIT'::balance_ledger_direction,
                  ?, 0, ?, current_user, ?
                )
                """, channelCode, currency, e.getValue(), e.getValue().negate(),
                     remark + " (" + channelCode + ")");
        }

        return Map.of(
            "paidCount", rows.size(),
            "groupCount", byCur.size(),
            "groups", byCur
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  利润视图：AR - AP per shipment / order
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/profit-list")
    public Map<String, Object> profitList(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        // 按 shipment 聚合 AR/AP
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              s.id::text AS shipment_id,
              s.shipment_no,
              o.order_no,
              c.code AS customer_code,
              c.name AS customer_name,
              ch_ar.ar_total, ch_ap.ap_total,
              coalesce(ch_ar.ar_total, 0) - coalesce(ch_ap.ap_total, 0) AS profit,
              ch_ar.currency
            FROM shipments s
            LEFT JOIN shipment_order_links sol ON sol.shipment_id = s.id
            LEFT JOIN orders o ON o.id = sol.order_id
            LEFT JOIN customers c ON c.id = s.customer_id
            LEFT JOIN LATERAL (
              SELECT sum(amount) AS ar_total, max(currency) AS currency
                FROM charges WHERE shipment_id = s.id AND side='AR' AND status<>'VOID'::charge_status
            ) ch_ar ON true
            LEFT JOIN LATERAL (
              SELECT sum(amount) AS ap_total
                FROM charges WHERE shipment_id = s.id AND side='AP' AND status<>'VOID'::charge_status
            ) ch_ap ON true
            WHERE coalesce(ch_ar.ar_total, 0) > 0 OR coalesce(ch_ap.ap_total, 0) > 0
            ORDER BY s.created_at DESC
            LIMIT ? OFFSET ?
            """, limit, offset);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM shipments s
            WHERE EXISTS (SELECT 1 FROM charges WHERE shipment_id = s.id AND status<>'VOID'::charge_status)
            """, Long.class);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    /** 利润 dashboard — 月度 / 年度 / 总览。 */
    @GetMapping("/profit-summary")
    public Map<String, Object> profitSummary() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 总 AR / AP / 利润
        BigDecimal arTotal = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges
             WHERE side='AR' AND status<>'VOID'::charge_status
            """, BigDecimal.class);
        BigDecimal apTotal = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges
             WHERE side='AP' AND status<>'VOID'::charge_status
            """, BigDecimal.class);
        stats.put("totalAr", arTotal);
        stats.put("totalAp", apTotal);
        stats.put("totalProfit", arTotal.subtract(apTotal));
        if (arTotal.signum() > 0) {
            stats.put("profitRate", arTotal.subtract(apTotal)
                .divide(arTotal, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) + "%");
        } else {
            stats.put("profitRate", "—");
        }

        // 本月
        BigDecimal arMonth = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges
             WHERE side='AR' AND status<>'VOID'::charge_status
               AND created_at >= date_trunc('month', now())
            """, BigDecimal.class);
        BigDecimal apMonth = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges
             WHERE side='AP' AND status<>'VOID'::charge_status
               AND created_at >= date_trunc('month', now())
            """, BigDecimal.class);
        stats.put("monthAr", arMonth);
        stats.put("monthAp", apMonth);
        stats.put("monthProfit", arMonth.subtract(apMonth));

        // Top 3 客户（按利润降序）
        List<Map<String, Object>> topCustomers = jdbc.queryForList("""
            SELECT c.code, c.name,
                   sum(CASE WHEN ch.side='AR' THEN ch.amount ELSE 0 END) AS ar,
                   sum(CASE WHEN ch.side='AP' THEN ch.amount ELSE 0 END) AS ap,
                   sum(CASE WHEN ch.side='AR' THEN ch.amount ELSE -ch.amount END) AS profit
              FROM charges ch
              JOIN customers c ON c.id = ch.customer_id
             WHERE ch.status<>'VOID'::charge_status
             GROUP BY c.code, c.name
             ORDER BY profit DESC LIMIT 3
            """);
        stats.put("topCustomers", topCustomers);

        return stats;
    }
}
