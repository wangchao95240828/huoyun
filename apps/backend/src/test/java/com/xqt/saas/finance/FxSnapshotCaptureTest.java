package com.xqt.saas.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S6 单测：
 *  1. 同币种 → no-op
 *  2. 异币种命中 exchange_rates → snapshot 含 rate
 *  3. 异币种未命中 → fallback rate=1 + source=MISSING_RATE
 *  4. 11 类 biz_type 全部覆盖（参数化）
 *  5. DB 异常静默吞掉
 */
class FxSnapshotCaptureTest {
    private static final String TENANT = "tenant-1";

    private JdbcTemplate jdbc;
    private FxSnapshotCapture capture;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        capture = new FxSnapshotCapture(jdbc);
        // 默认租户 base_currency = CNY
        lenient().when(jdbc.queryForObject(contains("FROM tenants"), eq(String.class), anyString()))
            .thenReturn("CNY");
    }

    @Test
    void sameCurrencyNoSnapshot() {
        String id = capture.captureForLedger(TENANT, "CNY", "PREPAY", "order", "ORD-1");
        assertThat(id).isNull();
        verify(jdbc, never()).queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignCurrencyWritesSnapshotWithCurrentRate() {
        when(jdbc.queryForObject(contains("FROM exchange_rates"), eq(BigDecimal.class),
            anyString(), anyString(), anyString())).thenReturn(new BigDecimal("7.2500"));
        when(jdbc.queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("snap-1");

        String id = capture.captureForLedger(TENANT, "USD", "RECEIPT", "invoice", "INV-1");

        assertThat(id).isEqualTo("snap-1");
        verify(jdbc, times(1)).queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), eq(TENANT), eq("USD"), eq("CNY"),
            eq(new BigDecimal("7.2500")), eq("AUTO_LEDGER"),
            eq("RECEIPT"), eq("invoice"), eq("INV-1"));
    }

    @Test
    void missingRateFallsBackToOneWithMissingRateSource() {
        when(jdbc.queryForObject(contains("FROM exchange_rates"), eq(BigDecimal.class),
            anyString(), anyString(), anyString()))
            .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("snap-2");

        String id = capture.captureForLedger(TENANT, "EUR", "PAYMENT", "partner_invoice", "PI-1");

        assertThat(id).isEqualTo("snap-2");
        verify(jdbc, times(1)).queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), eq(TENANT), eq("EUR"), eq("CNY"),
            eq(BigDecimal.ONE), eq("MISSING_RATE"),
            eq("PAYMENT"), eq("partner_invoice"), eq("PI-1"));
    }

    @Test
    void dbFailureReturnsNullDoesNotThrow() {
        when(jdbc.queryForObject(contains("FROM tenants"), eq(String.class), anyString()))
            .thenThrow(new DataAccessResourceFailureException("db down"));

        String id = capture.captureForLedger(TENANT, "USD", "REFUND", "order", "ORD-X");
        assertThat(id).isNull();
    }

    static Stream<String> elevenBizTypes() {
        return Stream.of("PREPAY", "PREPAY_RELEASE", "RECEIPT", "PAYMENT", "REFUND",
            "ADJUST", "REBATE", "FINE", "REPARATION", "VOID", "FX_DIFF");
    }

    @ParameterizedTest
    @MethodSource("elevenBizTypes")
    void all11BizTypesTriggerSnapshotForForeignCurrency(String bizType) {
        when(jdbc.queryForObject(contains("FROM exchange_rates"), eq(BigDecimal.class),
            anyString(), anyString(), anyString())).thenReturn(new BigDecimal("7.2"));
        when(jdbc.queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("snap-" + bizType);

        String id = capture.captureForLedger(TENANT, "USD", bizType, "source", "REF-1");

        assertThat(id).isEqualTo("snap-" + bizType);
        verify(jdbc, times(1)).queryForObject(contains("INSERT INTO fx_rate_snapshots"),
            eq(String.class), eq(TENANT), eq("USD"), eq("CNY"),
            eq(new BigDecimal("7.2")), eq("AUTO_LEDGER"),
            eq(bizType), eq("source"), eq("REF-1"));
    }
}
