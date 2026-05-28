package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateFromOrder;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GeneratePartnerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.SettleCustomerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.SettlePartnerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeResponses.GenerateResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.InvoiceResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.SettleResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.VoidResult;
import com.xqt.saas.framework.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对照 ACC: Charge.php / CBill.php / Received.php / Pay.php。
 *
 * 闭环：
 *   下单/Submit → charges(ESTIMATED, side=AR/AP)
 *   → generateFromOrder() → charges(CONFIRMED) 走审核流（AuditService.audit）
 *   → generateCustomerInvoice() → 聚合 AUDITED 行成 customer_invoices + lines
 *   → settleCustomerInvoice() → 写 payments + 联动 paid_amount/writeoff_status
 *   → voidCharge() → 置 VOID 退余额
 */
@Service
public class DocumentChargeService {
    private final DocumentChargeRepository repository;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public DocumentChargeService(DocumentChargeRepository repository,
                                 AuditService auditService, JdbcTemplate jdbc) {
        this.repository = repository;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    /** 提交单据：把 ESTIMATED → CONFIRMED。审核动作由前端再调 audit-biz。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public GenerateResult generateFromOrder(AuthPrincipal principal, GenerateFromOrder body) {
        if (body == null || body.orderId() == null) {
            throw ApiException.badRequest("orderId is required");
        }
        setTenant(principal.tenantId());
        int arCount = repository.confirmEstimatedCharges(principal.tenantId(), body.orderId(), "AR");
        int apCount = body.includeAp() == null || body.includeAp()
            ? repository.confirmEstimatedCharges(principal.tenantId(), body.orderId(), "AP")
            : 0;
        // 聚合金额作为返回
        Map<String, Object> totals = sumChargesByOrder(principal.tenantId(), body.orderId());
        BigDecimal arTotal = totals == null ? BigDecimal.ZERO
            : (BigDecimal) totals.getOrDefault("ar_total", BigDecimal.ZERO);
        BigDecimal apTotal = totals == null ? BigDecimal.ZERO
            : (BigDecimal) totals.getOrDefault("ap_total", BigDecimal.ZERO);
        BigDecimal profit = arTotal.subtract(apTotal);
        return new GenerateResult(body.orderId(), arCount, apCount, arTotal, apTotal, profit, List.of());
    }

    /** 客户账单生成：按日期段聚合，写一张账单 + 多行 lines。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public InvoiceResult generateCustomerInvoice(AuthPrincipal principal,
                                                  GenerateCustomerInvoice body) {
        if (body == null || body.customerId() == null
            || body.dateFrom() == null || body.dateTo() == null) {
            throw ApiException.badRequest("customerId / dateFrom / dateTo required");
        }
        setTenant(principal.tenantId());
        String currency = body.currency() == null ? "CNY" : body.currency();
        List<Map<String, Object>> rows;
        if (body.chargeIds() != null && !body.chargeIds().isEmpty()) {
            rows = repository.findChargesByIds(principal.tenantId(), body.chargeIds());
        } else {
            rows = repository.findBillableArCharges(principal.tenantId(), body.customerId(),
                body.dateFrom(), body.dateTo(), currency);
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("no billable charges in selected range");
        }
        BigDecimal total = rows.stream()
            .map(r -> (BigDecimal) r.get("unpaid_amount"))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        String invoiceNo = repository.nextInvoiceNo(principal.tenantId(), "INV");
        String invoiceId = repository.insertCustomerInvoice(
            principal.tenantId(), body.customerId(), invoiceNo, currency, total, body.templateCode());

        List<String> chargeIds = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String chargeId = (String) r.get("id");
            BigDecimal lineAmount = (BigDecimal) r.get("unpaid_amount");
            String shipmentId = (String) r.get("shipment_id");
            repository.insertCustomerInvoiceLine(principal.tenantId(), invoiceId,
                chargeId, shipmentId, lineAmount);
            chargeIds.add(chargeId);
        }
        repository.markChargesBilled(principal.tenantId(), chargeIds, invoiceNo);
        return new InvoiceResult(invoiceId, invoiceNo, currency, total, chargeIds.size(), chargeIds);
    }

    /** 收款核销：amount > 0 为收款 / < 0 为反核销。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public SettleResult settleCustomerInvoice(AuthPrincipal principal, SettleCustomerInvoice body) {
        if (body == null || body.invoiceId() == null || body.amount() == null) {
            throw ApiException.badRequest("invoiceId / amount required");
        }
        setTenant(principal.tenantId());
        Map<String, Object> invoice = repository.findInvoice(principal.tenantId(), body.invoiceId());
        if (invoice == null) {
            throw ApiException.notFound("invoice not found: " + body.invoiceId());
        }
        String customerId = (String) invoice.get("customer_id");
        String currency = body.currency() == null ? (String) invoice.get("currency") : body.currency();
        // 写 payments
        String paymentId = repository.insertPayment(
            principal.tenantId(), customerId, body.amount(), currency, body.referenceNo());
        // 联动 invoice + lines + charges
        Map<String, Object> updated = repository.applyPaymentToInvoice(
            principal.tenantId(), body.invoiceId(), body.amount());
        return new SettleResult(
            body.invoiceId(),
            (String) updated.get("invoice_no"),
            (BigDecimal) updated.get("paid_amount"),
            (BigDecimal) updated.get("unpaid_amount"),
            (String) updated.get("writeoff_status"),
            paymentId
        );
    }

    /** Void：单独一笔费用作废。撤销余额预扣（若有）。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public VoidResult voidCharge(AuthPrincipal principal, String chargeId) {
        if (chargeId == null) throw ApiException.badRequest("chargeId required");
        setTenant(principal.tenantId());
        BigDecimal refunded = repository.voidChargeAndRefund(principal.tenantId(), chargeId);
        return new VoidResult(chargeId, refunded);
    }

    /** 利润聚合：byBranch/byCustomer/byChannel/byMonth。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> profitSummary(AuthPrincipal principal, String groupBy,
                                                    LocalDate dateFrom, LocalDate dateTo) {
        setTenant(principal.tenantId());
        return repository.aggregateProfit(principal.tenantId(), groupBy, dateFrom, dateTo);
    }

    // ───────────────────── 供应商（AP）侧闭环 ─────────────────────

    /** 供应商账单生成：按日期段聚合该供应商已审 AP 费用为一张 partner_invoice。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public InvoiceResult generatePartnerInvoice(AuthPrincipal principal, GeneratePartnerInvoice body) {
        if (body == null || body.partnerId() == null
            || body.dateFrom() == null || body.dateTo() == null) {
            throw ApiException.badRequest("partnerId / dateFrom / dateTo required");
        }
        setTenant(principal.tenantId());
        String currency = body.currency() == null ? "CNY" : body.currency();
        List<Map<String, Object>> rows;
        if (body.chargeIds() != null && !body.chargeIds().isEmpty()) {
            rows = repository.findChargesByIds(principal.tenantId(), body.chargeIds());
        } else {
            rows = repository.findBillableApCharges(principal.tenantId(), body.partnerId(),
                body.dateFrom(), body.dateTo(), currency);
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("no billable AP charges in selected range");
        }
        BigDecimal total = rows.stream()
            .map(r -> (BigDecimal) r.get("unpaid_amount"))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        String invoiceNo = repository.nextInvoiceNo(principal.tenantId(), "PINV");
        String invoiceId = repository.insertPartnerInvoice(
            principal.tenantId(), body.partnerId(), invoiceNo, currency, total);

        List<String> chargeIds = new java.util.ArrayList<>();
        int lineNo = 1;
        for (Map<String, Object> r : rows) {
            String chargeId = (String) r.get("id");
            BigDecimal lineAmount = (BigDecimal) r.get("unpaid_amount");
            repository.insertPartnerInvoiceLine(principal.tenantId(), invoiceId, chargeId,
                (String) r.get("shipment_id"), (String) r.get("charge_item_id"),
                currency, lineAmount, lineNo++);
            chargeIds.add(chargeId);
        }
        return new InvoiceResult(invoiceId, invoiceNo, currency, total, chargeIds.size(), chargeIds);
    }

    /** 供应商账单付款核销：amount > 0 付款 / < 0 反核销。回写 AP charges。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public SettleResult settlePartnerInvoice(AuthPrincipal principal, SettlePartnerInvoice body) {
        if (body == null || body.invoiceId() == null || body.amount() == null) {
            throw ApiException.badRequest("invoiceId / amount required");
        }
        setTenant(principal.tenantId());
        Map<String, Object> invoice = repository.findPartnerInvoice(principal.tenantId(), body.invoiceId());
        if (invoice == null) {
            throw ApiException.notFound("partner invoice not found: " + body.invoiceId());
        }
        String partnerId = (String) invoice.get("partner_id");
        String currency = body.currency() == null ? (String) invoice.get("currency") : body.currency();
        String paymentId = repository.insertPartnerPayment(
            principal.tenantId(), partnerId, body.amount(), currency,
            body.bankAccountId(), body.referenceNo());
        // 金额动作记汇率快照（本币付款 rate=1，跨币种由调用方提供）
        repository.insertFxSnapshot(principal.tenantId(), currency, "CNY", BigDecimal.ONE, "PARTNER_SETTLE");
        Map<String, Object> updated = repository.applyPaymentToPartnerInvoice(
            principal.tenantId(), body.invoiceId(), body.amount());
        return new SettleResult(
            body.invoiceId(),
            (String) updated.get("invoice_no"),
            (BigDecimal) updated.get("paid_amount"),
            (BigDecimal) updated.get("unpaid_amount"),
            (String) updated.get("writeoff_status"),
            paymentId
        );
    }

    private Map<String, Object> sumChargesByOrder(String tenantId, String orderId) {
        try {
            return jdbc.queryForMap("""
                SELECT
                    coalesce(sum(case when ch.side = 'AR' then ch.amount end), 0) AS ar_total,
                    coalesce(sum(case when ch.side = 'AP' then ch.amount end), 0) AS ap_total
                FROM charges ch
                LEFT JOIN shipments sh ON sh.id = ch.shipment_id
                LEFT JOIN orders o ON o.customer_ref = sh.customer_ref AND o.tenant_id = sh.tenant_id
                WHERE ch.tenant_id = ?::uuid
                  AND o.id = ?::uuid
                """, tenantId, orderId);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private void setTenant(String tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",
            String.class, tenantId);
    }
}
