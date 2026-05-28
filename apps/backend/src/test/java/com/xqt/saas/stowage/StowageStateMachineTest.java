package com.xqt.saas.stowage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S5 单测：
 *  1. stowage CONFIRMED → cartons.stowage_id 反查 shipments，写 tracking_events + 推进 shipments.status
 *  2. dispatch DONE → customer + ±3 天匹配 shipments，写 DELIVERED + publish ShipmentDeliveredEvent
 *  3. dispatch PICKED（OUT_FOR_DELIVERY 业务态）→ 写 tracking_events，不改 shipments.status
 *  4. transit IN_TRANSIT 当前无 link 表 → no-op 返回 0
 *  5. DB 异常时方法不抛错（业务连续性优先）
 */
class StowageStateMachineTest {
    private static final String TENANT = "tenant-1";

    private JdbcTemplate jdbc;
    private ApplicationEventPublisher events;
    private StowageStateMachine sm;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        events = mock(ApplicationEventPublisher.class);
        sm = new StowageStateMachine(jdbc, events);
    }

    private static Map<String, Object> shipRow(String id, String no) {
        Map<String, Object> m = new HashMap<>();
        m.put("shipment_id", id);
        m.put("shipment_no", no);
        m.put("tenant_id", TENANT);
        return m;
    }

    @Test
    void stowageConfirmedFanoutsTrackingEventsAndAdvancesShipments() {
        when(jdbc.queryForList(contains("c.stowage_id"), anyString()))
            .thenReturn(List.of(
                shipRow("ship-1", "SHIP-001"),
                shipRow("ship-2", "SHIP-002")));

        int n = sm.onStowageConfirmed(TENANT, "stowage-1");

        assertThat(n).isEqualTo(2);
        // 2 条 tracking_events INSERT
        verify(jdbc, times(2)).update(contains("INSERT INTO tracking_events"),
            eq(TENANT), anyString(), anyString(), eq("STOWAGE_CONFIRMED"),
            eq("IN_TRANSIT"), any());
        // 2 条 shipments status UPDATE
        verify(jdbc, times(2)).update(contains("UPDATE shipments SET status"),
            eq("IN_TRANSIT"), anyString());
    }

    @Test
    void dispatchDeliveredWritesDeliveredEventAndPublishesAppEvent() {
        when(jdbc.queryForList(contains("acc_dispatches"), anyString()))
            .thenReturn(List.of(shipRow("ship-9", "SHIP-009")));

        int n = sm.onDispatchDelivered(TENANT, "dispatch-1");

        assertThat(n).isEqualTo(1);
        verify(jdbc, times(1)).update(contains("INSERT INTO tracking_events"),
            eq(TENANT), eq("ship-9"), eq("SHIP-009"), eq("DELIVERED"),
            eq("DELIVERED"), any());
        verify(jdbc, times(1)).update(contains("UPDATE shipments SET status"),
            eq("DELIVERED"), eq("ship-9"));
        ArgumentCaptor<ShipmentDeliveredEvent> cap = ArgumentCaptor.forClass(ShipmentDeliveredEvent.class);
        verify(events, times(1)).publishEvent(cap.capture());
        assertThat(cap.getValue().shipmentId()).isEqualTo("ship-9");
        assertThat(cap.getValue().shipmentNo()).isEqualTo("SHIP-009");
        assertThat(cap.getValue().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void dispatchOutForDeliveryWritesEventButDoesNotChangeShipmentStatus() {
        when(jdbc.queryForList(contains("acc_dispatches"), anyString()))
            .thenReturn(List.of(shipRow("ship-3", "SHIP-003")));

        int n = sm.onDispatchOutForDelivery(TENANT, "dispatch-3");

        assertThat(n).isEqualTo(1);
        verify(jdbc, times(1)).update(contains("INSERT INTO tracking_events"),
            eq(TENANT), eq("ship-3"), eq("SHIP-003"), eq("DISPATCH_OUT"),
            eq("OUT_FOR_DELIVERY"), any());
        // 出库阶段不改 shipments.status
        verify(jdbc, never()).update(contains("UPDATE shipments SET status"),
            any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void transitInTransitIsNoOp() {
        int n = sm.onTransitInTransit(TENANT, "transit-1");
        assertThat(n).isZero();
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any());
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void databaseFailureDoesNotPropagateException() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenThrow(new DataAccessResourceFailureException("db down"));
        int n = sm.onStowageConfirmed(TENANT, "stowage-x");
        assertThat(n).isZero();
        // 不抛 → 不应有 tracking_events 写入
        verify(jdbc, never()).update(contains("INSERT INTO tracking_events"),
            any(), any(), any(), any(), any(), any());
    }

    @Test
    void shipmentDeliveredEventCarriesTenantAndShipment() {
        when(jdbc.queryForList(contains("acc_dispatches"), anyString()))
            .thenReturn(List.of(shipRow("ship-multi-1", "SH-1"), shipRow("ship-multi-2", "SH-2")));

        sm.onDispatchDelivered(TENANT, "d-multi");

        ArgumentCaptor<ShipmentDeliveredEvent> cap = ArgumentCaptor.forClass(ShipmentDeliveredEvent.class);
        verify(events, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).hasSize(2);
        assertThat(cap.getAllValues()).extracting(ShipmentDeliveredEvent::shipmentId)
            .containsExactlyInAnyOrder("ship-multi-1", "ship-multi-2");
    }
}
