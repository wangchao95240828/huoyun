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
    private static final String SINGLE_TENANT = "2bda8c16-7b19-4ce6-ab71-9584f5a140ed";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final com.xqt.saas.finance.FxSnapshotCapture fxCapture;

    public AccFinanceWorkbenchController(JdbcTemplate jdbc, JsonSupport json,
                                         com.xqt.saas.finance.FxSnapshotCapture fxCapture) {
        this.jdbc = jdbc;
        this.json = json;
        this.fxCapture = fxCapture;
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
              ci.id::text          AS invoice_id,
              ci.status            AS invoice_status,
              ci.total_amount      AS invoice_total,
              ci.paid_amount       AS invoice_paid,
              ci.unpaid_amount     AS invoice_unpaid,
              ci.verify_status     AS invoice_verify_status
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
            ch = jdbc.queryForMap("""
                SELECT amount, status::text AS status, currency,
                       customer_id::text AS customer_id, order_id::text AS order_id
                  FROM charges WHERE id = ?::uuid
                """, id);
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
        BigDecimal diff = newAmount.subtract(oldAmount);
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

        // balance_ledger ADJUST 差额冲账：参考 ACC CAdjust 审核入账
        // diff > 0 → 客户欠更多（DEBIT 余额减）；diff < 0 → 客户欠更少（CREDIT 加回）
        if (diff.signum() != 0) {
            writeLedger(
                (String) ch.get("customer_id"), (String) ch.get("currency"),
                diff.abs(),
                diff.signum() > 0 ? "DEBIT" : "CREDIT",
                "ADJUST",
                "charges", id, (String) ch.get("order_id"),
                "system",
                "调整 " + oldAmount + " → " + newAmount + (reason == null ? "" : " (" + reason + ")")
            );
        }

        return Map.of(
            "id", id,
            "oldAmount", oldAmount,
            "newAmount", newAmount,
            "diff", diff,
            "status", "ADJUSTED"
        );
    }

    private boolean EXISTS_INVOICE_LINE(String chargeId) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM customer_invoice_lines WHERE charge_id = ?::uuid", Long.class, chargeId);
        return n != null && n > 0;
    }

    /**
     * 通用 helper：给客户找/建影子账户，写一笔 balance_ledger 流水并更新余额。
     * 参考 ACC CAdjust.php / Pay.php：每次 charge / invoice 状态变都要有对账流水。
     *
     * @param direction "DEBIT" (扣余额) / "CREDIT" (加余额)
     * @param bizType   PREPAY / ADJUST / RECEIPT / VOID (balance_ledger_biz_type enum)
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean writeLedger(String customerId, String currency,
                                 BigDecimal amount, String direction, String bizType,
                                 String sourceType, String sourceId, String sourceRef,
                                 String operator, String remark) {
        if (amount == null || amount.signum() == 0) return false;
        try {
            // 确保 RLS session 变量已设（@Transactional 嵌套调用时可能丢失）
            jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");
            // 找/建影子账户（queryForList 处理 0 行场景，避免 EmptyResultDataAccessException 被外层 catch 吞掉）
            List<String> accIds = jdbc.queryForList("""
                SELECT id::text FROM financial_accounts
                 WHERE owner_type='CUSTOMER' AND owner_id=?::uuid AND currency=? LIMIT 1
                """, String.class, customerId, currency);
            String accountId;
            if (accIds.isEmpty()) {
                accountId = jdbc.queryForObject("""
                    INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                    account_type, currency, balance, source, is_show)
                    VALUES (current_setting('app.current_tenant_id')::uuid, 'CUSTOMER', ?::uuid, ?, 'CASH', ?, 0, 'LOCAL', true)
                    RETURNING id::text
                    """, String.class, customerId, "客户预扣账户", currency);
            } else {
                accountId = accIds.get(0);
            }
            BigDecimal balBefore = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id=?::uuid", BigDecimal.class, accountId);
            BigDecimal signed = "DEBIT".equals(direction) ? amount.negate() : amount;
            BigDecimal balAfter = balBefore.add(signed);

            jdbc.update("UPDATE financial_accounts SET balance = ? WHERE id = ?::uuid", balAfter, accountId);
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'CUSTOMER', ?::uuid, ?::balance_ledger_biz_type, ?, ?::uuid, ?,
                  ?, ?::balance_ledger_direction, ?, ?, ?, ?, ?
                )
                """, accountId, customerId, bizType, sourceType, sourceId, sourceRef,
                     currency, direction, amount, balBefore, balAfter, operator, remark);
            // FX 快照：ledger.currency != tenant.base_currency 时自动写一条
            try {
                fxCapture.captureForLedger(SINGLE_TENANT, currency, bizType, sourceType, sourceRef);
            } catch (Exception ignored) {}
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  阶段 ② → ③ 批量审核 + 生成账单：把选中的 charges 合一期 customer_invoice
    // ════════════════════════════════════════════════════════════════════════
    // ════════════════════════════════════════════════════════════════════════
    //  一审通过（只 AUDIT 不出账）— ACC 流程：会计审核 → audit_status=AUDITED
    //  后续主管再单独发起 create-invoice 出账
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/audit-charges")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> auditCharges(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        int n = jdbc.update("""
            UPDATE charges SET audit_status = 'AUDITED', audited_at = now()
             WHERE id = ANY(?::uuid[])
               AND audit_status = 'PENDING'
               AND status <> 'VOID'::charge_status
            """, (Object) ids.toArray(new String[0]));
        return Map.of("auditedCount", n);
    }

    /** 反一审 — AUDITED → PENDING（出账过的不允许）。 */
    @PostMapping("/unaudit-charges")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> unauditCharges(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        // 出账过的不允许反一审
        Long invoiced = jdbc.queryForObject("""
            SELECT count(*) FROM customer_invoice_lines WHERE charge_id = ANY(?::uuid[])
            """, Long.class, (Object) ids.toArray(new String[0]));
        if (invoiced != null && invoiced > 0) {
            throw ApiException.badRequest("含已出账的 charge，请先作废账单");
        }
        int n = jdbc.update("""
            UPDATE charges SET audit_status = 'PENDING', audited_at = NULL
             WHERE id = ANY(?::uuid[])
               AND audit_status = 'AUDITED'
            """, (Object) ids.toArray(new String[0]));
        return Map.of("unauditedCount", n);
    }

    /** 把已 AUDITED 但未出账的 charges 合一期账单（不再做一审，只出账）。 */
    @PostMapping("/create-invoice")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> createInvoice(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("chargeIds");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("chargeIds 必填");
        // 校验：全部 AUDITED，未出账，非 VOID
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, customer_id::text AS customer_id, currency, amount,
                   status::text AS status, audit_status
              FROM charges WHERE id = ANY(?::uuid[])
            """, (Object) ids.toArray(new String[0]));
        if (rows.size() != ids.size()) throw ApiException.badRequest("部分 charge 不存在");
        for (Map<String, Object> r : rows) {
            if (!"AUDITED".equals(r.get("audit_status"))) {
                throw ApiException.badRequest("含未一审通过 charge: " + r.get("id"));
            }
            if (EXISTS_INVOICE_LINE((String) r.get("id"))) {
                throw ApiException.badRequest("含已出账 charge: " + r.get("id"));
            }
        }
        return generateInvoicesByCustomer(rows, body.get("invoiceDate") == null
            ? LocalDate.now().toString() : body.get("invoiceDate").toString());
    }

    /** 共用：把 charges 按 customer+currency 分组生成 customer_invoices + lines。 */
    private Map<String, Object> generateInvoicesByCustomer(List<Map<String, Object>> rows, String invoiceDate) {
        Map<String, List<Map<String, Object>>> byCustCur = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = r.get("customer_id") + "|" + r.get("currency");
            byCustCur.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byCustCur.entrySet()) {
            String customerId = e.getKey().split("\\|")[0];
            String currency = e.getKey().split("\\|")[1];
            List<Map<String, Object>> group = e.getValue();
            BigDecimal total = group.stream()
                .map(r -> (BigDecimal) r.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            String yyyymm = LocalDate.parse(invoiceDate).toString().substring(0, 7).replace("-", "");
            Integer seq = jdbc.queryForObject("SELECT nextval('cinv_invoice_seq')", Integer.class);
            String invoiceNo = String.format("CINV-%s-%04d", yyyymm, seq);

            BigDecimal prevBalance = BigDecimal.ZERO;
            try {
                List<BigDecimal> prev = jdbc.queryForList("""
                    SELECT coalesce(unpaid_amount, 0) FROM customer_invoices
                     WHERE customer_id = ?::uuid AND currency = ? AND status <> 'VOID'
                     ORDER BY issued_at DESC LIMIT 1
                    """, BigDecimal.class, customerId, currency);
                if (!prev.isEmpty() && prev.get(0) != null) prevBalance = prev.get(0);
            } catch (Exception ignored) {}

            String invoiceId = jdbc.queryForObject("""
                INSERT INTO customer_invoices (
                  tenant_id, customer_id, invoice_no, currency,
                  total_amount, paid_amount, unpaid_amount,
                  line_count, previous_balance, status, invoice_date, issued_at
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid,
                  ?::uuid, ?, ?, ?, 0, ?, ?, ?, 'DRAFT', ?::date, now()
                ) RETURNING id::text
                """, String.class, customerId, invoiceNo, currency, total, total,
                     group.size(), prevBalance, invoiceDate);
            for (Map<String, Object> r : group) {
                jdbc.update("""
                    INSERT INTO customer_invoice_lines (tenant_id, invoice_id, charge_id, amount)
                    VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid, ?)
                    """, invoiceId, r.get("id"), r.get("amount"));
            }
            created.add(Map.of(
                "invoiceId", invoiceId, "invoiceNo", invoiceNo,
                "customerId", customerId, "currency", currency,
                "totalAmount", total, "lineCount", group.size(),
                "previousBalance", prevBalance
            ));
        }
        return Map.of("ok", true, "invoiceCount", created.size(), "chargeCount", rows.size(), "invoices", created);
    }

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

                // 按 tracking_no 找 charges (走 cartons 表) — 一个 shipment 可能多 charge_item（FREIGHT/FUEL/...）
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
                // 多 charge 按原 amount 比例分配新总额；只有 1 条直接覆盖
                BigDecimal oldTotal = chRows.stream()
                    .map(r -> (BigDecimal) r.get("amount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (oldTotal.signum() == 0) {
                    // 原金额为 0 → 全部加给第一条
                    Map<String, Object> first = chRows.get(0);
                    jdbc.update("""
                        UPDATE charges SET amount = ?, status = 'ADJUSTED'::charge_status, audit_status = 'PENDING'
                         WHERE id = ?::uuid
                        """, newAmount, first.get("id"));
                    matched.add(Map.of(
                        "trackingNo", trackingNo, "chargeIds", List.of(first.get("id")),
                        "oldAmount", BigDecimal.ZERO, "newAmount", newAmount,
                        "diff", newAmount, "rule", "first-only(old=0)"
                    ));
                } else {
                    List<String> updatedIds = new ArrayList<>();
                    BigDecimal allocated = BigDecimal.ZERO;
                    for (int i = 0; i < chRows.size(); i++) {
                        Map<String, Object> r = chRows.get(i);
                        BigDecimal oldA = (BigDecimal) r.get("amount");
                        BigDecimal newA;
                        if (i == chRows.size() - 1) {
                            // 最后一条吃掉余数，保证总额精确
                            newA = newAmount.subtract(allocated);
                        } else {
                            newA = newAmount.multiply(oldA).divide(oldTotal, 2, java.math.RoundingMode.HALF_UP);
                            allocated = allocated.add(newA);
                        }
                        jdbc.update("""
                            UPDATE charges SET amount = ?, status = 'ADJUSTED'::charge_status, audit_status = 'PENDING'
                             WHERE id = ?::uuid
                            """, newA, r.get("id"));
                        updatedIds.add((String) r.get("id"));
                    }
                    matched.add(Map.of(
                        "trackingNo", trackingNo, "chargeIds", updatedIds,
                        "oldAmount", oldTotal, "newAmount", newAmount,
                        "diff", newAmount.subtract(oldTotal),
                        "rule", chRows.size() + "-charges-pro-rated"
                    ));
                }
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
    //  charge 审计历史 — 给行内"时间线"按钮用
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping("/charges/{id}/audit-history")
    public Map<String, Object> chargeAuditHistory(@PathVariable String id) {
        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT occurred_at, action, actor_name, before_state, after_state, remark
              FROM audit_events
             WHERE entity_type = 'charges' AND entity_id = ?
             ORDER BY occurred_at DESC
             LIMIT 100
            """, id);
        return Map.of("data", events);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  撤销 charge 调整 — 把已 ADJUSTED 的回到 ESTIMATED（用 audit_events 找回原始值）
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/charges/{id}/unadjust")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unadjust(@PathVariable String id) {
        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT amount, status::text AS status, currency,
                       customer_id::text AS customer_id, order_id::text AS order_id
                  FROM charges WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("charge 不存在");
        }
        if (!"ADJUSTED".equals(ch.get("status"))) {
            throw ApiException.badRequest("仅 ADJUSTED 状态可撤销，当前 " + ch.get("status"));
        }
        if (EXISTS_INVOICE_LINE(id)) {
            throw ApiException.badRequest("已出账的 charge 不能撤销调整");
        }

        // 从 audit_events 找最近一条 UPDATE before_state 里的 amount
        // 注：JDBC PreparedStatement 把 jsonb `?` 操作符误识为参数占位符 → 改用 jsonb_exists 函数
        BigDecimal originalAmount;
        try {
            originalAmount = jdbc.queryForObject("""
                SELECT (before_state->>'amount')::numeric
                  FROM audit_events
                 WHERE entity_type = 'charges' AND entity_id = ? AND action = 'UPDATE'
                   AND jsonb_exists(before_state, 'amount')
                 ORDER BY occurred_at DESC LIMIT 1
                """, BigDecimal.class, id);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("找不到原始金额（audit_events 无记录）");
        }
        BigDecimal currentAmount = (BigDecimal) ch.get("amount");
        jdbc.update("""
            UPDATE charges SET amount = ?, status = 'ESTIMATED'::charge_status, audit_status = 'PENDING'
             WHERE id = ?::uuid
            """, originalAmount, id);

        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'charges', ?, 'UPDATE', current_user,
                    jsonb_build_object('amount', ?, 'status', 'ADJUSTED'),
                    jsonb_build_object('amount', ?, 'status', 'ESTIMATED'),
                    '撤销调整')
            """, id, currentAmount, originalAmount);

        // balance_ledger 反向冲账：撤销之前的 ADJUST diff
        BigDecimal reverseDiff = currentAmount.subtract(originalAmount);
        if (reverseDiff.signum() != 0) {
            writeLedger(
                (String) ch.get("customer_id"), (String) ch.get("currency"),
                reverseDiff.abs(),
                reverseDiff.signum() > 0 ? "CREDIT" : "DEBIT",
                "ADJUST",
                "charges", id, (String) ch.get("order_id"),
                "system",
                "撤销调整 " + currentAmount + " → " + originalAmount
            );
        }

        return Map.of(
            "id", id,
            "newAmount", originalAmount,
            "previousAmount", currentAmount,
            "status", "ESTIMATED"
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  作废账单 — 把账单切到 VOID 并解关联 charges（回到待审核 ADJUSTED）
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/invoices/{id}/void")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> voidInvoice(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap(
                "SELECT status, invoice_no, paid_amount FROM customer_invoices WHERE id = ?::uuid", id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("账单不存在");
        }
        if ("PAID".equals(inv.get("status"))) {
            throw ApiException.badRequest("已付账单不能直接作废，请先反核销");
        }
        if ("VOID".equals(inv.get("status"))) {
            throw ApiException.badRequest("账单已是 VOID");
        }

        // 1) 解关联 charges 回到 ADJUSTED + PENDING
        int unlinked = jdbc.update("""
            UPDATE charges SET audit_status = 'PENDING'
             WHERE id IN (SELECT charge_id FROM customer_invoice_lines WHERE invoice_id = ?::uuid)
            """, id);
        jdbc.update("DELETE FROM customer_invoice_lines WHERE invoice_id = ?::uuid", id);

        // 2) 账单标 VOID
        jdbc.update("""
            UPDATE customer_invoices SET status = 'VOID', writeoff_status = 'VOID'
             WHERE id = ?::uuid
            """, id);

        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : "";
        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'customer_invoices', ?, 'UPDATE', current_user,
                    jsonb_build_object('status', ?),
                    jsonb_build_object('status', 'VOID'),
                    ?)
            """, id, inv.get("status"), "作废账单: " + reason);

        return Map.of(
            "id", id,
            "invoiceNo", inv.get("invoice_no"),
            "unlinkedCharges", unlinked,
            "status", "VOID"
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  反核销 — 把 PAID 账单退回 PENDING（财务标错的撤销入口）
    //  - customer_invoices PAID → PENDING, paid_amount=0, unpaid_amount=total
    //  - charges SETTLED → UNSETTLED
    //  - balance_ledger 反向 RECEIPT（DEBIT 把客户余额扣回去）
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/invoices/{id}/unsettle")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unsettleInvoice(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap("""
                SELECT id::text AS id, customer_id::text AS customer_id, currency,
                       total_amount, paid_amount, status
                  FROM customer_invoices WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("账单不存在");
        }
        if (!"PAID".equals(inv.get("status")) && !"PARTIAL_PAID".equals(inv.get("status"))) {
            throw ApiException.badRequest("仅 PAID/PARTIAL_PAID 状态可反核销，当前 " + inv.get("status"));
        }
        BigDecimal totalAmount = (BigDecimal) inv.get("total_amount");
        BigDecimal alreadyPaid = (BigDecimal) inv.get("paid_amount");

        // 1) 账单回退到 PENDING
        jdbc.update("""
            UPDATE customer_invoices
               SET paid_amount = 0, unpaid_amount = total_amount, status = 'PENDING',
                   confirmed_at = NULL, last_payment_at = NULL
             WHERE id = ?::uuid
            """, id);

        // 2) charges 回退到 UNSETTLED
        int unsettled = jdbc.update("""
            UPDATE charges SET settlement_status = 'UNSETTLED', paid_amount = 0
             WHERE id IN (SELECT charge_id FROM customer_invoice_lines WHERE invoice_id = ?::uuid)
            """, id);

        // 3) balance_ledger 反向 RECEIPT：之前 CREDIT 加回的余额，现在 DEBIT 扣回去
        writeLedger(
            (String) inv.get("customer_id"), (String) inv.get("currency"),
            alreadyPaid, "DEBIT", "VOID",
            "customer_invoices", id, (String) inv.get("id"),
            "system",
            "反核销账单 " + inv.get("id") + (body != null && body.get("reason") != null ? "（" + body.get("reason") + "）" : "")
        );

        // 4) audit_events 留痕
        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'customer_invoices', ?, 'UPDATE', current_user,
                    jsonb_build_object('status', ?, 'paid_amount', ?),
                    jsonb_build_object('status', 'PENDING', 'paid_amount', 0),
                    ?)
            """, id, inv.get("status"), alreadyPaid,
                 "反核销" + (body != null && body.get("reason") != null ? ": " + body.get("reason") : ""));

        return Map.of(
            "id", id,
            "status", "PENDING",
            "reversedAmount", alreadyPaid,
            "unsettledCharges", unsettled
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  阶段 ④ 核销/扣减确认 — 财务手动 mark 账单已付
    //  动作：customer_invoices PAID + charges SETTLED + balance_ledger RECEIPT
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/invoices/{id}/mark-paid")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> markInvoicePaid(@PathVariable String id, @RequestBody Map<String, Object> body) {
        BigDecimal paidAmount = null;
        if (body != null && body.get("amount") != null) {
            try { paidAmount = new BigDecimal(body.get("amount").toString()); }
            catch (Exception ex) { throw ApiException.badRequest("amount 格式错"); }
        }
        String remark = body == null || body.get("remark") == null ? null : body.get("remark").toString();

        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap("""
                SELECT id::text AS id, customer_id::text AS customer_id, currency,
                       total_amount, paid_amount, unpaid_amount, status
                  FROM customer_invoices WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("账单不存在");
        }
        if ("VOID".equals(inv.get("status"))) {
            throw ApiException.badRequest("已作废账单不能标记已付");
        }
        if ("PAID".equals(inv.get("status"))) {
            throw ApiException.badRequest("账单已是 PAID 状态");
        }

        BigDecimal totalAmount = (BigDecimal) inv.get("total_amount");
        BigDecimal alreadyPaid = (BigDecimal) inv.get("paid_amount");
        BigDecimal unpaidBefore = (BigDecimal) inv.get("unpaid_amount");
        BigDecimal payNow = paidAmount == null ? unpaidBefore : paidAmount;
        BigDecimal newPaid = alreadyPaid.add(payNow);
        BigDecimal newUnpaid = totalAmount.subtract(newPaid);
        if (newUnpaid.signum() < 0) {
            throw ApiException.badRequest("支付金额超过未付金额");
        }
        String newStatus = newUnpaid.signum() == 0 ? "PAID" : "PARTIAL_PAID";

        // 1) 更新账单状态
        jdbc.update("""
            UPDATE customer_invoices
               SET paid_amount = ?, unpaid_amount = ?, status = ?,
                   confirmed_at = CASE WHEN ?='PAID' THEN now() ELSE confirmed_at END,
                   last_payment_at = now()
             WHERE id = ?::uuid
            """, newPaid, newUnpaid, newStatus, newStatus, id);

        // 2) 全额付清 → 联动 charges 标 SETTLED
        if ("PAID".equals(newStatus)) {
            jdbc.update("""
                UPDATE charges SET settlement_status = 'SETTLED', paid_amount = amount
                 WHERE id IN (SELECT charge_id FROM customer_invoice_lines WHERE invoice_id = ?::uuid)
                """, id);
        }

        // 3) balance_ledger RECEIPT — 用 helper 自动建影子账户 + 写流水
        writeLedger(
            (String) inv.get("customer_id"), (String) inv.get("currency"),
            payNow, "CREDIT", "RECEIPT",
            "customer_invoices", id, (String) inv.get("id"),
            "system",
            remark == null ? "核销收款" : remark
        );

        return Map.of(
            "id", id,
            "status", newStatus,
            "paidAmount", newPaid,
            "unpaidAmount", newUnpaid
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  退款 — 已 PAID/PARTIAL_PAID 账单退回客户余额（balance_ledger REFUND）
    //  - customer_invoices.paid_amount 减；status 按新 paid 重算
    //  - charges 不动（保留历史），只生成 ledger 流水
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/invoices/{id}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundInvoice(@PathVariable String id, @RequestBody Map<String, Object> body) {
        BigDecimal refundAmount;
        try { refundAmount = new BigDecimal(body.get("amount").toString()); }
        catch (Exception ex) { throw ApiException.badRequest("amount 必填且为数字"); }
        if (refundAmount.signum() <= 0) throw ApiException.badRequest("退款金额必须 > 0");
        String reason = body.get("reason") == null ? null : body.get("reason").toString();

        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap("""
                SELECT id::text AS id, customer_id::text AS customer_id, currency,
                       total_amount, paid_amount, status
                  FROM customer_invoices WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) { throw ApiException.notFound("账单不存在"); }

        String s = (String) inv.get("status");
        if (!"PAID".equals(s) && !"PARTIAL_PAID".equals(s)) {
            throw ApiException.badRequest("仅 PAID/PARTIAL_PAID 状态可退款，当前 " + s);
        }
        BigDecimal alreadyPaid = (BigDecimal) inv.get("paid_amount");
        if (refundAmount.compareTo(alreadyPaid) > 0) {
            throw ApiException.badRequest("退款金额超过已付（已付 " + alreadyPaid + "）");
        }
        BigDecimal totalAmount = (BigDecimal) inv.get("total_amount");
        BigDecimal newPaid = alreadyPaid.subtract(refundAmount);
        BigDecimal newUnpaid = totalAmount.subtract(newPaid);
        String newStatus = newPaid.signum() == 0 ? "PENDING"
            : newUnpaid.signum() == 0 ? "PAID" : "PARTIAL_PAID";

        jdbc.update("""
            UPDATE customer_invoices SET paid_amount = ?, unpaid_amount = ?, status = ?
             WHERE id = ?::uuid
            """, newPaid, newUnpaid, newStatus, id);

        // 退款也要回滚 charges 的 SETTLED 状态（如果之前 PAID 了的话）
        if ("PAID".equals(s)) {
            jdbc.update("""
                UPDATE charges SET settlement_status = 'UNSETTLED', paid_amount = 0
                 WHERE id IN (SELECT charge_id FROM customer_invoice_lines WHERE invoice_id = ?::uuid)
                """, id);
        }

        // balance_ledger REFUND CREDIT — 客户余额加回退款金额
        writeLedger(
            (String) inv.get("customer_id"), (String) inv.get("currency"),
            refundAmount, "CREDIT", "REFUND",
            "customer_invoices", id, (String) inv.get("id"),
            "system",
            "退款 " + refundAmount + (reason == null ? "" : "（" + reason + "）")
        );

        jdbc.update("""
            INSERT INTO audit_events (tenant_id, entity_type, entity_id, action, actor_name, before_state, after_state, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, 'customer_invoices', ?, 'UPDATE', current_user,
                    jsonb_build_object('paid_amount', ?, 'status', ?),
                    jsonb_build_object('paid_amount', ?, 'status', ?),
                    ?)
            """, id, alreadyPaid, s, newPaid, newStatus,
                 "退款" + (reason == null ? "" : ": " + reason));

        return Map.of(
            "id", id, "status", newStatus,
            "refundedAmount", refundAmount,
            "paidAmount", newPaid, "unpaidAmount", newUnpaid
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    //  批量 mark-paid（全额）/ 批量作废 / 批量退款
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/invoices/batch-mark-paid")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchMarkPaid(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        int ok = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (String id : ids) {
            try {
                markInvoicePaid(id, java.util.Collections.singletonMap("remark", "批量核销"));
                ok++;
            } catch (Exception ex) {
                failed.add(Map.of("id", id, "reason", ex.getMessage()));
            }
        }
        return Map.of("success", ok, "failed", failed.size(), "failedDetails", failed);
    }

    @PostMapping("/invoices/batch-void")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchVoid(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.get("ids");
        String reason = body.get("reason") == null ? "批量作废" : body.get("reason").toString();
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        int ok = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (String id : ids) {
            try {
                voidInvoice(id, java.util.Collections.singletonMap("reason", reason));
                ok++;
            } catch (Exception ex) {
                failed.add(Map.of("id", id, "reason", ex.getMessage()));
            }
        }
        return Map.of("success", ok, "failed", failed.size(), "failedDetails", failed);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  二审待办 — total_amount 超 5w 且 verify_status=PENDING 的账单
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping("/needs-verify")
    public Map<String, Object> needsVerify(
        @RequestParam(required = false, defaultValue = "50000") BigDecimal threshold,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM customer_invoices ci
             WHERE ci.total_amount > ?
               AND coalesce(ci.verify_status, 'PENDING') = 'PENDING'
               AND ci.status NOT IN ('VOID')
            """, Long.class, threshold);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ci.id::text AS id, ci.invoice_no, ci.currency, ci.total_amount,
                   ci.paid_amount, ci.unpaid_amount, ci.status, ci.verify_status,
                   ci.issued_at AS created_at,
                   c.code AS customer_code, c.name AS customer_name
              FROM customer_invoices ci
              LEFT JOIN customers c ON c.id = ci.customer_id
             WHERE ci.total_amount > ?
               AND coalesce(ci.verify_status, 'PENDING') = 'PENDING'
               AND ci.status NOT IN ('VOID')
             ORDER BY ci.total_amount DESC
             LIMIT ? OFFSET ?
            """, threshold, limit, offset);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  孤立 charges 清理 — 订单 CANCELLED 但 charges 还挂在预扣
    //  动作：charges SET status=VOID + 反向 ledger
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/cleanup-orphan-charges")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cleanupOrphanCharges() {
        // 找所有"订单已取消但 charge 还在 ESTIMATED/ADJUSTED 未出账"的孤儿
        List<Map<String, Object>> orphans = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.currency, ch.customer_id::text AS customer_id,
                   ch.order_id::text AS order_id, ch.status::text AS status
              FROM charges ch
              JOIN orders o ON o.id = ch.order_id
             WHERE ch.side = 'AR'
               AND ch.status IN ('ESTIMATED'::charge_status, 'ADJUSTED'::charge_status)
               AND o.status = 'CANCELLED'
               AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = ch.id)
            """);
        int n = 0;
        for (Map<String, Object> ch : orphans) {
            jdbc.update("""
                UPDATE charges SET status='VOID'::charge_status, audit_status='UNAUDITED'
                 WHERE id = ?::uuid
                """, ch.get("id"));
            // 反向 ledger：之前 PREPAY/ADJUST DEBIT，现在 VOID CREDIT 退回
            writeLedger(
                (String) ch.get("customer_id"), (String) ch.get("currency"),
                (BigDecimal) ch.get("amount"), "CREDIT", "VOID",
                "charges", (String) ch.get("id"), (String) ch.get("order_id"),
                "system",
                "订单已取消，回退预扣（原状态 " + ch.get("status") + "）"
            );
            n++;
        }
        return Map.of("cleanedCount", n);
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

    // ════════════════════════════════════════════════════════════════════════
    //  财务 dashboard 汇总 — 总应收 / 本月已收 / 逾期 / 客户数 / 待二审
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 总应收（未付）— 所有 invoices.unpaid_amount 合计
        BigDecimal totalReceivable = jdbc.queryForObject("""
            SELECT coalesce(sum(unpaid_amount), 0) FROM customer_invoices
             WHERE status NOT IN ('VOID') AND unpaid_amount > 0
            """, BigDecimal.class);
        stats.put("totalReceivable", totalReceivable);

        // 本月已收 — balance_ledger RECEIPT CREDIT 本月合计
        BigDecimal paidThisMonth = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM balance_ledger
             WHERE biz_type='RECEIPT' AND direction='CREDIT'
               AND created_at >= date_trunc('month', now())
            """, BigDecimal.class);
        stats.put("paidThisMonth", paidThisMonth);

        // 预扣未对账 — charges ESTIMATED 合计
        BigDecimal prepayPending = jdbc.queryForObject("""
            SELECT coalesce(sum(amount), 0) FROM charges
             WHERE side='AR' AND status='ESTIMATED'::charge_status
               AND NOT EXISTS (SELECT 1 FROM customer_invoice_lines il WHERE il.charge_id = charges.id)
            """, BigDecimal.class);
        stats.put("prepayPending", prepayPending);

        // 逾期 — 出账 >30 天未付
        BigDecimal overdue = jdbc.queryForObject("""
            SELECT coalesce(sum(unpaid_amount), 0) FROM customer_invoices
             WHERE status NOT IN ('VOID','PAID')
               AND unpaid_amount > 0
               AND issued_at < now() - interval '30 days'
            """, BigDecimal.class);
        stats.put("overdueAmount", overdue);

        // 待二审账单数（> 5w 且 verify_status='PENDING'）
        Long pendingVerify = jdbc.queryForObject("""
            SELECT count(*) FROM customer_invoices
             WHERE total_amount > 50000 AND coalesce(verify_status,'PENDING')='PENDING'
               AND status NOT IN ('VOID')
            """, Long.class);
        stats.put("pendingVerifyCount", pendingVerify == null ? 0 : pendingVerify);

        // 活跃客户数（有未对账 charges 或未付账单）
        Long activeCustomers = jdbc.queryForObject("""
            SELECT count(DISTINCT customer_id) FROM (
              SELECT customer_id FROM charges WHERE side='AR' AND customer_id IS NOT NULL
                AND status='ESTIMATED'::charge_status
              UNION
              SELECT customer_id FROM customer_invoices WHERE unpaid_amount > 0
                AND status NOT IN ('VOID')
            ) t
            """, Long.class);
        stats.put("activeCustomers", activeCustomers == null ? 0 : activeCustomers);

        return stats;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Invoice 可打印 HTML（浏览器 Ctrl+P 另存 PDF）
    // ════════════════════════════════════════════════════════════════════════
    @GetMapping(value = "/invoices/{id}/print", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> printInvoice(@PathVariable String id) {
        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap("""
                SELECT ci.invoice_no, ci.invoice_date, ci.currency, ci.total_amount,
                       ci.paid_amount, ci.unpaid_amount, ci.previous_balance,
                       ci.line_count, ci.status, ci.confirmed_at,
                       c.code AS customer_code, c.name AS customer_name, c.contacts, c.phone
                  FROM customer_invoices ci
                  LEFT JOIN customers c ON c.id = ci.customer_id
                 WHERE ci.id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("账单不存在");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT il.amount, o.order_no, ct.tracking_no, ch.created_at
              FROM customer_invoice_lines il
              JOIN charges ch ON ch.id = il.charge_id
              LEFT JOIN orders o ON o.id = ch.order_id
              LEFT JOIN cartons ct ON ct.shipment_id = ch.shipment_id
             WHERE il.invoice_id = ?::uuid
             ORDER BY ch.created_at
            """, id);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<title>账单 ").append(inv.get("invoice_no")).append("</title>");
        html.append("<style>");
        html.append("body{font-family:'Helvetica Neue',Arial,'PingFang SC','Microsoft YaHei',sans-serif;max-width:800px;margin:30px auto;padding:20px;color:#1e293b;}");
        html.append("h1{font-size:24px;margin:0 0 4px;color:#0f172a;}.muted{color:#64748b;font-size:13px;}");
        html.append(".grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:20px 0;}");
        html.append(".card{background:#f8fafc;border-left:3px solid #6366f1;padding:8px 12px;border-radius:4px;}");
        html.append(".card .lbl{font-size:11px;color:#64748b;}.card .val{font-size:16px;font-weight:600;}");
        html.append("table{width:100%;border-collapse:collapse;margin-top:18px;}");
        html.append("th,td{border-bottom:1px solid #e2e8f0;padding:8px 6px;text-align:left;font-size:13px;}");
        html.append("th{background:#f1f5f9;font-weight:600;}");
        html.append(".total-row{font-weight:600;background:#fef3c7;}");
        html.append(".no-print{margin:20px 0;}@media print{.no-print{display:none;}body{margin:0;}}");
        html.append("</style></head><body>");
        html.append("<button class=\"no-print\" onclick=\"window.print()\" ")
            .append("style=\"padding:8px 16px;background:#6366f1;color:white;border:none;border-radius:4px;cursor:pointer;\">")
            .append("打印 / 另存 PDF</button>");
        html.append("<h1>对账单 / Invoice</h1>");
        html.append("<div class=\"muted\">单号：").append(inv.get("invoice_no"))
            .append("　·　出账日期：").append(inv.get("invoice_date"))
            .append("　·　状态：").append(inv.get("status")).append("</div>");
        html.append("<div class=\"grid\">");
        html.append("<div class=\"card\"><div class=\"lbl\">客户</div><div class=\"val\">")
            .append(inv.get("customer_name") == null ? "-" : inv.get("customer_name"))
            .append("</div><div class=\"muted\">").append(inv.get("customer_code") == null ? "" : inv.get("customer_code"))
            .append(" / 联系人：").append(inv.get("contacts") == null ? "-" : inv.get("contacts")).append("</div></div>");
        html.append("<div class=\"card\" style=\"border-left-color:#10b981;\"><div class=\"lbl\">本期金额</div><div class=\"val\">")
            .append(inv.get("total_amount")).append(" ").append(inv.get("currency")).append("</div>");
        html.append("<div class=\"muted\">明细 ").append(inv.get("line_count")).append(" 条　上期余额 ")
            .append(inv.get("previous_balance")).append("</div></div>");
        html.append("<div class=\"card\" style=\"border-left-color:#f59e0b;\"><div class=\"lbl\">已付</div><div class=\"val\">")
            .append(inv.get("paid_amount")).append(" ").append(inv.get("currency")).append("</div></div>");
        html.append("<div class=\"card\" style=\"border-left-color:#ef4444;\"><div class=\"lbl\">未付</div><div class=\"val\">")
            .append(inv.get("unpaid_amount")).append(" ").append(inv.get("currency")).append("</div></div>");
        html.append("</div>");
        html.append("<table><thead><tr><th>序号</th><th>订单号</th><th>运单号</th><th>记录时间</th><th style=\"text-align:right\">金额</th></tr></thead><tbody>");
        int i = 1;
        for (Map<String, Object> ln : lines) {
            html.append("<tr><td>").append(i++).append("</td>");
            html.append("<td>").append(ln.get("order_no") == null ? "-" : ln.get("order_no")).append("</td>");
            html.append("<td>").append(ln.get("tracking_no") == null ? "-" : ln.get("tracking_no")).append("</td>");
            html.append("<td>").append(ln.get("created_at") == null ? "" : ln.get("created_at").toString().substring(0, 19).replace('T',' ')).append("</td>");
            html.append("<td style=\"text-align:right\">").append(ln.get("amount")).append("</td></tr>");
        }
        html.append("<tr class=\"total-row\"><td colspan=\"4\" style=\"text-align:right\">合计</td>")
            .append("<td style=\"text-align:right\">").append(inv.get("total_amount")).append(" ")
            .append(inv.get("currency")).append("</td></tr>");
        html.append("</tbody></table>");
        if (inv.get("confirmed_at") != null) {
            html.append("<p class=\"muted\" style=\"margin-top:30px\">收款确认时间：")
                .append(inv.get("confirmed_at")).append("</p>");
        }
        html.append("</body></html>");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .body(html.toString());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  客户端账单列表（HMAC 用，调用方传 customerId 限自己）
    // ════════════════════════════════════════════════════════════════════════
    public Map<String, Object> customerInvoiceList(String customerId, String currency) {
        String curFilter = currency == null || currency.isBlank() ? null : currency;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, invoice_no, invoice_date, currency,
                   total_amount, paid_amount, unpaid_amount, status, issued_at, last_payment_at
              FROM customer_invoices
             WHERE customer_id = ?::uuid
               AND status NOT IN ('VOID')
               AND (?::text IS NULL OR currency = ?)
             ORDER BY issued_at DESC
             LIMIT 200
            """, customerId, curFilter, curFilter);
        return Map.of("data", rows, "total", rows.size());
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
