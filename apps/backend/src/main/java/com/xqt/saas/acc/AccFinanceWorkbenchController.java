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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

        // charges 表无 invoice_id 列，账单关联走 customer_invoice_lines 中间表
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              CASE
                WHEN il.invoice_id IS NULL AND ch.status = 'ESTIMATED'::charge_status THEN 'prepay'
                WHEN il.invoice_id IS NULL AND ch.status = 'ADJUSTED'::charge_status  THEN 'pending'
                WHEN il.invoice_id IS NOT NULL                                         THEN 'invoiced'
                ELSE 'other'
              END                                              AS bucket,
              count(*)                                         AS row_count,
              coalesce(sum(ch.amount), 0)                      AS total_amount,
              coalesce(sum(ch.amount - ch.paid_amount), 0)     AS unpaid_amount
            FROM charges ch
            LEFT JOIN customer_invoice_lines il ON il.charge_id = ch.id
            WHERE ch.side = 'AR'
              AND ch.status <> 'VOID'::charge_status
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
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
            JOIN customer_invoice_lines il ON il.charge_id = ch.id
            WHERE ch.side = 'AR' AND ch.status <> 'VOID'::charge_status
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
            JOIN customer_invoice_lines il ON il.charge_id = ch.id
            JOIN customer_invoices ci      ON ci.id = il.invoice_id
            LEFT JOIN orders o    ON o.id  = ch.order_id
            LEFT JOIN customers c ON c.id  = ch.customer_id
            WHERE ch.side = 'AR' AND ch.status <> 'VOID'::charge_status
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
              AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
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
              AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            ORDER BY ch.created_at DESC
            LIMIT ? OFFSET ?
            """, status, custFilter, custFilter, curFilter, curFilter, limit, offset);

        return AccPaging.result(rows, total == null ? 0 : total);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  阶段 ① → ② 单条调整：实际成本出来后修改 charges.amount + status=ADJUSTED
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/charges/{id}/adjust")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adjust(@PathVariable String id, @RequestBody Map<String, Object> body) {
        BigDecimal newAmount;
        try {
            newAmount = new BigDecimal(body.get("amount").toString());
        } catch (Exception ex) {
            throw ApiException.badRequest("amount 必填且为数字");
        }
        String reason = body.get("reason") == null ? null : body.get("reason").toString();

        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap(
                "SELECT amount, status::text AS status FROM charges WHERE id = ?::uuid", id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("charge 不存在: " + id);
        }
        if ("VOID".equals(ch.get("status"))) {
            throw ApiException.badRequest("已作废的 charge 不能调整");
        }
        if (EXISTS_INVOICE_LINE(id)) {
            throw ApiException.badRequest("已出账的 charge 不能调整（需先反审账单）");
        }

        BigDecimal oldAmount = (BigDecimal) ch.get("amount");
        jdbc.update("""
            UPDATE charges SET amount = ?, status = 'ADJUSTED'::charge_status, audit_status = 'PENDING'
             WHERE id = ?::uuid
            """, newAmount, id);

        // 记一笔 audit_events，给"调整前/后"留痕
        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'charges', ?, 'UPDATE', current_user,
                    jsonb_build_object('amount', ?, 'status', ?),
                    jsonb_build_object('amount', ?, 'status', 'ADJUSTED'),
                    ?)
            """, id, oldAmount, ch.get("status"), newAmount, reason);

        return Map.of(
            "id", id,
            "oldAmount", oldAmount,
            "newAmount", newAmount,
            "diff", newAmount.subtract(oldAmount),
            "status", "ADJUSTED"
        );
    }

    private boolean EXISTS_INVOICE_LINE(String chargeId) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM customer_invoice_lines WHERE charge_id = ?::uuid", Long.class, chargeId);
        return n != null && n > 0;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  阶段 ② → ③ 批量审核 + 生成账单：把选中的 charges 合一期 customer_invoice
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/audit-and-invoice")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> auditAndInvoice(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("chargeIds 必填");
        }
        String invoiceDate = body.get("invoiceDate") == null ? LocalDate.now().toString()
                                                              : body.get("invoiceDate").toString();

        // ① 收集 charges 信息，按 customer+currency 分组（同组同期）
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, customer_id::text AS customer_id, currency,
                   amount, status::text AS status, audit_status
              FROM charges WHERE id = ANY(?::uuid[])
            """, (Object) ids.toArray(new String[0]));
        if (rows.size() != ids.size()) {
            throw ApiException.badRequest("部分 charge 不存在");
        }
        // 业务校验
        for (Map<String, Object> r : rows) {
            if ("VOID".equals(r.get("status"))) {
                throw ApiException.badRequest("含已作废 charge: " + r.get("id"));
            }
            if (EXISTS_INVOICE_LINE((String) r.get("id"))) {
                throw ApiException.badRequest("含已出账 charge: " + r.get("id"));
            }
        }

        Map<String, List<Map<String, Object>>> byCustCur = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = r.get("customer_id") + "|" + r.get("currency");
            byCustCur.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // ② 每组生成一期账单
        List<Map<String, Object>> created = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byCustCur.entrySet()) {
            String customerId = e.getKey().split("\\|")[0];
            String currency = e.getKey().split("\\|")[1];
            List<Map<String, Object>> group = e.getValue();
            BigDecimal total = group.stream()
                .map(r -> (BigDecimal) r.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 生成账单编号 CINV-YYYYMM-NNNN
            String yyyymm = LocalDate.parse(invoiceDate).toString().substring(0, 7).replace("-", "");
            Integer seq = jdbc.queryForObject("SELECT nextval('cinv_invoice_seq')", Integer.class);
            String invoiceNo = String.format("CINV-%s-%04d", yyyymm, seq);

            // 上期未付带入（best-effort：失败则当作 0）
            // 注：customer_invoices 在 @Transactional 内查可能撞 RLS / method-security 拦截
            BigDecimal prevBalance = BigDecimal.ZERO;
            try {
                List<BigDecimal> prev = jdbc.queryForList("""
                    SELECT coalesce(unpaid_amount, 0) FROM customer_invoices
                     WHERE customer_id = ?::uuid AND currency = ?
                     ORDER BY issued_at DESC LIMIT 1
                    """, BigDecimal.class, customerId, currency);
                if (!prev.isEmpty() && prev.get(0) != null) prevBalance = prev.get(0);
            } catch (Exception ignored) {
                // 首次出账 / 拒访问 → prev=0，不阻断主流程
            }

            String invoiceId = jdbc.queryForObject("""
                INSERT INTO customer_invoices (
                  tenant_id, customer_id, invoice_no, currency,
                  total_amount, paid_amount, unpaid_amount,
                  line_count, previous_balance, status, invoice_date, issued_at
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid,
                  ?::uuid, ?, ?, ?,
                  0, ?,
                  ?, ?, 'DRAFT', ?::date, now()
                ) RETURNING id::text
                """, String.class,
                customerId, invoiceNo, currency, total, total,
                group.size(), prevBalance, invoiceDate);

            // 关联 charges → invoice_lines
            for (Map<String, Object> r : group) {
                jdbc.update("""
                    INSERT INTO customer_invoice_lines (tenant_id, invoice_id, charge_id, amount)
                    VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid, ?)
                    """, invoiceId, r.get("id"), r.get("amount"));
            }

            // 一审通过这些 charges
            jdbc.update("""
                UPDATE charges SET audit_status = 'AUDITED', audited_at = now()
                 WHERE id = ANY(?::uuid[])
                """, (Object) group.stream().map(r -> (String) r.get("id")).toArray(String[]::new));

            created.add(Map.of(
                "invoiceId", invoiceId,
                "invoiceNo", invoiceNo,
                "customerId", customerId,
                "currency", currency,
                "totalAmount", total,
                "lineCount", group.size(),
                "previousBalance", prevBalance
            ));
        }

        return Map.of(
            "ok", true,
            "invoiceCount", created.size(),
            "chargeCount", rows.size(),
            "invoices", created
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UPS / FedEx 实际账单 CSV 导入 → 批量调金额
    //  CSV 格式（首行 header）：tracking_no,actual_amount,currency
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/import-actual-bill")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importActualBill(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("文件必填");
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw ApiException.badRequest("CSV 空文件");
            String[] cols = header.toLowerCase().split(",");
            int idxTrack = -1, idxAmount = -1, idxCurrency = -1;
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i].trim();
                if (c.equals("tracking_no") || c.equals("tracking")) idxTrack = i;
                else if (c.equals("actual_amount") || c.equals("amount")) idxAmount = i;
                else if (c.equals("currency")) idxCurrency = i;
            }
            if (idxTrack < 0 || idxAmount < 0) {
                throw ApiException.badRequest("CSV 必须包含 tracking_no,actual_amount[,currency] 三列");
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
                String currency = idxCurrency >= 0 && parts.length > idxCurrency
                    ? parts[idxCurrency].trim() : null;

                // 按 tracking_no 找 charge (走 cartons 表)
                List<Map<String, Object>> chRows = jdbc.queryForList("""
                    SELECT ch.id::text AS id, ch.amount, ch.currency, ch.status::text AS status
                      FROM charges ch
                      JOIN cartons ct ON ct.shipment_id = ch.shipment_id
                     WHERE ct.tracking_no = ?
                       AND ch.side = 'AR'
                       AND ch.status IN ('ESTIMATED'::charge_status, 'ADJUSTED'::charge_status)
                       AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
                    """, trackingNo);
                if (chRows.isEmpty()) {
                    skipped.add(Map.of("trackingNo", trackingNo, "reason", "找不到匹配的可调整 charge"));
                    continue;
                }
                // 累加可能多条（一个运单多个 charge_item）— 此处只调整 FREIGHT，简化为按差额按比例
                // KISS：取第一条调整。后续可扩展
                Map<String, Object> ch = chRows.get(0);
                BigDecimal oldAmount = (BigDecimal) ch.get("amount");
                jdbc.update("""
                    UPDATE charges SET amount = ?, status = 'ADJUSTED'::charge_status, audit_status = 'PENDING'
                     WHERE id = ?::uuid
                    """, newAmount, ch.get("id"));
                matched.add(Map.of(
                    "trackingNo", trackingNo,
                    "chargeId", ch.get("id"),
                    "oldAmount", oldAmount,
                    "newAmount", newAmount,
                    "diff", newAmount.subtract(oldAmount)
                ));
            }
        }

        return Map.of(
            "matched", matched.size(),
            "skipped", skipped.size(),
            "matchedDetails", matched,
            "skippedDetails", skipped
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  预扣明细 CSV 导出（客户端给客户下载对账用）
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping("/prepay-details/export")
    public ResponseEntity<byte[]> exportPrepayDetails(
        @RequestParam(required = false) String customerId,
        @RequestParam(required = false) String currency
    ) {
        String custFilter = customerId == null || customerId.isBlank() ? null : customerId;
        String curFilter = currency == null || currency.isBlank() ? null : currency;

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              o.order_no                AS order_no,
              c.code                    AS customer_code,
              c.name                    AS customer_name,
              ch.amount                 AS amount,
              ch.currency               AS currency,
              ch.status::text           AS status,
              to_char(ch.created_at, 'YYYY-MM-DD HH24:MI:SS') AS created_at
            FROM charges ch
            LEFT JOIN orders o    ON o.id  = ch.order_id
            LEFT JOIN customers c ON c.id  = ch.customer_id
            WHERE ch.side = 'AR' AND ch.status = 'ESTIMATED'::charge_status
              AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
              AND (?::text IS NULL OR ch.customer_id = ?::uuid)
              AND (?::text IS NULL OR ch.currency = ?)
            ORDER BY ch.created_at DESC
            """, custFilter, custFilter, curFilter, curFilter);

        StringBuilder csv = new StringBuilder();
        csv.append("订单号,客户编码,客户名称,扣款金额,币种,状态,扣款时间\n");
        for (Map<String, Object> r : rows) {
            csv.append(escape(r.get("order_no"))).append(',');
            csv.append(escape(r.get("customer_code"))).append(',');
            csv.append(escape(r.get("customer_name"))).append(',');
            csv.append(r.get("amount")).append(',');
            csv.append(escape(r.get("currency"))).append(',');
            csv.append(escape(r.get("status"))).append(',');
            csv.append(escape(r.get("created_at"))).append('\n');
        }
        byte[] body = ("﻿" + csv.toString()).getBytes(StandardCharsets.UTF_8); // UTF-8 BOM 让 Excel 正常打开
        String filename = "prepay_details_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .body(body);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  客户账户余额三段公式：可打单余额 + 预扣明细 = 总账户
    //  对应用户描述："充值 200 → 预扣 10 → 可打单 190 / 预扣 10 / 总 200"
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping("/customer-balance")
    public Map<String, Object> customerBalance(
        @RequestParam String customerId,
        @RequestParam(required = false, defaultValue = "USD") String currency
    ) {
        // 1) 可打单余额：financial_accounts.balance
        BigDecimal usable = BigDecimal.ZERO;
        String accountId = null;
        try {
            Map<String, Object> acc = jdbc.queryForMap("""
                SELECT id::text AS id, balance FROM financial_accounts
                 WHERE owner_type='CUSTOMER' AND owner_id = ?::uuid AND currency = ?
                 LIMIT 1
                """, customerId, currency);
            usable = (BigDecimal) acc.get("balance");
            accountId = (String) acc.get("id");
        } catch (DataAccessException ignored) {
            // 客户无账户 → usable=0
        }

        // 2) 预扣明细余额：未出账 ESTIMATED 的 charges 合计
        BigDecimal prepay = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges ch
            WHERE ch.side='AR' AND ch.status='ESTIMATED'::charge_status
              AND ch.customer_id = ?::uuid AND ch.currency = ?
              AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
            """, BigDecimal.class, customerId, currency);
        if (prepay == null) prepay = BigDecimal.ZERO;

        // 3) 已出账未付：customer_invoices.unpaid_amount 合计
        BigDecimal invoicedUnpaid = jdbc.queryForObject("""
            SELECT coalesce(sum(unpaid_amount), 0) FROM customer_invoices
             WHERE customer_id = ?::uuid AND currency = ? AND status <> 'VOID'
            """, BigDecimal.class, customerId, currency);
        if (invoicedUnpaid == null) invoicedUnpaid = BigDecimal.ZERO;

        BigDecimal totalAccount = usable.add(prepay);

        return Map.of(
            "customerId", customerId,
            "currency", currency,
            "accountId", accountId == null ? "" : accountId,
            "usableBalance", usable,         // 可打单余额（财务账户余额）
            "prepayDeductions", prepay,      // 预扣明细余额（已扣未出账）
            "totalAccount", totalAccount,    // 总账户 = 可打单 + 预扣
            "invoicedUnpaid", invoicedUnpaid // 已出账未付（独立于上面）
        );
    }

    /** balance_ledger + invoice 时间线（统一时间轴）。 */
    @GetMapping("/customer-balance-history")
    public Map<String, Object> customerBalanceHistory(
        @RequestParam String customerId,
        @RequestParam(required = false, defaultValue = "USD") String currency,
        @RequestParam(required = false, defaultValue = "30") Integer limit
    ) {
        if (limit == null || limit < 1) limit = 30;
        if (limit > 200) limit = 200;

        // balance_ledger（流水）
        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT
              bl.created_at,
              bl.biz_type::text AS biz_type,
              bl.direction::text AS direction,
              bl.amount,
              bl.balance_before,
              bl.balance_after,
              bl.source_ref,
              bl.operator,
              bl.remark
            FROM balance_ledger bl
            WHERE bl.owner_type='CUSTOMER' AND bl.owner_id = ?::uuid
              AND bl.currency = ?
            ORDER BY bl.created_at DESC
            LIMIT ?
            """, customerId, currency, limit);

        return Map.of("data", ledger);
    }

    private static String escape(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
