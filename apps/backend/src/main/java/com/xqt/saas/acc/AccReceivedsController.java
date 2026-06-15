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
import com.xqt.saas.framework.money.MoneySnapshotService;
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
 * /api/acc/receiveds — 前端列：no / customerName / bankName / amount / theDate / auditName / remark
 * AR 收款，来源 payments + customers。
 */
@RestController
@RequestMapping("/api/acc/receiveds")
public class AccReceivedsController {
    private static final String TABLE = "payments";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;
    private final com.xqt.saas.documentcharges.DocumentChargeService docService;

        private final BranchAccessFilter branchAccess;

public AccReceivedsController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  MoneySnapshotService moneySnapshotService,
                                  com.xqt.saas.documentcharges.DocumentChargeService docService,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
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
        @RequestParam(required = false) String status,
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
            String auditStatus = (status == null || status.isBlank()) ? null : status;
            AccFinanceFilters.Built adv = AccFinanceFilters.build(
                "p.customer_id", "p.currency", "p.audit_status", "p.audit_status",
                "p.received_at", "p.amount",
                customerIds, currencies, statuses, auditStatuses,
                createdFrom, createdTo, amountFrom, amountTo);
            var access = branchAccess.forCurrentViaCustomer("p");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, dateFrom, dateFrom, dateTo, dateTo, auditStatus, auditStatus));
            countParams.addAll(adv.params);
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM payments p"
                + " WHERE (?::text IS NULL OR p.reference_no ILIKE ?)"
                + "   AND (?::date IS NULL OR p.received_at >= ?::date)"
                + "   AND (?::date IS NULL OR p.received_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR p.audit_status = ?)"
                + adv.sql
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT"
                + "  p.id::text       AS id,"
                + "  p.reference_no,"
                + "  p.currency,"
                + "  p.amount,"
                + "  p.poundage,"
                + "  p.fx_rate,"
                + "  p.received_at,"
                + "  p.remark,"
                + "  p.audit_status,"
                + "  p.audited_at,"
                + "  p.audit_name,"
                + "  c.name           AS customer_name,"
                + "  coalesce(fa.bank_name, fa.account_name) AS bank_name"
                + " FROM payments p"
                + " LEFT JOIN customers c ON c.id = p.customer_id"
                + " LEFT JOIN financial_accounts fa ON fa.id = p.financial_account_id"
                + " WHERE (?::text IS NULL OR p.reference_no ILIKE ?)"
                + "   AND (?::date IS NULL OR p.received_at >= ?::date)"
                + "   AND (?::date IS NULL OR p.received_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR p.audit_status = ?)"
                + adv.sql
                + access.sql()
                + " ORDER BY p.received_at DESC"
                + " LIMIT ? OFFSET ?",
                buildReceivedsListParamsWithAdv(search, dateFrom, dateTo, auditStatus, adv.params, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM payments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object customerId = body.get("customer_id");
        // ACC Received.php L1339/L405: 找不到选定的客户
        if (customerId == null || customerId.toString().isBlank()) {
            throw ApiException.badRequest("找不到选定的客户");
        }
        // ACC Received.php L377: 金额必须大于零
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("金额必须大于零");
        }
        // ACC Received.php L379: 手续费不能小于零，也不能大于付款金额
        BigDecimal poundage = body.get("poundage") instanceof Number pn
            ? new BigDecimal(pn.toString()) : BigDecimal.ZERO;
        if (poundage.signum() < 0) {
            throw ApiException.badRequest("手续费不能小于零");
        }
        if (poundage.compareTo(amount) > 0) {
            throw ApiException.badRequest("手续费不能大于付款金额");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        // ACC Received.php L1259: 找不到选择的货币
        if (currency == null || currency.length() != 3) {
            throw ApiException.badRequest("找不到选择的货币");
        }
        String referenceNo = (String) body.getOrDefault("reference_no", body.get("no"));
        Object bankId = body.getOrDefault("financial_account_id", body.get("bankId"));
        // ACC Received.php L372: 请选择收款账户
        if (bankId == null || bankId.toString().isBlank()) {
            throw ApiException.badRequest("请选择收款账户");
        }
        String remark = (String) body.get("remark");
        String id = jdbc.queryForObject("""
            INSERT INTO payments (
              tenant_id, customer_id, currency, amount, received_at, reference_no,
              financial_account_id, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, now(), ?,
              ?::uuid, ?
            )
            RETURNING id::text
            """, String.class,
            customerId == null ? null : customerId.toString(),
            currency, amount, referenceNo,
            bankId == null ? null : bankId.toString(), remark);
        // AR 收款的汇率快照
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("收款已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE payments SET
              reference_no = coalesce(?, reference_no),
              amount       = coalesce(?, amount)
            WHERE id = ?::uuid
            """, (String) allowed.get("reference_no"),
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        // 改了金额刷新汇率快照
        if (allowed.containsKey("amount") && allowed.get("amount") instanceof Number n) {
            String currency = jdbc.queryForObject(
                "SELECT currency FROM payments WHERE id = ?::uuid", String.class, id);
            moneySnapshotService.snapshot(TABLE, id, new BigDecimal(n.toString()), currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM payments WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("收款已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM payments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * 对应前端 "快速收款"。统一走 documentcharges：raw INSERT payments 后再调
     * docService.recordStandaloneReceipt 写资金流水（balance_ledger RECEIPT），
     * 让快速收款也进资金账本，不再绕过流水（消除"双轨"）。
     *
     * 前端 body: { customerId, amount, bankId }；返回 { ok, id, ledgerWritten }。
     */
    @PostMapping("/quick")
    public Map<String, Object> quick(@RequestBody Map<String, Object> body) {
        // 1) 先按原有 CRUD 落 payments 行（保持兼容性 + 拿到 paymentId）
        Map<String, Object> created = create(body);
        String paymentId = (String) created.get("id");
        String bankId = body.get("financial_account_id") != null
            ? body.get("financial_account_id").toString()
            : body.get("bankId") == null ? null : body.get("bankId").toString();
        String customerId = body.get("customer_id") != null
            ? body.get("customer_id").toString()
            : body.get("customerId") == null ? null : body.get("customerId").toString();
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String remark = (String) body.getOrDefault("remark", "快速收款");

        boolean ledgerWritten = false;
        if (bankId != null && !bankId.isBlank() && customerId != null && amount.signum() > 0) {
            try {
                docService.recordStandaloneReceipt(currentPrincipal(),
                    customerId, bankId, paymentId, currency, amount, remark);
                ledgerWritten = true;
            } catch (com.xqt.saas.common.ApiException ignored) {
                // 流水写入失败不阻断主流程（payments 行已落），账户不存在等情况静默跳过
            }
        }
        return Map.of("ok", true, "id", paymentId, "ledgerWritten", ledgerWritten);
    }

    private com.xqt.saas.auth.AuthPrincipal currentPrincipal() {
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.xqt.saas.auth.AuthPrincipal p)) {
            throw com.xqt.saas.common.ApiException.unauthorized("authentication required");
        }
        return p;
    }

    private static Object[] buildReceivedsListParams(String search, String dateFrom, String dateTo,
                                                       String auditStatus,
                                                       BranchAccessFilter.AccessClause access,
                                                       int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, dateFrom, dateFrom, dateTo, dateTo, auditStatus, auditStatus));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private static Object[] buildReceivedsListParamsWithAdv(String search, String dateFrom, String dateTo,
                                                              String auditStatus,
                                                              java.util.List<Object> advParams,
                                                              BranchAccessFilter.AccessClause access,
                                                              int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, dateFrom, dateFrom, dateTo, dateTo, auditStatus, auditStatus));
        params.addAll(advParams);
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("reference_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("bankName", row.get("bank_name") == null ? "" : row.get("bank_name"));
        out.put("amount", row.get("amount"));
        out.put("poundage", row.get("poundage"));
        out.put("fxRate", row.get("fx_rate"));
        out.put("theDate", json.value(row.get("received_at")));
        out.put("remark", row.get("remark") == null ? "" : row.get("remark"));
        out.put("currency", row.get("currency"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
