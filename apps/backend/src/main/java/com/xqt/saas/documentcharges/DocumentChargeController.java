package com.xqt.saas.documentcharges;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateCustomerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GenerateFromOrder;
import com.xqt.saas.documentcharges.DocumentChargeRequests.GeneratePartnerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.SettleCustomerInvoice;
import com.xqt.saas.documentcharges.DocumentChargeRequests.SettlePartnerInvoice;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/document/charges /api/document/invoices —— ACC 闭环对外端点。
 */
@RestController
@RequestMapping("/api/document")
public class DocumentChargeController {
    private final DocumentChargeService service;

    public DocumentChargeController(DocumentChargeService service) {
        this.service = service;
    }

    @PostMapping("/charges/generate-from-order")
    public Object generate(@RequestBody GenerateFromOrder body) {
        return service.generateFromOrder(principal(), body);
    }

    @PostMapping("/charges/{id}/void")
    public Object voidCharge(@PathVariable String id) {
        return service.voidCharge(principal(), id);
    }

    @PostMapping("/invoices/generate")
    public Object generateInvoice(@RequestBody GenerateCustomerInvoice body) {
        return service.generateCustomerInvoice(principal(), body);
    }

    @PostMapping("/invoices/{id}/settle")
    public Object settleInvoice(@PathVariable String id, @RequestBody SettleCustomerInvoice body) {
        if (body.invoiceId() == null) {
            body = new SettleCustomerInvoice(id, body.amount(), body.currency(),
                body.paymentMethod(), body.bankAccountId(), body.referenceNo(), body.remark());
        }
        return service.settleCustomerInvoice(principal(), body);
    }

    @PostMapping("/partner-invoices/generate")
    public Object generatePartnerInvoice(@RequestBody GeneratePartnerInvoice body) {
        return service.generatePartnerInvoice(principal(), body);
    }

    @PostMapping("/partner-invoices/{id}/settle")
    public Object settlePartnerInvoice(@PathVariable String id, @RequestBody SettlePartnerInvoice body) {
        if (body.invoiceId() == null) {
            body = new SettlePartnerInvoice(id, body.amount(), body.currency(),
                body.bankAccountId(), body.referenceNo(), body.remark());
        }
        return service.settlePartnerInvoice(principal(), body);
    }

    @GetMapping("/profits/summary")
    public Map<String, Object> profitSummary(
        @RequestParam(required = false, defaultValue = "month") String groupBy,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        LocalDate from = dateFrom == null ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(dateFrom);
        LocalDate to = dateTo == null ? LocalDate.now() : LocalDate.parse(dateTo);
        List<Map<String, Object>> rows = service.profitSummary(principal(), groupBy, from, to);
        return Map.of("groupBy", groupBy, "dateFrom", from.toString(), "dateTo", to.toString(),
            "data", rows);
    }

    private AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw ApiException.unauthorized("authentication required");
        }
        return p;
    }
}
