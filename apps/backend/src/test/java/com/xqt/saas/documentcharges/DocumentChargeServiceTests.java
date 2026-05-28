package com.xqt.saas.documentcharges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.xqt.saas.documentcharges.DocumentChargeResponses.GenerateResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.InvoiceResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.SettleResult;
import com.xqt.saas.documentcharges.DocumentChargeResponses.VoidResult;
import com.xqt.saas.framework.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DocumentChargeService 单元测试。
 *
 * 覆盖 docs/acc-remaining-logic-implementation-guide.md §4.6 的 12 项最小要求中
 * 与 service 层相关的 8 项（其余 4 项在 RateEngineTests / 集成测试覆盖）。
 */
class DocumentChargeServiceTests {
    private static final String TENANT = "tenant-1";

    private DocumentChargeRepository repo;
    private AuditService auditService;
    private JdbcTemplate jdbc;
    private DocumentChargeService service;

    private AuthPrincipal principal() {
        return new AuthPrincipal("user-1", TENANT, "xqt", "admin", "Admin",
            List.of("admin"), List.of(), 0L, "jti");
    }

    @BeforeEach
    void setup() {
        repo = Mockito.mock(DocumentChargeRepository.class);
        auditService = Mockito.mock(AuditService.class);
        jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        service = new DocumentChargeService(repo, auditService, jdbc);
    }

    // ─── 1. generateFromOrder：AR/AP ESTIMATED → CONFIRMED ───
    @Test
    void generateFromOrderConfirmsBothSides() {
        when(repo.confirmEstimatedCharges(TENANT, "order-1", "AR")).thenReturn(2);
        when(repo.confirmEstimatedCharges(TENANT, "order-1", "AP")).thenReturn(2);
        lenient().when(jdbc.queryForMap(anyString(), any(Object[].class)))
            .thenReturn(Map.of(
                "ar_total", new BigDecimal("148.13"),
                "ap_total", new BigDecimal("100.00")));

        GenerateResult r = service.generateFromOrder(principal(),
            new GenerateFromOrder("order-1", true, null));

        assertThat(r.arCount()).isEqualTo(2);
        assertThat(r.apCount()).isEqualTo(2);
        assertThat(r.profitEstimate()).isEqualByComparingTo("48.13");
    }

    // ─── 2. generateFromOrder includeAp=false ───
    @Test
    void generateFromOrderSkipsApWhenFalse() {
        when(repo.confirmEstimatedCharges(TENANT, "order-1", "AR")).thenReturn(1);
        lenient().when(jdbc.queryForMap(anyString(), any(Object[].class)))
            .thenReturn(Map.of(
                "ar_total", new BigDecimal("125"),
                "ap_total", BigDecimal.ZERO));

        GenerateResult r = service.generateFromOrder(principal(),
            new GenerateFromOrder("order-1", false, null));

        assertThat(r.arCount()).isEqualTo(1);
        assertThat(r.apCount()).isEqualTo(0);
        verify(repo, times(0)).confirmEstimatedCharges(any(), any(), eq("AP"));
    }

    // ─── 3. generateFromOrder 缺 orderId 报错 ───
    @Test
    void generateFromOrderRejectsMissingOrderId() {
        assertThatThrownBy(() -> service.generateFromOrder(principal(),
            new GenerateFromOrder(null, true, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("orderId is required");
    }

    // ─── 4. 客户账单：日期段聚合写一张账单 + lines ───
    @Test
    void generateCustomerInvoiceAggregatesAuditedCharges() {
        when(repo.findBillableArCharges(eq(TENANT), eq("cust-1"), any(), any(), eq("CNY")))
            .thenReturn(List.of(
                Map.of("id", "ch-1", "unpaid_amount", new BigDecimal("100"),
                       "shipment_id", "sh-1"),
                Map.of("id", "ch-2", "unpaid_amount", new BigDecimal("48.13"),
                       "shipment_id", "sh-2")
            ));
        when(repo.nextInvoiceNo(TENANT, "INV")).thenReturn("INV20260527001");
        when(repo.insertCustomerInvoice(eq(TENANT), eq("cust-1"), eq("INV20260527001"),
            eq("CNY"), any(BigDecimal.class), any())).thenReturn("inv-1");

        InvoiceResult r = service.generateCustomerInvoice(principal(),
            new GenerateCustomerInvoice("cust-1", LocalDate.now().minusDays(30),
                LocalDate.now(), "CNY", null, null));

        assertThat(r.invoiceId()).isEqualTo("inv-1");
        assertThat(r.lineCount()).isEqualTo(2);
        assertThat(r.totalAmount()).isEqualByComparingTo("148.13");
        verify(repo, times(2)).insertCustomerInvoiceLine(any(), any(), any(), any(), any());
        verify(repo).markChargesBilled(TENANT, List.of("ch-1", "ch-2"), "INV20260527001");
    }

    // ─── 5. 客户账单：显式 chargeIds 覆盖日期 ───
    @Test
    void generateCustomerInvoiceHonorsExplicitIds() {
        when(repo.findChargesByIds(TENANT, List.of("ch-x")))
            .thenReturn(List.of(Map.of("id", "ch-x", "unpaid_amount", new BigDecimal("80"),
                "shipment_id", "sh-x")));
        when(repo.nextInvoiceNo(TENANT, "INV")).thenReturn("INV-X");
        when(repo.insertCustomerInvoice(any(), any(), any(), any(), any(), any())).thenReturn("inv-x");

        InvoiceResult r = service.generateCustomerInvoice(principal(),
            new GenerateCustomerInvoice("cust-1", LocalDate.now(), LocalDate.now(),
                "CNY", null, List.of("ch-x")));

        assertThat(r.totalAmount()).isEqualByComparingTo("80");
        verify(repo, times(0)).findBillableArCharges(any(), any(), any(), any(), any());
    }

    // ─── 6. 客户账单：无可计入费用报错 ───
    @Test
    void generateCustomerInvoiceRejectsEmptySelection() {
        when(repo.findBillableArCharges(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateCustomerInvoice(principal(),
            new GenerateCustomerInvoice("cust-1", LocalDate.now().minusDays(7),
                LocalDate.now(), "CNY", null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("no billable charges");
    }

    // ─── 7. 收款全核销 → writeoff_status=PAID ───
    @Test
    void settleCustomerInvoicePaysFull() {
        when(repo.findInvoice(TENANT, "inv-1")).thenReturn(Map.of(
            "id", "inv-1", "invoice_no", "INV-1", "currency", "CNY",
            "customer_id", "cust-1",
            "total_amount", new BigDecimal("100"),
            "paid_amount", BigDecimal.ZERO,
            "writeoff_status", "UNPAID"));
        when(repo.insertPayment(TENANT, "cust-1", new BigDecimal("100"), "CNY", "PAY-001"))
            .thenReturn("pay-1");
        when(repo.applyPaymentToInvoice(TENANT, "inv-1", new BigDecimal("100")))
            .thenReturn(Map.of(
                "invoice_no", "INV-1",
                "paid_amount", new BigDecimal("100"),
                "unpaid_amount", BigDecimal.ZERO,
                "writeoff_status", "PAID"));

        SettleResult r = service.settleCustomerInvoice(principal(),
            new SettleCustomerInvoice("inv-1", new BigDecimal("100"), null, null, null, "PAY-001", null));

        assertThat(r.paidAmount()).isEqualByComparingTo("100");
        assertThat(r.unpaidAmount()).isEqualByComparingTo("0");
        assertThat(r.writeoffStatus()).isEqualTo("PAID");
        assertThat(r.paymentId()).isEqualTo("pay-1");
    }

    // ─── 8. 收款部分核销 → writeoff_status=PARTIAL ───
    @Test
    void settleCustomerInvoicePartialPay() {
        when(repo.findInvoice(TENANT, "inv-1")).thenReturn(Map.of(
            "id", "inv-1", "invoice_no", "INV-1", "currency", "CNY",
            "customer_id", "cust-1",
            "total_amount", new BigDecimal("100"),
            "paid_amount", BigDecimal.ZERO,
            "writeoff_status", "UNPAID"));
        when(repo.insertPayment(any(), any(), any(), any(), any())).thenReturn("pay-2");
        when(repo.applyPaymentToInvoice(TENANT, "inv-1", new BigDecimal("40")))
            .thenReturn(Map.of(
                "invoice_no", "INV-1",
                "paid_amount", new BigDecimal("40"),
                "unpaid_amount", new BigDecimal("60"),
                "writeoff_status", "PARTIAL"));

        SettleResult r = service.settleCustomerInvoice(principal(),
            new SettleCustomerInvoice("inv-1", new BigDecimal("40"), null, null, null, null, null));

        assertThat(r.writeoffStatus()).isEqualTo("PARTIAL");
        assertThat(r.unpaidAmount()).isEqualByComparingTo("60");
    }

    // ─── 9. 反核销：amount 负数应被接受 ───
    @Test
    void settleAllowsNegativeForReversal() {
        when(repo.findInvoice(TENANT, "inv-1")).thenReturn(Map.of(
            "id", "inv-1", "invoice_no", "INV-1", "currency", "CNY",
            "customer_id", "cust-1",
            "total_amount", new BigDecimal("100"),
            "paid_amount", new BigDecimal("100"),
            "writeoff_status", "PAID"));
        when(repo.insertPayment(any(), any(), any(), any(), any())).thenReturn("pay-rev");
        when(repo.applyPaymentToInvoice(TENANT, "inv-1", new BigDecimal("-100")))
            .thenReturn(Map.of(
                "invoice_no", "INV-1",
                "paid_amount", BigDecimal.ZERO,
                "unpaid_amount", new BigDecimal("100"),
                "writeoff_status", "UNPAID"));

        SettleResult r = service.settleCustomerInvoice(principal(),
            new SettleCustomerInvoice("inv-1", new BigDecimal("-100"), null, null, null, null, null));

        assertThat(r.writeoffStatus()).isEqualTo("UNPAID");
        assertThat(r.paidAmount()).isEqualByComparingTo("0");
    }

    // ─── 10. settle：发票不存在 ───
    @Test
    void settleRejectsMissingInvoice() {
        when(repo.findInvoice(TENANT, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.settleCustomerInvoice(principal(),
            new SettleCustomerInvoice("missing", new BigDecimal("10"), null, null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("invoice not found");
    }

    // ─── 11. void：返回退款金额 ───
    @Test
    void voidChargeReturnsRefundedAmount() {
        when(repo.voidChargeAndRefund(TENANT, "ch-1")).thenReturn(new BigDecimal("125.50"));

        VoidResult r = service.voidCharge(principal(), "ch-1");

        assertThat(r.chargeId()).isEqualTo("ch-1");
        assertThat(r.refundedToBalance()).isEqualByComparingTo("125.50");
    }

    // ─── 12. 利润聚合：byCustomer/byChannel/byMonth ───
    @Test
    void profitSummaryDelegatesToRepository() {
        when(repo.aggregateProfit(eq(TENANT), eq("customer"), any(), any())).thenReturn(List.of(
            Map.of("dim", "cust-1", "revenue", new BigDecimal("1000"),
                   "cost", new BigDecimal("700"), "profit", new BigDecimal("300"),
                   "shipment_count", 5)
        ));

        List<Map<String, Object>> rows = service.profitSummary(principal(), "customer",
            LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("profit")).isEqualTo(new BigDecimal("300"));
    }

    // ─── 13. 供应商账单：日期段聚合 AP 费用写一张 partner_invoice ───
    @Test
    void generatePartnerInvoiceAggregatesApCharges() {
        when(repo.findBillableApCharges(eq(TENANT), eq("partner-1"), any(), any(), eq("CNY")))
            .thenReturn(List.of(
                Map.of("id", "ap-1", "unpaid_amount", new BigDecimal("80"),
                       "shipment_id", "sh-1", "charge_item_id", "ci-1"),
                Map.of("id", "ap-2", "unpaid_amount", new BigDecimal("14.80"),
                       "shipment_id", "sh-1", "charge_item_id", "ci-2")
            ));
        when(repo.nextInvoiceNo(TENANT, "PINV")).thenReturn("PINV20260528001");
        when(repo.insertPartnerInvoice(eq(TENANT), eq("partner-1"), eq("PINV20260528001"),
            eq("CNY"), any(BigDecimal.class))).thenReturn("pinv-1");

        InvoiceResult r = service.generatePartnerInvoice(principal(),
            new GeneratePartnerInvoice("partner-1", LocalDate.now().minusDays(30),
                LocalDate.now(), "CNY", null));

        assertThat(r.invoiceId()).isEqualTo("pinv-1");
        assertThat(r.lineCount()).isEqualTo(2);
        assertThat(r.totalAmount()).isEqualByComparingTo("94.80");
        verify(repo, times(2)).insertPartnerInvoiceLine(any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ─── 14. 供应商账单：无可计入 AP 费用报错 ───
    @Test
    void generatePartnerInvoiceRejectsEmpty() {
        when(repo.findBillableApCharges(any(), any(), any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.generatePartnerInvoice(principal(),
            new GeneratePartnerInvoice("partner-1", LocalDate.now().minusDays(7),
                LocalDate.now(), "CNY", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("no billable AP charges");
    }

    // ─── 15. 供应商付款全额核销 → PAID + 回写 AP charges + fx 快照 ───
    @Test
    void settlePartnerInvoicePaysFull() {
        when(repo.findPartnerInvoice(TENANT, "pinv-1")).thenReturn(Map.of(
            "id", "pinv-1", "invoice_no", "PINV-1", "currency", "CNY",
            "partner_id", "partner-1",
            "total_amount", new BigDecimal("94.80"),
            "paid_amount", BigDecimal.ZERO,
            "writeoff_status", "UNPAID"));
        when(repo.insertPartnerPayment(eq(TENANT), eq("partner-1"), eq(new BigDecimal("94.80")),
            eq("CNY"), any(), any())).thenReturn("pp-1");
        when(repo.applyPaymentToPartnerInvoice(TENANT, "pinv-1", new BigDecimal("94.80")))
            .thenReturn(Map.of(
                "invoice_no", "PINV-1",
                "paid_amount", new BigDecimal("94.80"),
                "unpaid_amount", BigDecimal.ZERO,
                "writeoff_status", "PAID"));

        SettleResult r = service.settlePartnerInvoice(principal(),
            new SettlePartnerInvoice("pinv-1", new BigDecimal("94.80"), null, null, "PAY-AP", null));

        assertThat(r.writeoffStatus()).isEqualTo("PAID");
        assertThat(r.paymentId()).isEqualTo("pp-1");
        // 金额动作必须记一笔汇率快照
        verify(repo, times(1)).insertFxSnapshot(eq(TENANT), eq("CNY"), eq("CNY"), any(), any());
    }

    // ─── 16. 供应商付款：发票不存在报错 ───
    @Test
    void settlePartnerInvoiceRejectsMissing() {
        when(repo.findPartnerInvoice(TENANT, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.settlePartnerInvoice(principal(),
            new SettlePartnerInvoice("missing", new BigDecimal("10"), null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("partner invoice not found");
    }
}
