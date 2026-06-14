package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChargeAuditGlSideEffectTest {

    @Test
    void supports_onlyCharges() {
        ChargeAuditGlSideEffect se = new ChargeAuditGlSideEffect(mock(JdbcTemplate.class));
        assertThat(se.supports("charges")).isTrue();
        assertThat(se.supports("orders")).isFalse();
        assertThat(se.supports("customer_invoices")).isFalse();
    }

    @Test
    void onAudited_skipIfPeriodLocked() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // 第一次 queryForMap: 返回 AR charge
        when(jdbc.queryForMap(any(String.class), any(Object[].class)))
            .thenReturn(Map.of("side", "AR", "amount", new BigDecimal("100"),
                "currency", "USD", "order_id", "order-1"));
        // period 锁定
        when(jdbc.queryForObject(any(String.class), eq(Boolean.class)))
            .thenReturn(true);

        ChargeAuditGlSideEffect se = new ChargeAuditGlSideEffect(jdbc);
        se.onAudited("charges", "00000000-0000-0000-0000-000000000001", "tenant", "user");

        // 不应该写凭证（period 锁了）
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void onAudited_skipIfNotApplicableSide() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(any(String.class), any(Object[].class)))
            .thenReturn(Map.of("side", "WEIRD_SIDE", "amount", new BigDecimal("100"),
                "currency", "USD"));
        when(jdbc.queryForObject(any(String.class), eq(Boolean.class))).thenReturn(false);

        ChargeAuditGlSideEffect se = new ChargeAuditGlSideEffect(jdbc);
        se.onAudited("charges", "00000000-0000-0000-0000-000000000001", "tenant", "user");

        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void onAudited_skipIfZeroAmount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(any(String.class), any(Object[].class)))
            .thenReturn(Map.of("side", "AR", "amount", BigDecimal.ZERO,
                "currency", "USD", "order_id", "order-1"));

        ChargeAuditGlSideEffect se = new ChargeAuditGlSideEffect(jdbc);
        se.onAudited("charges", "00000000-0000-0000-0000-000000000001", "tenant", "user");
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }
}
