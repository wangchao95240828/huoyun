package com.xqt.saas.rates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class RateEngineTests {
    private static final String TENANT = "tenant-1";
    private static final String CHANNEL_ID = "channel-1";

    @Test
    void quoteAppliesWeightTierAndFuelAndRemote() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);

        when(repo.findChannelByCode(TENANT, "EU-AIR-UPS")).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", "EU-AIR-UPS", "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", LocalDate.of(2026, 5, 12)))
            .thenReturn(Map.of("id", "rc-1", "version", "ACC-DEMO-2026Q2", "currency", "CNY", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(TENANT, "rc-1", "ZONE_A", new BigDecimal("5.000"))).thenReturn(Map.of(
            "id", "line-2", "zone_code", "ZONE_A",
            "weight_from", new BigDecimal("1"), "weight_to", new BigDecimal("21"),
            "uom", "KG", "unit_price", new BigDecimal("25.0"), "min_amount", BigDecimal.ZERO
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(new BigDecimal("0.185"));

        Quote quote = new RateEngine(repo, jdbc).quote(TENANT, new RateQuoteRequest(
            "EU-AIR-UPS", "US", null, new BigDecimal("5"), 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        ));

        assertThat(quote.channelCode()).isEqualTo("EU-AIR-UPS");
        assertThat(quote.chargeableWeightKg()).isEqualByComparingTo("5");
        assertThat(quote.freight()).isEqualByComparingTo("125.00"); // 25 * 5
        assertThat(quote.fuelAmount()).isEqualByComparingTo("23.13"); // 125 * 0.185 = 23.125 -> HALF_UP 23.13
        assertThat(quote.surchargeAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.totalAmount()).isEqualByComparingTo("148.13");
        assertThat(quote.remoteLevel()).isEqualTo("NONE");
        assertThat(quote.fuelRate()).isEqualByComparingTo("0.185");
        assertThat(quote.breakdown()).extracting(RateQuoteResponse.BreakdownLine::code)
            .containsExactly("FREIGHT", "FUEL");
    }

    @Test
    void remoteLevelAdds15Percent() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        when(repo.findChannelByCode(TENANT, "EU-AIR-UPS")).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", "EU-AIR-UPS", "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", LocalDate.of(2026, 5, 12)))
            .thenReturn(Map.of("id", "rc-1", "version", "ACC-DEMO-2026Q2", "currency", "CNY", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "GB", "HS1 0AB")).thenReturn("REMOTE");
        when(repo.findTier(TENANT, "rc-1", "ZONE_A", new BigDecimal("5.000"))).thenReturn(Map.of(
            "id", "line-2", "zone_code", "ZONE_A",
            "weight_from", new BigDecimal("1"), "weight_to", new BigDecimal("21"),
            "uom", "KG", "unit_price", new BigDecimal("25.0"), "min_amount", BigDecimal.ZERO
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(BigDecimal.ZERO);

        Quote quote = new RateEngine(repo, jdbc).quote(TENANT, new RateQuoteRequest(
            "EU-AIR-UPS", "GB", "HS1 0AB", new BigDecimal("5"), 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        ));

        assertThat(quote.remoteLevel()).isEqualTo("REMOTE");
        assertThat(quote.freight()).isEqualByComparingTo("125.00");
        assertThat(quote.surchargeAmount()).isEqualByComparingTo("18.75"); // 125 * 0.15
        assertThat(quote.totalAmount()).isEqualByComparingTo("143.75");
        assertThat(quote.breakdown()).extracting(RateQuoteResponse.BreakdownLine::code)
            .containsExactly("FREIGHT", "REMOTE");
    }

    @Test
    void volumetricWeightWins() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        // dim_factor=6000, volume=0.06 cbm => volumetric = 0.06 * 1_000_000 / 6000 = 10 kg
        when(repo.findChannelByCode(TENANT, "EU-AIR-UPS")).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", "EU-AIR-UPS", "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", LocalDate.of(2026, 5, 12)))
            .thenReturn(Map.of("id", "rc-1", "version", "ACC-DEMO-2026Q2", "currency", "CNY", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        when(repo.findTier(TENANT, "rc-1", "ZONE_A", new BigDecimal("10.000"))).thenReturn(Map.of(
            "id", "line-2", "zone_code", "ZONE_A",
            "weight_from", new BigDecimal("1"), "weight_to", new BigDecimal("21"),
            "uom", "KG", "unit_price", new BigDecimal("25.0"), "min_amount", BigDecimal.ZERO
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(BigDecimal.ZERO);

        Quote quote = new RateEngine(repo, jdbc).quote(TENANT, new RateQuoteRequest(
            "EU-AIR-UPS", "US", null, new BigDecimal("3"), 1, new BigDecimal("0.06"), null, "CNY", LocalDate.of(2026, 5, 12)
        ));

        assertThat(quote.actualWeightKg()).isEqualByComparingTo("3");
        assertThat(quote.volumetricWeightKg()).isEqualByComparingTo("10");
        assertThat(quote.chargeableWeightKg()).isEqualByComparingTo("10");
        assertThat(quote.freight()).isEqualByComparingTo("250.00");
    }

    @Test
    void minAmountFloors() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        when(repo.findChannelByCode(TENANT, "EU-AIR-UPS")).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", "EU-AIR-UPS", "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", LocalDate.of(2026, 5, 12)))
            .thenReturn(Map.of("id", "rc-1", "version", "ACC-DEMO-2026Q2", "currency", "CNY", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "US", null)).thenReturn("NONE");
        // 0.5 kg @ 30/kg = 15.00, min_amount = 30
        when(repo.findTier(TENANT, "rc-1", "ZONE_A", new BigDecimal("0.500"))).thenReturn(Map.of(
            "id", "line-1", "zone_code", "ZONE_A",
            "weight_from", new BigDecimal("0"), "weight_to", new BigDecimal("1"),
            "uom", "KG", "unit_price", new BigDecimal("30.0"), "min_amount", new BigDecimal("30")
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(BigDecimal.ZERO);

        Quote quote = new RateEngine(repo, jdbc).quote(TENANT, new RateQuoteRequest(
            "EU-AIR-UPS", "US", null, new BigDecimal("0.5"), 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        ));

        assertThat(quote.freight()).isEqualByComparingTo("30.00");
    }

    @Test
    void embargoRejected() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        when(repo.findChannelByCode(TENANT, "EU-AIR-UPS")).thenReturn(Map.of(
            "id", CHANNEL_ID, "code", "EU-AIR-UPS", "name", "欧洲空派 UPS",
            "dim_factor", new BigDecimal("6000"), "primary_uom", "KG", "active", true
        ));
        when(repo.findActiveRateCard(TENANT, CHANNEL_ID, "AR", "CNY", LocalDate.of(2026, 5, 12)))
            .thenReturn(Map.of("id", "rc-1", "version", "ACC-DEMO-2026Q2", "currency", "CNY", "status", "ACTIVE"));
        when(repo.findRemoteLevel(TENANT, CHANNEL_ID, "KP", null)).thenReturn("EMBARGO");
        when(repo.findTier(TENANT, "rc-1", "ZONE_A", new BigDecimal("5.000"))).thenReturn(Map.of(
            "id", "line-2", "zone_code", "ZONE_A",
            "weight_from", new BigDecimal("1"), "weight_to", new BigDecimal("21"),
            "uom", "KG", "unit_price", new BigDecimal("25.0"), "min_amount", BigDecimal.ZERO
        ));
        when(repo.findFuelRate(TENANT, CHANNEL_ID, "2026-05")).thenReturn(BigDecimal.ZERO);

        RateEngine engine = new RateEngine(repo, jdbc);
        RateQuoteRequest req = new RateQuoteRequest(
            "EU-AIR-UPS", "KP", null, new BigDecimal("5"), 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        );
        assertThatThrownBy(() -> engine.quote(TENANT, req)).isInstanceOf(ApiException.class);
    }

    @Test
    void unknownChannelThrows() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(TENANT);
        when(repo.findChannelByCode(TENANT, "UNKNOWN")).thenReturn(null);

        RateEngine engine = new RateEngine(repo, jdbc);
        RateQuoteRequest req = new RateQuoteRequest(
            "UNKNOWN", "US", null, new BigDecimal("5"), 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        );
        assertThatThrownBy(() -> engine.quote(TENANT, req)).isInstanceOf(ApiException.class);
    }

    @Test
    void missingWeightRejected() {
        RateRepository repo = Mockito.mock(RateRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        RateEngine engine = new RateEngine(repo, jdbc);
        RateQuoteRequest req = new RateQuoteRequest(
            "EU-AIR-UPS", "US", null, null, 1, null, null, "CNY", LocalDate.of(2026, 5, 12)
        );
        assertThatThrownBy(() -> engine.quote(TENANT, req)).isInstanceOf(ApiException.class);
    }
}
