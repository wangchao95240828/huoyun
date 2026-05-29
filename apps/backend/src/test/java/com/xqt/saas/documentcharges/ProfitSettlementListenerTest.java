package com.xqt.saas.documentcharges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.xqt.saas.stowage.ShipmentDeliveredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ProfitSettlementListener 单测：
 *  1. ShipmentDeliveredEvent → 写 profit_snapshots（AR-AP-commission-sellerCost = gross）
 *  2. 已结算过的 shipment 不重复写（幂等）
 *  3. DB 异常静默，不抛
 *  4. commission 从 evidence JSON 中聚合
 */
class ProfitSettlementListenerTest {
    private static final String TENANT = "tenant-1";
    private static final String SHIP_ID = "00000000-0000-0000-0000-000000000001";

    private JdbcTemplate jdbc;
    private ProfitSettlementListener listener;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        listener = new ProfitSettlementListener(jdbc);
    }

    private ShipmentDeliveredEvent event() {
        return new ShipmentDeliveredEvent(TENANT, SHIP_ID, "SHIP-001",
            OffsetDateTime.parse("2026-05-29T08:00:00+08:00"));
    }

    private Map<String, Object> sumsRow(BigDecimal ar, BigDecimal ap, BigDecimal commission) {
        Map<String, Object> m = new HashMap<>();
        m.put("ar_amount", ar);
        m.put("ap_amount", ap);
        m.put("commission_amount", commission);
        m.put("seller_cost", BigDecimal.ZERO);
        m.put("currency", "CNY");
        return m;
    }

    @Test
    void deliveredShipmentWritesProfitSnapshot() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), anyString(), anyString()))
            .thenReturn(0);
        when(jdbc.queryForMap(contains("ar_amount"), anyString(), anyString()))
            .thenReturn(sumsRow(new BigDecimal("500.00"), new BigDecimal("300.00"), new BigDecimal("20.00")));

        listener.onShipmentDelivered(event());

        // gross = 500 - 300 - 20 - 0 = 180
        verify(jdbc, times(1)).update(contains("INSERT INTO profit_snapshots"),
            eq(TENANT), eq(SHIP_ID),
            eq(new BigDecimal("500.00")), eq(new BigDecimal("300.00")),
            eq(BigDecimal.ZERO), eq(new BigDecimal("20.00")),
            eq(new BigDecimal("180.00")), eq("CNY"),
            eq("SHIP-001"), anyString());
    }

    @Test
    void existingSnapshotIsIdempotent() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), anyString(), anyString()))
            .thenReturn(1);

        listener.onShipmentDelivered(event());

        verify(jdbc, never()).queryForMap(anyString(), any(Object[].class));
        verify(jdbc, never()).update(contains("INSERT INTO profit_snapshots"), any(Object[].class));
    }

    @Test
    void dbExceptionIsSwallowed() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), anyString(), anyString()))
            .thenThrow(new DataAccessResourceFailureException("db down"));

        // 不抛
        listener.onShipmentDelivered(event());
    }

    @Test
    void negativeProfitWrittenWhenApExceedsAr() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), anyString(), anyString()))
            .thenReturn(0);
        when(jdbc.queryForMap(contains("ar_amount"), anyString(), anyString()))
            .thenReturn(sumsRow(new BigDecimal("100.00"), new BigDecimal("150.00"), BigDecimal.ZERO));

        listener.onShipmentDelivered(event());

        // gross = 100 - 150 - 0 - 0 = -50 → 亏损也要落账
        ArgumentCaptor<BigDecimal> grossCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(jdbc, times(1)).update(contains("INSERT INTO profit_snapshots"),
            eq(TENANT), eq(SHIP_ID),
            any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), any(BigDecimal.class),
            grossCap.capture(), eq("CNY"),
            eq("SHIP-001"), anyString());
        assertThat(grossCap.getValue()).isEqualByComparingTo("-50.00");
    }

    @Test
    void numberCoercionWorksForDifferentTypes() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), anyString(), anyString()))
            .thenReturn(0);
        // PG 有时返回 Long/Double 而非 BigDecimal
        Map<String, Object> mixed = new HashMap<>();
        mixed.put("ar_amount", 500L);          // Long
        mixed.put("ap_amount", 300.0);          // Double
        mixed.put("commission_amount", null);  // null
        mixed.put("seller_cost", BigDecimal.ZERO);
        mixed.put("currency", "USD");
        when(jdbc.queryForMap(contains("ar_amount"), anyString(), anyString())).thenReturn(mixed);

        listener.onShipmentDelivered(event());

        verify(jdbc, times(1)).update(contains("INSERT INTO profit_snapshots"),
            eq(TENANT), eq(SHIP_ID),
            any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), eq("USD"),
            eq("SHIP-001"), anyString());
    }
}
