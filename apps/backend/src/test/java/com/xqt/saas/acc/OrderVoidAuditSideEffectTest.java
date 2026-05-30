package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 订单作废审核 SideEffect 单测：对照 ACC 「作废订单」 审核流程。
 */
class OrderVoidAuditSideEffectTest {
    private static final String TENANT = "tenant-1";
    private static final String ORDER_ID = "00000000-0000-0000-0000-000000000001";

    private JdbcTemplate jdbc;
    private OrderVoidAuditSideEffect effect;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        effect = new OrderVoidAuditSideEffect(jdbc);
    }

    @Test
    void supportsOnlyOrdersTable() {
        assertThat(effect.supports("orders")).isTrue();
        assertThat(effect.supports("charges")).isFalse();
        assertThat(effect.supports("acc_finance_txns")).isFalse();
    }

    @Test
    void onAuditedSavesPrevStatusAndSetsVoid() {
        effect.onAudited("orders", ORDER_ID, TENANT, "admin");

        // 1) 保存 status_before_void 到 metadata
        verify(jdbc, times(1)).update(contains("status_before_void"),
            eq(ORDER_ID), eq(TENANT));
        // 2) 改 status='CANCELLED'
        verify(jdbc, times(1)).update(contains("status = 'CANCELLED'"),
            eq(ORDER_ID), eq(TENANT));
    }

    @Test
    void onUndoneRestoresStatusFromMetadata() {
        when(jdbc.queryForObject(contains("status_before_void"), eq(String.class),
            anyString(), anyString())).thenReturn("ACCEPTED");

        effect.onUndone("orders", ORDER_ID, TENANT, "admin");

        verify(jdbc, times(1)).update(contains("UPDATE orders SET status"),
            eq("ACCEPTED"), eq(ORDER_ID), eq(TENANT));
    }

    @Test
    void onUndoneFallsBackToDraftWhenNoMetadata() {
        when(jdbc.queryForObject(contains("status_before_void"), eq(String.class),
            anyString(), anyString())).thenReturn(null);

        effect.onUndone("orders", ORDER_ID, TENANT, "admin");

        verify(jdbc, times(1)).update(contains("UPDATE orders SET status"),
            eq("DRAFT"), eq(ORDER_ID), eq(TENANT));
    }

    @Test
    void dbFailureIsSwallowed() {
        when(jdbc.update(contains("status_before_void"), anyString(), anyString()))
            .thenThrow(new DataAccessResourceFailureException("db down"));

        // 不抛
        effect.onAudited("orders", ORDER_ID, TENANT, "admin");
        // 第二步 UPDATE 不会执行（exception 阻断）
        verify(jdbc, never()).update(contains("status = 'CANCELLED'"),
            anyString(), anyString());
    }
}
