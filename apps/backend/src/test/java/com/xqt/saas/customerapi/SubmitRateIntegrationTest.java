package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.rates.RateEngine;
import com.xqt.saas.rates.RateQuoteResponse.BreakdownLine;
import com.xqt.saas.rates.RateQuoteResponse.MatchEvidence;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 阶段 3 Submit 生产化测试：
 *   - quote.breakdown → 拆 AR/AP 多费用行
 *   - blockers → 阻断
 *   - strict 模式报价失败 → 阻断（不 fallback）
 *   - 取号成功 → 累加 channel_account_daily_usage
 */
class SubmitRateIntegrationTest {
    private static final String TENANT = "tenant-1";

    private CustomerApiPrincipal principal() {
        return new CustomerApiPrincipal("cred-1", TENANT, "cust-1", "CUST001", "60000DEMO", "secret");
    }

    private CustomerApiRepository baseRepo(String metadataJson) {
        CustomerApiRepository repo = mock(CustomerApiRepository.class);
        when(repo.findOrderForCustomerApi(TENANT, "cust-1", "ORD-S1"))
            .thenReturn(new HashMap<>(Map.of(
                "order_id", "00000000-0000-0000-0000-000000000001",
                "order_no", "DOC-ORD-S1",
                "customer_ref", "ORD-S1",
                "status", "DRAFT",
                "metadata", metadataJson
            )));
        when(repo.findChannelIdByCode(TENANT, "EU-AIR-UPS")).thenReturn("ch-1");
        when(repo.insertShipment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("ship-1");
        when(repo.markOrderSubmitted("00000000-0000-0000-0000-000000000001")).thenReturn(1);
        when(repo.findChargeItemIdByCode(any(), any())).thenReturn("ci-1");
        // 预付账户余额充足
        when(repo.findCustomerBalanceAccount(eq(TENANT), eq("cust-1"), any()))
            .thenReturn(Map.of("id", "acct-1", "balance", new BigDecimal("100000")));
        when(repo.decrementBalance(eq("acct-1"), any())).thenReturn(true);
        when(repo.insertChargeLine(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("charge-1");
        return repo;
    }

    private CustomerApiService service(CustomerApiRepository repo, RateEngine engine) {
        JsonSupport json = new JsonSupport(new ObjectMapper());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn(TENANT);
        return new CustomerApiService(repo, json, jdbc,
            new CarrierGatewayRegistry(List.of(new NoopCarrierGateway()), new NoopCarrierGateway(), jdbc),
            engine);
    }

    private Quote fullQuote() {
        MatchEvidence ev = new MatchEvidence("rc-1", "line-1", "rc-ap", null, null,
            "ACC-001", "rrr-1", null, "0", List.of());
        return new Quote(
            "EU-AIR-UPS", "欧洲空派", "CNY", "NONE",
            new BigDecimal("1.5"), BigDecimal.ZERO, new BigDecimal("1.5"),
            new BigDecimal("100.00"),  // freight
            new BigDecimal("18.50"),   // fuel
            new BigDecimal("0.00"),    // surcharge
            new BigDecimal("0.00"),    // commission
            new BigDecimal("118.50"),  // total
            new BigDecimal("80.00"),   // costFreight
            new BigDecimal("14.80"),   // costFuel
            new BigDecimal("0.00"),    // costSurcharge
            new BigDecimal("94.80"),   // costTotal
            new BigDecimal("0.185"),
            ev,
            List.of(new BreakdownLine("FREIGHT", "基础运费", new BigDecimal("100.00"))),
            List.of()
        );
    }

    private static final String META =
        "{\"acc_compat\":{\"product\":\"EU-AIR-UPS\",\"country\":\"US\",\"weight\":\"1.5\","
        + "\"piece\":1,\"currency\":\"CNY\",\"channelAccount\":\"ACC-001\"}}";

    // ─── 1. quote.breakdown → 拆 AR/AP 多费用行 ───
    @Test
    void submitSplitsArAndApChargeLines() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any())).thenReturn(fullQuote());

        service(repo, engine).submitOrder(principal(), "ORD-S1");

        // AR: FREIGHT(100) + FUEL(18.5) = 2 行（surcharge=0 跳过）
        verify(repo, times(2)).insertChargeLine(any(), any(), any(), eq("AR"), any(), any(), any());
        // AP: FREIGHT(80) + FUEL(14.8) = 2 行（costSurcharge=0 跳过）
        verify(repo, times(2)).insertChargeLine(any(), any(), any(), eq("AP"), any(), any(), any());
        // 不再走旧的单笔合并 insertPrepaidCharge
        verify(repo, never()).insertPrepaidCharge(any(), any(), any(), any(), any(), any());
    }

    // ─── 2. blockers → 阻断 Submit ───
    @Test
    void submitBlockedWhenQuoteHasBlockers() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        Quote q = fullQuote();
        Quote blocked = new Quote(q.channelCode(), q.channelName(), q.currency(), q.remoteLevel(),
            q.actualWeightKg(), q.volumetricWeightKg(), q.chargeableWeightKg(),
            q.freight(), q.fuelAmount(), q.surchargeAmount(), q.commission(), q.totalAmount(),
            q.costFreight(), q.costFuel(), q.costSurcharge(), q.costTotal(), q.fuelRate(),
            q.matched(), q.breakdown(), List.of("battery not allowed"));
        when(engine.quote(eq(TENANT), any())).thenReturn(blocked);

        assertThatThrownBy(() -> service(repo, engine).submitOrder(principal(), "ORD-S1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("报价被拒");
    }

    // ─── 3. strict 模式 + 报价失败 → 阻断（不 fallback） ───
    @Test
    void submitStrictModeBlocksOnQuoteFailure() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any()))
            .thenThrow(ApiException.notFound("no active AR rate card"));

        CustomerApiService svc = service(repo, engine);
        ReflectionTestUtils.setField(svc, "strictQuote", true);

        assertThatThrownBy(() -> svc.submitOrder(principal(), "ORD-S1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("strict 模式");
    }

    // ─── 4. 非 strict + 报价失败 → 退化估算，仍能下单 ───
    @Test
    void submitNonStrictFallsBackOnQuoteFailure() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any()))
            .thenThrow(ApiException.notFound("no active AR rate card"));

        // 默认 strictQuote=false
        var result = service(repo, engine).submitOrder(principal(), "ORD-S1");

        assertThat(result.status()).isEqualTo("SUBMITTED");
        // 退化路径走单笔合并 insertPrepaidCharge
        verify(repo, times(1)).insertPrepaidCharge(any(), any(), any(), any(), any(), any());
    }

    // ─── 5. 取号成功 → 累加 channel_account_daily_usage ───
    @Test
    void submitBumpsChannelAccountUsage() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any())).thenReturn(fullQuote());

        service(repo, engine).submitOrder(principal(), "ORD-S1");

        verify(repo, times(1)).bumpChannelAccountUsage(
            eq(TENANT), eq("ch-1"), eq("ACC-001"), anyInt(), any());
    }

    // ─── 6. PreSubmit 与 Submit 报价金额一致（同一 RateEngine.quote 口径） ───
    @Test
    void preSubmitAndSubmitUseSameQuoteAmount() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any())).thenReturn(fullQuote());

        CustomerApiService svc = service(repo, engine);
        var pre = svc.preSubmitOrder(principal(), "ORD-S1");
        var sub = svc.submitOrder(principal(), "ORD-S1");

        // PreSubmit 的预估金额 = Submit 实际预扣（quote.totalAmount = 118.50），口径一致
        assertThat(pre.estimatedAmount()).isEqualByComparingTo("118.50");
        assertThat(sub.status()).isEqualTo("SUBMITTED");
        assertThat(pre.canSubmit()).isTrue();
        assertThat(pre.blockers()).isEmpty();
    }

    // ─── 7. PreSubmit 命中 blocker 时 canSubmit=false ───
    @Test
    void preSubmitReflectsQuoteBlockers() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        Quote q = fullQuote();
        Quote blocked = new Quote(q.channelCode(), q.channelName(), q.currency(), q.remoteLevel(),
            q.actualWeightKg(), q.volumetricWeightKg(), q.chargeableWeightKg(),
            q.freight(), q.fuelAmount(), q.surchargeAmount(), q.commission(), q.totalAmount(),
            q.costFreight(), q.costFuel(), q.costSurcharge(), q.costTotal(), q.fuelRate(),
            q.matched(), q.breakdown(), List.of("battery not allowed"));
        when(engine.quote(eq(TENANT), any())).thenReturn(blocked);

        var pre = service(repo, engine).preSubmitOrder(principal(), "ORD-S1");

        assertThat(pre.canSubmit()).isFalse();
        assertThat(pre.blockers()).contains("battery not allowed");
    }

    // ─── 8. 预扣写资金流水（DEBIT/PREPAY，before/after 正确） ───
    @Test
    void submitWritesPrepayBalanceLedger() {
        CustomerApiRepository repo = baseRepo(META);
        RateEngine engine = mock(RateEngine.class);
        when(engine.quote(eq(TENANT), any())).thenReturn(fullQuote());

        service(repo, engine).submitOrder(principal(), "ORD-S1");

        // 余额 100000，预扣 118.50 → after 99881.50；direction=DEBIT, biz=PREPAY
        verify(repo, times(1)).recordBalanceLedger(
            eq(TENANT), eq("acct-1"), eq("CUSTOMER"), eq("cust-1"),
            eq("PREPAY"), eq("order"), eq("DOC-ORD-S1"), eq("CNY"), eq("DEBIT"),
            eq(new BigDecimal("118.50")),
            eq(new BigDecimal("100000")), eq(new BigDecimal("99881.50")),
            any(), any());
    }
}
