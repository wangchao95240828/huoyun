package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.audit.AuditAction;
import com.xqt.saas.framework.audit.AuditService;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
    private final AuditService auditService;

    public AccBillsController(JdbcTemplate jdbc, JsonSupport json,
                              CascadeChecker cascadeChecker, FieldGate fieldGate,
                              AuditService auditService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM customer_invoices i
                WHERE (?::text IS NULL OR i.invoice_no ILIKE ?)
                  AND (?::date IS NULL OR i.issued_at >= ?::date)
                  AND (?::date IS NULL OR i.issued_at < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);

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
                  (
                    SELECT coalesce(sum(p.amount), 0) FROM payments p
                    WHERE p.tenant_id = i.tenant_id AND p.reference_no = i.invoice_no
                  ) AS paid_amount,
                  (
                    SELECT count(*) FROM customer_invoice_lines l WHERE l.invoice_id = i.id
                  ) AS line_count
                FROM customer_invoices i
                LEFT JOIN customers c ON c.id = i.customer_id
                WHERE (?::text IS NULL OR i.invoice_no ILIKE ?)
                  AND (?::date IS NULL OR i.issued_at >= ?::date)
                  AND (?::date IS NULL OR i.issued_at < (?::date + 1))
                ORDER BY i.issued_at DESC NULLS LAST, i.invoice_no
                LIMIT ? OFFSET ?
                """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
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

    /** 对应前端 "查看账单明细" — bills/{id}/items 拉 customer_invoice_lines。 */
    @GetMapping("/{id}/items")
    public Map<String, Object> items(@PathVariable String id) {
        try {
            List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT id::text AS id, line_no, description, quantity, unit_price, line_total
                FROM customer_invoice_lines WHERE invoice_id = ?::uuid
                ORDER BY line_no
                """, id);
            return Map.of("data", json.rows(lines));
        } catch (DataAccessException ex) {
            return Map.of("data", List.of());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String invoiceNo = (String) body.get("invoice_no");
        Object customerId = body.get("customer_id");
        String currency = (String) body.getOrDefault("currency", "CNY");
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
    public Map<String, Object> delete(@PathVariable String id) {
        // 级联校验：已关联收款的账单不允许删
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM customer_invoices WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * A4 — 生成客户账单（按客户+期间+币种汇总）
     *
     * 业务规则：
     *   1. 只汇总 side='AR' AND audit_status='AUDITED' AND invoice_id IS NULL AND settlement_status='UNSETTLED'
     *   2. 若期间内无可出账 charges 返回 400
     *   3. invoice_no 格式 CINV-YYYYMM-NNNN
     *   4. 客户上期未付自动带入 previous_balance
     *   5. 先 INSERT invoice → UPDATE charges.invoice_id → INSERT invoice_lines
     *   6. 整个流程单事务，任一步失败全部回滚
     */
    @PostMapping("/generate")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customer_id");
        String currency   = (String) body.get("currency");
        String dateFrom   = (String) body.get("date_from");
        String dateTo     = (String) body.get("date_to");

        // 参数校验
        if (customerId == null || currency == null || dateFrom == null || dateTo == null) {
            throw ApiException.badRequest("customer_id / currency / date_from / date_to 必填");
        }

        // ① 查找待出账的 charges（未关联账单的已审 AR 费用）
        List<Map<String, Object>> charges = jdbc.queryForList("""
            SELECT id::text AS id, amount
            FROM charges
            WHERE customer_id = ?::uuid AND currency = ?
              AND side = 'AR' AND audit_status = 'AUDITED'
              AND invoice_id IS NULL AND settlement_status = 'UNSETTLED'
              AND created_at >= ?::timestamptz AND created_at < (?::date + 1)::timestamptz
            ORDER BY created_at
            """, customerId, currency, dateFrom, dateTo);

        if (charges.isEmpty()) {
            throw ApiException.badRequest(
                String.format("期间 %s ~ %s 内无可出账 charges (customer=%s, currency=%s)",
                    dateFrom, dateTo, customerId, currency));
        }

        // ② 计算总额
        BigDecimal total = charges.stream()
            .map(c -> (BigDecimal) c.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ③ 上期余额（F10）：该客户同币种最近一张账单的 unpaid_amount
        BigDecimal previousBalance = jdbc.queryForObject("""
            SELECT coalesce(unpaid_amount, 0) FROM customer_invoices
            WHERE customer_id = ?::uuid AND currency = ?
            ORDER BY created_at DESC LIMIT 1
            """, BigDecimal.class, customerId, currency);
        if (previousBalance == null) previousBalance = BigDecimal.ZERO;

        // ④ 生成编号 CINV-YYYYMM-NNNN
        String invoiceNo = "CINV-" + java.time.LocalDate.now().toString()
            .substring(0, 7).replace("-", "") + "-" + String.format("%04d", nextSeq());

        // ⑤ 建账单
        String invoiceId = jdbc.queryForObject("""
            INSERT INTO customer_invoices (
              tenant_id, customer_id, invoice_no, currency,
              total_amount, paid_amount, unpaid_amount,
              line_count, previous_balance, status, invoice_date, issued_at
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, ?, ?, ?,
              0, ?,
              ?, ?, 'DRAFT', now(), now()
            )
            RETURNING id::text
            """, String.class,
            customerId, invoiceNo, currency,
            total, total,
            charges.size(), previousBalance);

        // ⑥ 关联 charges.invoice_id 打标
        List<String> ids = charges.stream().map(c -> (String) c.get("id")).toList();
        jdbc.update(
            "UPDATE charges SET invoice_id = ?::uuid WHERE id = ANY(?::uuid[])",
            invoiceId, ids.toArray(new String[0]));

        // ⑦ 逐条建 customer_invoice_lines
        for (Map<String, Object> ch : charges) {
            jdbc.update("""
                INSERT INTO customer_invoice_lines (tenant_id, invoice_id, charge_id, amount)
                VALUES (
                  current_setting('app.current_tenant_id')::uuid,
                  ?::uuid, ?::uuid, ?
                )
                """, invoiceId, ch.get("id"), ch.get("amount"));
        }

        // ⑧ 写审计事件
        String actor = getCurrentUserName();
        auditService.recordEvent(TABLE, invoiceId, AuditAction.CREATE,
            actor, Map.of(), Map.of(
                "invoice_no", invoiceNo,
                "customer_id", customerId,
                "currency", currency,
                "total_amount", total,
                "line_count", charges.size(),
                "previous_balance", previousBalance
            ));

        return Map.of(
            "invoice_id", invoiceId,
            "invoice_no", invoiceNo,
            "total_amount", total,
            "line_count", charges.size(),
            "previous_balance", previousBalance
        );
    }

    /** 序列号：从 cinv_invoice_seq 取下一个值 */
    private int nextSeq() {
        return jdbc.queryForObject("SELECT nextval('cinv_invoice_seq')", Integer.class);
    }

    /** 获取当前登录用户名（用于审计事件 actor_name） */
    private String getCurrentUserName() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.xqt.saas.auth.AuthPrincipal p) {
                return p.username();
            }
        } catch (Exception ignored) {
            // 审计 actor 获取失败不阻塞业务
        }
        return "system";
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
        out.put("salesman", "");           // 新模型未建模
        out.put("currency", row.get("currency"));
        // 审核流字段：前端用来显示"已审核"红色标记 + 决定能否点删除/反审按钮
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
