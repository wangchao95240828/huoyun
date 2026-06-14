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
            int rowIdx = 1;
            java.util.Set<String> seenTracking = new java.util.HashSet<>();
            while ((line = reader.readLine()) != null) {
                rowIdx++;
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length <= Math.max(idxTrack, idxAmount)) {
                    skipped.add(Map.of("row", rowIdx, "reason", "列数不足"));
                    continue;
                }
                String trackingNo = parts[idxTrack].trim();
                if (trackingNo.isEmpty()) {
                    skipped.add(Map.of("row", rowIdx, "reason", "追踪号不能为空"));
                    continue;
                }
                // ACC Cost.php L1825 派生: 文件内追踪号重复
                if (!seenTracking.add(trackingNo.toLowerCase())) {
                    skipped.add(Map.of("row", rowIdx, "trackingNo", trackingNo, "reason", "追踪号在文件内重复"));
                    continue;
                }
                BigDecimal newAmount;
                try { newAmount = new BigDecimal(parts[idxAmount].trim()); }
                catch (Exception ex) {
                    skipped.add(Map.of("row", rowIdx, "trackingNo", trackingNo, "reason", "第 " + rowIdx + " 行：金额必须为数字"));
                    continue;
                }
                // ACC Cost.php L1825: 金额必须大于零
                if (newAmount.signum() <= 0) {
                    skipped.add(Map.of("row", rowIdx, "trackingNo", trackingNo, "reason", "第 " + rowIdx + " 行：金额必须大于零"));
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
        if (ids.size() > 500) throw ApiException.badRequest("单次批量审核的数量不要超过 500 票");
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
    /** 大额付款阈值（CNY 等值）。超过需要再次确认。 */
    private static final BigDecimal LARGE_PAYMENT_THRESHOLD = new BigDecimal("50000");

    @PostMapping("/pay-supplier")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> paySupplier(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        if (ids.size() > 500) throw ApiException.badRequest("单次批量审核的数量不要超过 500 票");
        String remark = body.get("remark") == null ? "付供应商" : body.get("remark").toString();
        String fromAccountId = body.get("fromAccountId") == null ? null : body.get("fromAccountId").toString();
        boolean largeConfirmed = Boolean.TRUE.equals(body.get("largeConfirmed"));

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

        // 大额二次确认（按总金额）
        BigDecimal total = rows.stream().map(r -> (BigDecimal) r.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(LARGE_PAYMENT_THRESHOLD) >= 0 && !largeConfirmed) {
            throw ApiException.badRequest("大额付款 " + total + "，需要 largeConfirmed=true 二次确认");
        }

        // 按 currency 分组
        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
        Map<String, List<String>> chargesByCurrency = new LinkedHashMap<>();
        Map<String, String> channelByCurrency = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String currency = (String) r.get("currency");
            byCurrency.merge(currency, (BigDecimal) r.get("amount"), BigDecimal::add);
            chargesByCurrency.computeIfAbsent(currency, k -> new ArrayList<>()).add((String) r.get("id"));
            channelByCurrency.putIfAbsent(currency, r.get("channel_code") == null ? "?" : r.get("channel_code").toString());
        }

        // 标 SETTLED
        jdbc.update("""
            UPDATE charges SET settlement_status='SETTLED', paid_amount=amount
             WHERE id = ANY(?::uuid[])
            """, (Object) ids.toArray(new String[0]));

        jdbc.execute("SELECT set_config('app.current_tenant_id', '" + SINGLE_TENANT + "', true)");

        // 每个币种从 COMPANY 账户出钱
        for (Map.Entry<String, BigDecimal> e : byCurrency.entrySet()) {
            String currency = e.getKey();
            BigDecimal amount = e.getValue();
            String channelCode = channelByCurrency.get(currency);

            // 找付款账户：调用方指定的 / 该币种 COMPANY 账户
            String accountId;
            if (fromAccountId != null && !fromAccountId.isBlank()) {
                List<Map<String, Object>> accCheck = jdbc.queryForList("""
                    SELECT id::text AS id, currency, balance FROM financial_accounts
                     WHERE id = ?::uuid AND owner_type='COMPANY'
                    """, fromAccountId);
                if (accCheck.isEmpty()) throw ApiException.badRequest("付款账户不存在或不是 COMPANY 账户");
                if (!currency.equals(accCheck.get(0).get("currency"))) {
                    throw ApiException.badRequest("付款账户币种 " + accCheck.get(0).get("currency") + " 与应付币种 " + currency + " 不符");
                }
                accountId = (String) accCheck.get(0).get("id");
            } else {
                List<String> companyAccs = jdbc.queryForList("""
                    SELECT id::text FROM financial_accounts
                     WHERE owner_type='COMPANY' AND currency=? ORDER BY created_at LIMIT 1
                    """, String.class, currency);
                if (companyAccs.isEmpty()) {
                    throw ApiException.badRequest("没有 " + currency + " 的 COMPANY 账户，请先建立结算账户");
                }
                accountId = companyAccs.get(0);
            }

            BigDecimal balBefore = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id=?::uuid", BigDecimal.class, accountId);
            BigDecimal balAfter = balBefore.subtract(amount);
            jdbc.update("UPDATE financial_accounts SET balance=? WHERE id=?::uuid", balAfter, accountId);

            // 写 ledger
            String chargeIdsRef = String.join(",", chargesByCurrency.get(currency));
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'COMPANY', NULL,
                  'PAYMENT'::balance_ledger_biz_type,
                  'charges', NULL, ?,
                  ?, 'DEBIT'::balance_ledger_direction,
                  ?, ?, ?, current_user, ?
                )
                """, accountId, chargeIdsRef.length() > 200 ? channelCode : chargeIdsRef,
                     currency, amount, balBefore, balAfter,
                     remark + " (" + channelCode + ")");
        }

        return Map.of(
            "paidCount", rows.size(),
            "groupCount", byCurrency.size(),
            "groups", byCurrency,
            "totalAmount", total
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  反审 AP charges
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/unaudit-cost-charges")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> unauditCostCharges(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        if (ids.size() > 500) throw ApiException.badRequest("单次批量审核的数量不要超过 500 票");
        int n = jdbc.update("""
            UPDATE charges SET audit_status='PENDING', audited_at=NULL
             WHERE id = ANY(?::uuid[]) AND side='AP'
               AND audit_status='AUDITED' AND settlement_status='UNSETTLED'
            """, (Object) ids.toArray(new String[0]));
        return Map.of("unauditedCount", n);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  撤销供应商付款 — 反 pay-supplier
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/unsettle-payment")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> unsettlePayment(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        if (ids.size() > 500) throw ApiException.badRequest("单次批量审核的数量不要超过 500 票");
        String reason = body.get("reason") == null ? "撤销付款" : body.get("reason").toString();

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, amount, currency
              FROM charges
             WHERE id = ANY(?::uuid[]) AND side='AP' AND settlement_status='SETTLED'
            """, (Object) ids.toArray(new String[0]));
        if (rows.isEmpty()) throw ApiException.badRequest("没有可撤销的已付 AP charges");

        jdbc.update("""
            UPDATE charges SET settlement_status='UNSETTLED', paid_amount=0
             WHERE id = ANY(?::uuid[]) AND side='AP'
            """, (Object) ids.toArray(new String[0]));

        // 退回到 COMPANY 账户（反向 CREDIT）
        jdbc.execute("SELECT set_config('app.current_tenant_id', '" + SINGLE_TENANT + "', true)");
        Map<String, BigDecimal> byCur = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            byCur.merge((String) r.get("currency"), (BigDecimal) r.get("amount"), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : byCur.entrySet()) {
            String currency = e.getKey();
            BigDecimal amount = e.getValue();
            List<String> accIds = jdbc.queryForList("""
                SELECT id::text FROM financial_accounts
                 WHERE owner_type='COMPANY' AND currency=? ORDER BY created_at LIMIT 1
                """, String.class, currency);
            if (accIds.isEmpty()) continue;
            String accountId = accIds.get(0);
            BigDecimal balBefore = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id=?::uuid", BigDecimal.class, accountId);
            BigDecimal balAfter = balBefore.add(amount);
            jdbc.update("UPDATE financial_accounts SET balance=? WHERE id=?::uuid", balAfter, accountId);
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'COMPANY', NULL,
                  'VOID'::balance_ledger_biz_type,
                  'charges', NULL, NULL,
                  ?, 'CREDIT'::balance_ledger_direction,
                  ?, ?, ?, current_user, ?
                )
                """, accountId, currency, amount, balBefore, balAfter, "撤销付款: " + reason);
        }
        return Map.of("unsettledCount", rows.size(), "groups", byCur);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  AP 批量调整 / 批量作废
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/batch-void-cost")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchVoidCost(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        if (ids.size() > 500) throw ApiException.badRequest("单次批量审核的数量不要超过 500 票");
        int n = jdbc.update("""
            UPDATE charges SET status='VOID'::charge_status, audit_status='PENDING'
             WHERE id = ANY(?::uuid[]) AND side='AP' AND settlement_status='UNSETTLED'
            """, (Object) ids.toArray(new String[0]));
        return Map.of("voidedCount", n);
    }

    @PostMapping("/batch-adjust-cost")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchAdjustCost(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw ApiException.badRequest("items 必填 [{chargeId, amount}]");
        int updated = 0;
        for (Map<String, Object> item : items) {
            String chargeId = item.get("chargeId").toString();
            BigDecimal amount = new BigDecimal(item.get("amount").toString());
            int n = jdbc.update("""
                UPDATE charges SET amount=?, status='ADJUSTED'::charge_status, audit_status='PENDING'
                 WHERE id=?::uuid AND side='AP' AND settlement_status='UNSETTLED'
                   AND status<>'VOID'::charge_status
                """, amount, chargeId);
            updated += n;
        }
        return Map.of("adjustedCount", updated);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  公司资金账户管理 (COMPANY)
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/company-accounts")
    public Map<String, Object> companyAccounts() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, account_name, account_type, currency, balance, is_show, created_at
              FROM financial_accounts
             WHERE owner_type='COMPANY'
             ORDER BY currency, created_at
            """);
        return Map.of("data", rows);
    }

    @PostMapping("/company-accounts")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createCompanyAccount(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("accountName");
        String currency = (String) body.get("currency");
        String type = body.get("accountType") == null ? "CASH" : body.get("accountType").toString();
        BigDecimal initBalance = body.get("balance") == null
            ? BigDecimal.ZERO : new BigDecimal(body.get("balance").toString());
        if (name == null || name.isBlank()) throw ApiException.badRequest("accountName 必填");
        if (currency == null || currency.length() != 3) throw ApiException.badRequest("currency 必填(ISO 3 字母)");
        jdbc.execute("SELECT set_config('app.current_tenant_id', '" + SINGLE_TENANT + "', true)");
        String id = jdbc.queryForObject("""
            INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name, account_type, currency, balance, source, is_show)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'COMPANY', NULL, ?, ?, ?, ?, 'LOCAL', true)
            RETURNING id::text
            """, String.class, name, type, currency, initBalance);
        return Map.of("id", id, "accountName", name, "currency", currency, "balance", initBalance);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  供应商对账单 CSV 导出
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/export-supplier-statement")
    public org.springframework.http.ResponseEntity<byte[]> exportSupplierStatement(
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String currency) {
        StringBuilder sql = new StringBuilder("""
            SELECT ct.tracking_no, c.code AS channel_code, c.name AS channel_name,
                   ch.amount, ch.paid_amount, ch.currency,
                   ch.status::text AS status, ch.audit_status, ch.settlement_status,
                   ch.created_at, o.order_no
              FROM charges ch
              LEFT JOIN shipments s ON s.id = ch.shipment_id
              LEFT JOIN cartons ct ON ct.shipment_id = ch.shipment_id
              LEFT JOIN channels c ON c.id = s.channel_id
              LEFT JOIN orders o ON o.id = ch.order_id
             WHERE ch.side='AP' AND ch.status<>'VOID'::charge_status
            """);
        List<Object> params = new ArrayList<>();
        if (channelCode != null) { sql.append(" AND c.code=?"); params.add(channelCode); }
        if (currency != null) { sql.append(" AND ch.currency=?"); params.add(currency); }
        sql.append(" ORDER BY ch.created_at DESC LIMIT 5000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        StringBuilder csv = new StringBuilder();
        csv.append("tracking_no,channel_code,channel_name,order_no,amount,paid_amount,currency,status,audit_status,settlement_status,created_at\n");
        for (Map<String, Object> r : rows) {
            csv.append(r.getOrDefault("tracking_no", "")).append(",")
               .append(r.getOrDefault("channel_code", "")).append(",")
               .append("\"" + String.valueOf(r.getOrDefault("channel_name", "")).replace("\"","\"\"") + "\"").append(",")
               .append(r.getOrDefault("order_no", "")).append(",")
               .append(r.getOrDefault("amount", "")).append(",")
               .append(r.getOrDefault("paid_amount", "")).append(",")
               .append(r.getOrDefault("currency", "")).append(",")
               .append(r.getOrDefault("status", "")).append(",")
               .append(r.getOrDefault("audit_status", "")).append(",")
               .append(r.getOrDefault("settlement_status", "")).append(",")
               .append(r.getOrDefault("created_at", "")).append("\n");
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Type", "text/csv; charset=utf-8")
            .header("Content-Disposition", "attachment; filename=supplier-statement-" + LocalDate.now() + ".csv")
            .body(body);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  账龄分析 AR / AP — 30/60/90+ 分桶
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/aging")
    public Map<String, Object> aging() {
        // 输出形态对齐表格 {data, total}，加 side 字段区分 AR / AP
        List<Map<String, Object>> all = jdbc.queryForList("""
            SELECT 'AR' AS side,
              CASE
                WHEN (now() - issued_at) < interval '30 days' THEN '0-30'
                WHEN (now() - issued_at) < interval '60 days' THEN '31-60'
                WHEN (now() - issued_at) < interval '90 days' THEN '61-90'
                ELSE '90+'
              END AS bucket,
              currency,
              count(*) AS row_count,
              coalesce(sum(total_amount - coalesce(paid_amount, 0)), 0) AS unpaid_amount
            FROM customer_invoices
            WHERE status IN ('SENT', 'PENDING', 'PARTIAL_PAID')
            GROUP BY bucket, currency
            UNION ALL
            SELECT 'AP' AS side,
              CASE
                WHEN (now() - created_at) < interval '30 days' THEN '0-30'
                WHEN (now() - created_at) < interval '60 days' THEN '31-60'
                WHEN (now() - created_at) < interval '90 days' THEN '61-90'
                ELSE '90+'
              END AS bucket,
              currency,
              count(*) AS row_count,
              coalesce(sum(amount), 0) AS unpaid_amount
            FROM charges
            WHERE side='AP' AND status<>'VOID'::charge_status AND settlement_status='UNSETTLED'
            GROUP BY bucket, currency
            ORDER BY side, bucket, currency
            """);
        return Map.of("data", all, "total", all.size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  月度财报 — 按月汇总 AR/AP/利润
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/monthly-report")
    public Map<String, Object> monthlyReport(@RequestParam(required = false) Integer year) {
        int y = year == null ? java.time.Year.now().getValue() : year;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month,
                   currency,
                   sum(CASE WHEN side='AR' THEN amount ELSE 0 END) AS ar,
                   sum(CASE WHEN side='AP' THEN amount ELSE 0 END) AS ap,
                   sum(CASE WHEN side='AR' THEN amount ELSE -amount END) AS profit,
                   count(DISTINCT shipment_id) AS shipment_count
              FROM charges
             WHERE status<>'VOID'::charge_status
               AND extract(year FROM created_at) = ?
             GROUP BY month, currency
             ORDER BY month, currency
            """, y);
        return Map.of("year", y, "data", rows, "total", rows.size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  业绩提成 — 按订单利润 × 业务员 % 生成
    // ═════════════════════════════════════════════════════════════════════════
    @PostMapping("/commissions/generate")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateCommissions(@RequestBody Map<String, Object> body) {
        BigDecimal rate;
        try { rate = new BigDecimal(body.getOrDefault("rate", "0.05").toString()); }
        catch (Exception ex) { throw ApiException.badRequest("rate 必须为数字（默认 0.05 = 5%）"); }
        String month = body.get("month") == null
            ? java.time.YearMonth.now().toString() : body.get("month").toString();
        // 扫描该月有 SETTLED AP 的 shipment，按业务员聚合利润
        List<Map<String, Object>> aggregated = jdbc.queryForList("""
            SELECT c.salesman_id::text AS employee_id, ch.currency,
                   sum(CASE WHEN ch.side='AR' THEN ch.amount ELSE -ch.amount END) AS profit_amount,
                   sum(CASE WHEN ch.side='AR' THEN ch.amount ELSE 0 END) AS sales_amount,
                   count(DISTINCT ch.shipment_id) AS shipment_count
              FROM charges ch
              JOIN customers c ON c.id = ch.customer_id
             WHERE ch.status<>'VOID'::charge_status
               AND c.salesman_id IS NOT NULL
               AND to_char(date_trunc('month', ch.created_at), 'YYYY-MM') = ?
             GROUP BY c.salesman_id, ch.currency
             HAVING sum(CASE WHEN ch.side='AR' THEN ch.amount ELSE -ch.amount END) > 0
            """, month);
        int generated = 0;
        for (Map<String, Object> row : aggregated) {
            // 同月同业务员同币种已有则跳过
            Integer dup = jdbc.queryForObject("""
                SELECT count(*) FROM acc_commissions
                 WHERE employee_id=?::uuid AND the_month=? AND currency=?
                """, Integer.class, row.get("employee_id"), month, row.get("currency"));
            if (dup != null && dup > 0) continue;
            BigDecimal profit = (BigDecimal) row.get("profit_amount");
            BigDecimal commission = profit.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
            jdbc.update("""
                INSERT INTO acc_commissions
                  (tenant_id, employee_id, the_month, amount, currency, sales_amount, profit_amount, status, audit_status, remark)
                VALUES
                  (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?, ?, ?, 'PENDING', 'PENDING', ?)
                """, row.get("employee_id"), month, commission, row.get("currency"),
                     row.get("sales_amount"), profit,
                     "自动生成: " + month + " 利润 × " + rate);
            generated++;
        }
        return Map.of("month", month, "rate", rate, "generated", generated);
    }

    @GetMapping("/commissions")
    public Map<String, Object> listCommissions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        Long total = jdbc.queryForObject("SELECT count(*) FROM acc_commissions", Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT co.id::text AS id, co.employee_id::text AS employee_id,
                   e.name AS employee_name, e.emp_no AS employee_code,
                   co.the_month, co.amount, co.currency, co.sales_amount, co.profit_amount,
                   co.status, co.audit_status, co.audited_at, co.audit_name, co.remark, co.created_at
              FROM acc_commissions co
              LEFT JOIN acc_employees e ON e.id = co.employee_id
             ORDER BY co.created_at DESC LIMIT ? OFFSET ?
            """, limit, offset);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    @PostMapping("/commissions/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approveCommission(@PathVariable String id) {
        int n = jdbc.update("""
            UPDATE acc_commissions SET audit_status='AUDITED', audited_at=now(),
                   audit_name=current_user
             WHERE id=?::uuid AND audit_status='PENDING'
            """, id);
        return Map.of("approved", n);
    }

    @PostMapping("/commissions/{id}/pay")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payCommission(@PathVariable String id) {
        Map<String, Object> com = jdbc.queryForMap("""
            SELECT employee_id::text AS employee_id, amount, currency, audit_status, status
              FROM acc_commissions WHERE id=?::uuid
            """, id);
        if (!"AUDITED".equals(com.get("audit_status"))) throw ApiException.badRequest("未审核不能付");
        if ("PAID".equals(com.get("status"))) throw ApiException.badRequest("已付，请勿重复");
        jdbc.update("UPDATE acc_commissions SET status='PAID' WHERE id=?::uuid", id);
        return Map.of("paid", true, "amount", com.get("amount"), "currency", com.get("currency"));
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
