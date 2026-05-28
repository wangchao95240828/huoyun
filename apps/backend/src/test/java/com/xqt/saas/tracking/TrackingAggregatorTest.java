package com.xqt.saas.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S1 单测：3 源混合 + 排序 + visibility 过滤。
 */
class TrackingAggregatorTest {
    private static final String TENANT = "tenant-1";
    private static final String SHIPMENT = "ship-1";

    private JdbcTemplate jdbc;
    private TrackingAggregator aggregator;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        aggregator = new TrackingAggregator(jdbc);
    }

    private Map<String, Object> teRow(String id, Instant t, String status, String location, String rawStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("event_time", Timestamp.from(t));
        m.put("raw_status", rawStatus);
        m.put("status", status);
        m.put("location", location);
        m.put("src", "CARRIER_API");
        m.put("payload", "{}");
        return m;
    }

    private Map<String, Object> stowRow(String id, Instant t, String status, String name, String operator, String remark) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("created_at", Timestamp.from(t));
        m.put("name", name);
        m.put("status", status);
        m.put("location", "深圳仓");
        m.put("remark", remark);
        m.put("audit_name", operator);
        return m;
    }

    private Map<String, Object> dispRow(String id, Instant t, String status, String addr, String contact, String remark) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("created_at", Timestamp.from(t));
        m.put("status", status);
        m.put("pick_address", addr);
        m.put("contact_name", contact);
        m.put("remark", remark);
        return m;
    }

    // 1. 3 源事件按时间排序
    @Test
    void aggregateMergesAndSortsByTime() {
        Instant t1 = Instant.parse("2026-05-28T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-28T12:00:00Z");
        Instant t3 = Instant.parse("2026-05-28T14:00:00Z");

        when(jdbc.queryForList(contains("FROM tracking_events"), eq(TENANT), eq(SHIPMENT)))
            .thenReturn(List.of(teRow("te-1", t2, "DELIVERED", "纽约", "Delivered")));
        when(jdbc.queryForList(contains("FROM acc_stowage_steps"), eq(TENANT), eq(SHIPMENT)))
            .thenReturn(List.of(stowRow("st-1", t1, "DONE", "出库", "wms_admin", "出库无异常")));
        when(jdbc.queryForList(contains("FROM acc_dispatches"), eq(TENANT), eq(SHIPMENT)))
            .thenReturn(List.of(dispRow("dp-1", t3, "DONE", "深圳南山", "李揽收员", "内部备注：客户偏好下午取件")));

        List<TrackingEvent> events = aggregator.aggregateByShipment(TENANT, SHIPMENT);

        assertThat(events).hasSize(3);
        // 按时间排序：t1(stowage) → t2(tracking) → t3(dispatch)
        assertThat(events.get(0).source()).isEqualTo("stowage_steps");
        assertThat(events.get(1).source()).isEqualTo("tracking_events");
        assertThat(events.get(2).source()).isEqualTo("dispatches");
    }

    // 2. visibility = INTERNAL 时含 operator + internalRemark
    @Test
    void internalViewIncludesOperatorAndRemark() {
        Instant t = Instant.now();
        when(jdbc.queryForList(contains("FROM tracking_events"), anyString(), anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM acc_stowage_steps"), anyString(), anyString())).thenReturn(
            List.of(stowRow("st-1", t, "DONE", "扫描", "alice", "客户偏好备注")));
        when(jdbc.queryForList(contains("FROM acc_dispatches"), anyString(), anyString())).thenReturn(List.of());

        List<TrackingEvent> events = aggregator.aggregateByShipment(TENANT, SHIPMENT);

        assertThat(events).hasSize(1);
        TrackingEvent e = events.get(0);
        assertThat(e.visibility()).isEqualTo(TrackingEvent.Visibility.INTERNAL);
        assertThat(e.operator()).isEqualTo("alice");
        assertThat(e.internalRemark()).isEqualTo("客户偏好备注");
    }

    // 3. toPublic 剥 operator / internalRemark
    @Test
    void publicViewStripsOperatorAndRemark() {
        Instant t = Instant.now();
        when(jdbc.queryForList(contains("FROM tracking_events"), anyString(), anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM acc_stowage_steps"), anyString(), anyString())).thenReturn(
            List.of(stowRow("st-1", t, "DONE", "扫描", "alice", "客户偏好备注")));
        when(jdbc.queryForList(contains("FROM acc_dispatches"), anyString(), anyString())).thenReturn(List.of());

        List<TrackingEvent> internal = aggregator.aggregateByShipment(TENANT, SHIPMENT);
        List<TrackingEvent> publicEvents = aggregator.toPublic(internal);

        assertThat(publicEvents.get(0).operator()).isNull();
        assertThat(publicEvents.get(0).internalRemark()).isNull();
        assertThat(publicEvents.get(0).visibility()).isEqualTo(TrackingEvent.Visibility.PUBLIC);
        // 公开字段保留
        assertThat(publicEvents.get(0).message()).isEqualTo("配载: 扫描");
        assertThat(publicEvents.get(0).location()).isEqualTo("深圳仓");
    }

    // 4. 按 tracking_no 查询：resolve shipmentId 后扩到其它源
    @Test
    void aggregateByTrackingNoResolvesShipment() {
        Instant t = Instant.now();
        when(jdbc.queryForObject(contains("FROM cartons"), eq(String.class),
            eq(TENANT), eq("1Z999"))).thenReturn(SHIPMENT);
        when(jdbc.queryForList(contains("FROM tracking_events"), eq(TENANT), eq("1Z999")))
            .thenReturn(List.of(teRow("te-1", t, "DELIVERED", "NY", "Delivered")));
        when(jdbc.queryForList(contains("FROM acc_stowage_steps"), eq(TENANT), eq(SHIPMENT)))
            .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM acc_dispatches"), eq(TENANT), eq(SHIPMENT)))
            .thenReturn(List.of());

        List<TrackingEvent> events = aggregator.aggregateByTrackingNo(TENANT, "1Z999");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).isEqualTo("tracking_events");
    }

    // 5. 当 tracking_no 找不到 shipment 时，不抛错，仅返回 tracking_events 命中
    @Test
    void aggregateByTrackingNoNoMatchingShipment() {
        Instant t = Instant.now();
        when(jdbc.queryForObject(contains("FROM cartons"), eq(String.class), anyString(), anyString()))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        when(jdbc.queryForList(contains("FROM tracking_events"), eq(TENANT), eq("UNKNOWN")))
            .thenReturn(List.of(teRow("te-1", t, "PICKED_UP", "深圳", "Picked")));

        List<TrackingEvent> events = aggregator.aggregateByTrackingNo(TENANT, "UNKNOWN");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).statusCode()).isEqualTo("PICKED_UP");
    }
}
