package com.xqt.saas.rates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RateEngine 单元测试。
 *
 * 覆盖文档 §3.6 列出的 12 项最小要求：
 *   1. 普通价
 *   2. 客户专属价覆盖普通价
 *   3. 客户组价覆盖普通价
 *   4. 客户价优先于客户组价
 *   5. 偏远邮编
 *   6. 禁运邮编
 *   7. 电池不允许
 *   8. 仿牌/敏感货不允许
 *   9. 渠道账号超重
 *  10. 渠道账号超件数
 *  11. 佣金计算
 *  12. Submit 调用报价并写 AR/AP 费用（在 SubmitOrderRateIntegrationTest 中验）
 */
class RateEngineTests {
    private static final String TENANT = "tenant-1";
    private static final String CHANNEL_ID = "channel-1";
    private static final String CHANNEL_CODE = "EU-AIR-UPS";
    private static final LocalDate DATE = LocalDate.of(2026, 5, 12);
    private static final BigDecimal CHARGEABLE_5 = new BigDecimal("5.000");
    private static final BigDecimal CHARGEABLE_10 = new BigDecimal("10.000");

    // ─── 辅助 ───
    private RateRepository repo;
    private JdbcTemplate jdbc;
    private RateEngine engine;

    private void setupMocks() {
        repo = Mockito.mock(RateRepository.class);
        jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        when(repo.findChannelByCode(TENANT, CHANNEL_CODE)).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", CHANNEL_CODE, "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(new BigDecimal("0.185"));
        // Mockito 对 Map 返回类型默认返回空 Map（不是 null）！所有"找不到时应该 null"的方法必须显式 stub。
        lenient().when(repo.findCustomerSpecificRateCard(any(), any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findCustomerGroupRateCard(any(), any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findServiceRestriction(any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findChannelAccountLimit(any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findCommissionRule(any(), any(), any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findRemoteRateRule(any(), any(), any(), any())).thenReturn(null);
        lenient().when(repo.findActiveRateCard(any(), any(), eq("AP"), any(), any())).thenReturn(null);
        engine = new RateEngine(repo, jdbc);
    }

    private Map<String, Object> baseTier(String id, BigDecimal unitPrice) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("zone_code", "US-Z005");
        m.put("weight_from", new BigDecimal("1"));
        m.put("weight_to", new BigDecimal("21"));
        m.put("uom", "KG");
        m.put("unit_price", unitPrice);
        m.put("min_amount", BigDecimal.ZERO);
        m.put("calculation_type", "PER_KG");
        m.put("postal_priority", 0);
        return m;
    }

    private RateQuoteRequest baseRequest(BigDecimal weight) {
        return new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null,
            "US", null, weight, 1, null, null, "CNY", DATE,
            0, 0, 0);
    }

    private RateQuoteRequest withBattery(int batteryType) {
        return new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, "ACC-001",
            "US", null, new BigDecimal("5"), 1, null, null, "CNY", DATE,
            batteryType, 0, 0);
    }

    private RateQuoteRequest withSpecial(int specialType) {
        return new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, "ACC-001",
            "US", null, new BigDecimal("5"), 1, null, null, "CNY", DATE,
            0, specialType, 0);
    }

    private RateQuoteRequest withCustomer(String customerId) {
        return new RateQuoteRequest(
            customerId, null, CHANNEL_CODE, null, null,
            "US", null, new BigDecimal("5"), 1, null, null, "CNY", DATE,
            0, 0, 0);
    }

    private RateQuoteRequest withCustomerAndGroup(String customerId, String groupId) {
        return new RateQuoteRequest(
            customerId, groupId, CHANNEL_CODE, null, null,
            "US", null, new BigDecimal("5"), 1, null, null, "CNY", DATE,
            0, 0, 0);
    }

    private RateQuoteRequest withAccountAndWeight(String accountCode, BigDecimal weight, int pieces) {
        return new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, accountCode,
            "US", null, weight, pieces, null, null, "CNY", DATE,
            0, 0, 0);
    }

    // ═══════════════ 1. 普通价 ═══════════════
    @Test
    void caseBasePriceWithFuel() {
        setupMocks();
        lenient().when(repo.findActiveRateCard(eq(TENANT), eq(CHANNEL_ID), eq("AR"), eq("CNY"), any()))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        lenient().when(repo.findRemoteLevel(eq(TENANT), eq(CHANNEL_ID), eq("US"), any()))
            .thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), any()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        Quote q = engine.quote(TENANT, baseRequest(new BigDecimal("5")));

        assertThat(q.freight()).isEqualByComparingTo("125.00");
        assertThat(q.fuelAmount()).isEqualByComparingTo("23.13");
        assertThat(q.surchargeAmount()).isEqualByComparingTo("0.00");
        assertThat(q.totalAmount()).isEqualByComparingTo("148.13");
        assertThat(q.matched().rateCardId()).isEqualTo("rc-base");
        assertThat(q.matched().customerRateMatched()).isNull();
        assertThat(q.blockers()).isEmpty();
    }

    // ═══════════════ 2. 客户专属价覆盖普通价 ═══════════════
    @Test
    void caseCustomerSpecificPriceOverridesBase() {
        setupMocks();
        when(repo.findCustomerSpecificRateCard(TENANT, "cust-1", CHANNEL_ID, null, DATE))
            .thenReturn(Map.of("id", "crc-1", "rate_card_id", "rc-cust", "priority", 100));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-cust"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-cust", new BigDecimal("18.0"))); // 折扣价

        Quote q = engine.quote(TENANT, withCustomer("cust-1"));

        assertThat(q.matched().rateCardId()).isEqualTo("rc-cust");
        assertThat(q.matched().customerRateMatched()).isEqualTo("crc-1");
        assertThat(q.freight()).isEqualByComparingTo("90.00"); // 18 * 5
    }

    // ═══════════════ 3. 客户组价覆盖普通价（且无客户价时使用）═══════════════
    @Test
    void caseGroupPriceOverridesBaseWhenNoCustomerSpecific() {
        setupMocks();
        // 无客户价
        when(repo.findCustomerSpecificRateCard(TENANT, null, CHANNEL_ID, null, DATE)).thenReturn(null);
        when(repo.findCustomerGroupRateCard(TENANT, "grp-1", CHANNEL_ID, null, DATE))
            .thenReturn(Map.of("id", "cgrc-1", "rate_card_id", "rc-grp", "priority", 50));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-grp"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-grp", new BigDecimal("22.0")));

        Quote q = engine.quote(TENANT, withCustomerAndGroup(null, "grp-1"));

        assertThat(q.matched().rateCardId()).isEqualTo("rc-grp");
        assertThat(q.matched().groupRateMatched()).isEqualTo("cgrc-1");
        assertThat(q.freight()).isEqualByComparingTo("110.00");
    }

    // ═══════════════ 4. 客户专属价优先于客户组价 ═══════════════
    @Test
    void caseCustomerSpecificBeatsGroup() {
        setupMocks();
        when(repo.findCustomerSpecificRateCard(TENANT, "cust-1", CHANNEL_ID, null, DATE))
            .thenReturn(Map.of("id", "crc-1", "rate_card_id", "rc-cust", "priority", 100));
        when(repo.findCustomerGroupRateCard(TENANT, "grp-1", CHANNEL_ID, null, DATE))
            .thenReturn(Map.of("id", "cgrc-1", "rate_card_id", "rc-grp", "priority", 50));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-cust"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-cust", new BigDecimal("15.0")));

        Quote q = engine.quote(TENANT, withCustomerAndGroup("cust-1", "grp-1"));

        assertThat(q.matched().rateCardId()).isEqualTo("rc-cust");
        assertThat(q.matched().customerRateMatched()).isEqualTo("crc-1");
        assertThat(q.matched().groupRateMatched()).isNull();
    }

    // ═══════════════ 5. 偏远邮编（按 remote_rate_rules）═══════════════
    @Test
    void caseRemotePostcodeAddsConfiguredSurcharge() {
        setupMocks();
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "GB", "HS1 0AB")).thenReturn("REMOTE");
        when(repo.findRemoteRateRule(TENANT, CHANNEL_ID, "REMOTE", DATE)).thenReturn(Map.of(
            "id", "rrr-1", "rate_type", "PERCENT", "rate", new BigDecimal("0.20") // 20% 自定义
        ));
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), eq("HS1 0AB")))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        RateQuoteRequest req = new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null,
            "GB", "HS1 0AB", new BigDecimal("5"), 1, null, null, "CNY", DATE,
            0, 0, 0);
        Quote q = engine.quote(TENANT, req);

        assertThat(q.surchargeAmount()).isEqualByComparingTo("25.00"); // 125 * 0.20
        assertThat(q.matched().remoteRuleId()).isEqualTo("rrr-1");
    }

    // ═══════════════ 6. 禁运邮编 ═══════════════
    @Test
    void caseEmbargoIsBlocked() {
        setupMocks();
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "IR", null)).thenReturn("EMBARGO");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        RateQuoteRequest req = new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null,
            "IR", null, new BigDecimal("5"), 1, null, null, "CNY", DATE,
            0, 0, 0);
        Quote q = engine.quote(TENANT, req);

        assertThat(q.blockers()).anyMatch(s -> s.startsWith("目的地") && s.contains("禁运"));
    }

    // ═══════════════ 7. 电池不允许 ═══════════════
    @Test
    void caseBatteryDisallowedBlocks() {
        setupMocks();
        when(repo.findServiceRestriction(TENANT, CHANNEL_ID, "ACC-001", null)).thenReturn(Map.of(
            "id", "sr-1", "battery_allowed", false,
            "battery_built_in_allowed", true, "battery_dry_allowed", true,
            "sensitive_allowed", true, "brand_allowed", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        Quote q = engine.quote(TENANT, withBattery(2));

        assertThat(q.blockers()).contains("此渠道不接受带电池货物");
    }

    // ═══════════════ 8. 仿牌不允许 ═══════════════
    @Test
    void caseBrandCounterfeitDisallowedBlocks() {
        setupMocks();
        when(repo.findServiceRestriction(TENANT, CHANNEL_ID, "ACC-001", null)).thenReturn(Map.of(
            "id", "sr-1", "battery_allowed", true,
            "battery_built_in_allowed", true, "battery_dry_allowed", true,
            "sensitive_allowed", true, "brand_allowed", false
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        Quote q = engine.quote(TENANT, withSpecial(5));

        assertThat(q.blockers()).contains("此渠道不接受仿牌商品");
    }

    // ═══════════════ 9. 渠道账号超重 ═══════════════
    @Test
    void caseChannelAccountWeightExceeded() {
        setupMocks();
        when(repo.findChannelAccountLimit(TENANT, CHANNEL_ID, "ACC-001", DATE)).thenReturn(Map.of(
            "id", "lim-1", "max_count", 0, "max_piece", 0,
            "max_weight", new BigDecimal("8.000"),
            "count_used", 0, "piece_used", 0, "weight_used", new BigDecimal("5.000")
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        Quote q = engine.quote(TENANT, withAccountAndWeight("ACC-001", new BigDecimal("5"), 1));

        // 已用 5 + 新增 5 = 10 > 8（max_weight），超限
        assertThat(q.blockers()).anyMatch(s -> s.startsWith("渠道账号当日重量已达上限"));
    }

    // ═══════════════ 10. 渠道账号超件数 ═══════════════
    @Test
    void caseChannelAccountPieceExceeded() {
        setupMocks();
        when(repo.findChannelAccountLimit(TENANT, CHANNEL_ID, "ACC-001", DATE)).thenReturn(Map.of(
            "id", "lim-1", "max_count", 0,
            "max_piece", 10, "max_weight", BigDecimal.ZERO,
            "count_used", 0, "piece_used", 9, "weight_used", BigDecimal.ZERO
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));

        Quote q = engine.quote(TENANT, withAccountAndWeight("ACC-001", new BigDecimal("5"), 3));

        // 已用 9 + 新增 3 = 12 > 10
        assertThat(q.blockers()).anyMatch(s -> s.startsWith("渠道账号当日件数已达上限"));
    }

    // ═══════════════ 11. 佣金计算 ═══════════════
    @Test
    void caseCommissionApplied() {
        setupMocks();
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-1", new BigDecimal("25.0")));
        when(repo.findCommissionRule(TENANT, "cust-1", null, CHANNEL_ID, null, DATE)).thenReturn(Map.of(
            "id", "cr-1", "rule_type", "RATE", "rate", new BigDecimal("0.10")
        ));

        Quote q = engine.quote(TENANT, withCustomer("cust-1"));

        // basis = freight 125 + fuel 23.13 + remote 0 = 148.13, 10% = 14.81
        assertThat(q.commission()).isEqualByComparingTo("14.81");
        assertThat(q.matched().commissionRuleId()).isEqualTo("cr-1");
    }

    // ═══════════════ 多段计费：FIRST_CONTINUED ═══════════════
    @Test
    void caseFirstContinuedTier() {
        setupMocks();
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-base", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");

        Map<String, Object> tier = new HashMap<>(baseTier("line-fc", new BigDecimal("0")));
        tier.put("calculation_type", "FIRST_CONTINUED");
        tier.put("first_weight_kg", new BigDecimal("1.0"));
        tier.put("first_amount", new BigDecimal("50.0"));
        tier.put("continued_step_kg", new BigDecimal("0.5"));
        tier.put("continued_unit_price", new BigDecimal("8.0"));
        when(repo.findTier(eq(TENANT), eq("rc-base"), eq("US-Z005"), any(BigDecimal.class), isNull())).thenReturn(tier);

        Quote q = engine.quote(TENANT, baseRequest(new BigDecimal("10")));

        // 首重 1kg = 50；续重 (10-1)/0.5 = 18 段 × 8 = 144；合计 194
        assertThat(q.freight()).isEqualByComparingTo("194.00");
    }

    // ═══════════════ AP 成本价输出 ═══════════════
    @Test
    void caseCostPriceOutputWhenApCardExists() {
        setupMocks();
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-ar", "status", "ACTIVE"));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AP", "CNY", DATE))
            .thenReturn(Map.of("id", "rc-ap", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(eq(TENANT), eq("rc-ar"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-ar", new BigDecimal("25.0")));
        when(repo.findTier(eq(TENANT), eq("rc-ap"), eq("US-Z005"), any(BigDecimal.class), isNull()))
            .thenReturn(baseTier("line-ap", new BigDecimal("20.0"))); // 成本 20/kg

        Quote q = engine.quote(TENANT, baseRequest(new BigDecimal("5")));

        assertThat(q.costFreight()).isEqualByComparingTo("100.00");
        assertThat(q.costTotal()).isNotNull();
        assertThat(q.matched().costRateCardId()).isEqualTo("rc-ap");
    }

    // ═══════════════ 校验：channelCode 必填 ═══════════════
    @Test
    void rejectsWhenChannelMissing() {
        setupMocks();
        assertThatThrownBy(() -> engine.quote(TENANT, new RateQuoteRequest(
            null, null, "", null, null, "US", null, new BigDecimal("1"), 1, null, null,
            "CNY", DATE, 0, 0, 0
        ))).isInstanceOf(ApiException.class)
           .hasMessageContaining("channelCode is required");
    }

    // ═══════════════ 任务 S3 A1：品名关键词附加费 ═══════════════
    private static HashMap<String, Object> keywordRow(String feeCode, String unit, BigDecimal amount,
                                                       BigDecimal rate, int priority) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("fee_code", feeCode);
        m.put("charge_unit", unit);
        m.put("amount", amount);
        m.put("rate", rate);
        m.put("priority", priority);
        return m;
    }

    @Test
    void caseA1KeywordSurchargeFixedAmount() {
        setupMocks();
        when(repo.findKeywordSurcharges(eq(TENANT), any(), any())).thenReturn(java.util.List.of(
            keywordRow("BATTERY_SURCHARGE", "FIXED", new BigDecimal("100.00"), null, 10)
        ));

        java.util.List<RateQuoteResponse.BreakdownLine> lines = engine.applyKeywordSurcharges(
            TENANT, java.util.List.of("Lithium Battery"), new BigDecimal("200.00"), DATE);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).code()).isEqualTo("BATTERY_SURCHARGE");
        assertThat(lines.get(0).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void caseA1KeywordSurchargePercentOfFreight() {
        setupMocks();
        when(repo.findKeywordSurcharges(eq(TENANT), any(), any())).thenReturn(java.util.List.of(
            keywordRow("DANGEROUS_GOODS", "PCT", null, new BigDecimal("0.0500"), 5)
        ));

        java.util.List<RateQuoteResponse.BreakdownLine> lines = engine.applyKeywordSurcharges(
            TENANT, java.util.List.of("Magnetic Toy"), new BigDecimal("200.00"), DATE);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).code()).isEqualTo("DANGEROUS_GOODS");
        assertThat(lines.get(0).amount()).isEqualByComparingTo("10.00"); // 200 * 0.05
    }

    @Test
    void caseA1KeywordSurchargeEmptyDeclarations() {
        setupMocks();
        java.util.List<RateQuoteResponse.BreakdownLine> lines = engine.applyKeywordSurcharges(
            TENANT, java.util.List.of(), new BigDecimal("200.00"), DATE);
        assertThat(lines).isEmpty();
        // 空品名时不查 DB
        Mockito.verify(repo, Mockito.never()).findKeywordSurcharges(any(), any(), any());
    }

    // ═══════════════ 任务 S3 A4：按箱最低计费 ═══════════════
    @Test
    void caseA4MinWeightPerBoxFloorsChargeable() {
        setupMocks();
        lenient().when(repo.findActiveRateCard(eq(TENANT), eq(CHANNEL_ID), eq("AR"), eq("CNY"), any()))
            .thenReturn(Map.of("id", "rc-a4", "status", "ACTIVE"));
        lenient().when(repo.findRemoteLevel(eq(TENANT), eq(CHANNEL_ID), eq("US"), any()))
            .thenReturn("NONE");
        Map<String, Object> tier = baseTier("line-a4", new BigDecimal("10.0"));
        tier.put("min_weight_per_box", new BigDecimal("3.000")); // 单箱 3kg 底
        when(repo.findTier(eq(TENANT), eq("rc-a4"), eq("US-Z005"), any(BigDecimal.class), any()))
            .thenReturn(tier);

        // 2 箱 × 实重 1kg = 2kg。单箱最低 3kg → 总 6kg。 6 × 10 = 60
        Quote q = engine.quote(TENANT, new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null, "US", null,
            new BigDecimal("2"), 2, null, null, "CNY", DATE, 0, 0, 0));

        assertThat(q.freight()).isEqualByComparingTo("60.00");
    }

    @Test
    void caseA4MinAmountPerBoxFloorsFreight() {
        setupMocks();
        lenient().when(repo.findActiveRateCard(eq(TENANT), eq(CHANNEL_ID), eq("AR"), eq("CNY"), any()))
            .thenReturn(Map.of("id", "rc-a4b", "status", "ACTIVE"));
        lenient().when(repo.findRemoteLevel(eq(TENANT), eq(CHANNEL_ID), eq("US"), any()))
            .thenReturn("NONE");
        Map<String, Object> tier = baseTier("line-a4b", new BigDecimal("5.0"));
        tier.put("min_amount_per_box", new BigDecimal("30.00")); // 单箱 30 元底
        when(repo.findTier(eq(TENANT), eq("rc-a4b"), eq("US-Z005"), any(BigDecimal.class), any()))
            .thenReturn(tier);

        // 1 箱 × 2kg × 5元/kg = 10 元，被单箱最低 30 元拉到 30
        Quote q = engine.quote(TENANT, new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null, "US", null,
            new BigDecimal("2"), 1, null, null, "CNY", DATE, 0, 0, 0));

        assertThat(q.freight()).isEqualByComparingTo("30.00");
    }

    // ═══════════════ 任务 S3 A6：PER_CBM 按体积计费 ═══════════════
    @Test
    void caseA6PerCbmCalculation() {
        setupMocks();
        lenient().when(repo.findActiveRateCard(eq(TENANT), eq(CHANNEL_ID), eq("AR"), eq("CNY"), any()))
            .thenReturn(Map.of("id", "rc-cbm", "status", "ACTIVE"));
        lenient().when(repo.findRemoteLevel(eq(TENANT), eq(CHANNEL_ID), eq("US"), any()))
            .thenReturn("NONE");
        Map<String, Object> tier = baseTier("line-cbm", new BigDecimal("3000.0"));
        tier.put("calculation_type", "PER_CBM");
        when(repo.findTier(eq(TENANT), eq("rc-cbm"), eq("US-Z005"), any(BigDecimal.class), any()))
            .thenReturn(tier);

        // 0.5 cbm × 3000元/cbm = 1500
        Quote q = engine.quote(TENANT, new RateQuoteRequest(
            null, null, CHANNEL_CODE, null, null, "US", null,
            new BigDecimal("100"), 1, new BigDecimal("0.5"), null, "CNY", DATE, 0, 0, 0));

        assertThat(q.freight()).isEqualByComparingTo("1500.00");
    }
}
