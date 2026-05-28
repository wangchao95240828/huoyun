package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.JsonSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S2 单测：
 * - cancel 成功 → orphan 表写 cancel_success=true
 * - cancel 失败 → orphan 表写 cancel_success=false
 * - cancel 抛异常 → orphan 表仍能写入 + cancel_response 含 exception
 * - 写 orphan 表失败时不抛错（best-effort）
 */
class SubmitCompensationServiceTest {
    private static final String TENANT = "tenant-1";

    private JdbcTemplate jdbc;
    private CarrierGatewayRegistry registry;
    private CarrierGateway gateway;
    private SubmitCompensationService service;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        registry = mock(CarrierGatewayRegistry.class);
        gateway = mock(CarrierGateway.class);
        when(registry.forChannel(any(), any())).thenReturn(gateway);
        when(jdbc.queryForObject(contains("set_config"), eq(String.class))).thenReturn("true");
        service = new SubmitCompensationService(jdbc, registry, new JsonSupport(new ObjectMapper()));
    }

    @Test
    void cancelSuccessWritesOrphanRowWithCancelSuccessTrue() {
        when(gateway.cancel(TENANT, "SBXM001")).thenReturn(true);

        service.compensateOrphanTracking(TENANT, "SANDBOX", "SBXM001", "SBX001",
            "EU-AIR-UPS", "ORDER1", "REF1", "insertCarton failed",
            Map.of("orderNo", "ORDER1"), Map.of("provider", "SANDBOX"));

        verify(gateway, times(1)).cancel(TENANT, "SBXM001");
        verify(jdbc, times(1)).update(contains("INSERT INTO acc_orphan_tracking_nos"),
            eq(TENANT), eq("SBXM001"), eq("SBX001"), eq("SANDBOX"),
            eq("EU-AIR-UPS"), eq("ORDER1"), eq("REF1"),
            eq("insertCarton failed"),
            anyString(), anyString(),
            eq(true), anyString());
    }

    @Test
    void cancelReturnsFalseWritesOrphanRowWithCancelSuccessFalse() {
        when(gateway.cancel(TENANT, "SBXM002")).thenReturn(false);

        service.compensateOrphanTracking(TENANT, "SANDBOX", "SBXM002", "SBX002",
            "EU-AIR-UPS", "ORDER2", null, "no shipment id",
            null, null);

        verify(jdbc, times(1)).update(contains("INSERT INTO acc_orphan_tracking_nos"),
            eq(TENANT), eq("SBXM002"), eq("SBX002"), eq("SANDBOX"),
            eq("EU-AIR-UPS"), eq("ORDER2"), isNull(),
            eq("no shipment id"),
            isNull(), isNull(),
            eq(false), anyString());
    }

    @Test
    void cancelThrowsStillRecordsOrphan() {
        when(gateway.cancel(TENANT, "SBXM003"))
            .thenThrow(new RuntimeException("provider connect timeout"));

        service.compensateOrphanTracking(TENANT, "SANDBOX", "SBXM003", "SBX003",
            "EU-AIR-UPS", "ORDER3", "REF3", "exception path",
            Map.of(), Map.of());

        // 即使 cancel 抛错，orphan 表也要写
        verify(jdbc, times(1)).update(contains("INSERT INTO acc_orphan_tracking_nos"),
            eq(TENANT), eq("SBXM003"), eq("SBX003"), eq("SANDBOX"),
            eq("EU-AIR-UPS"), eq("ORDER3"), eq("REF3"),
            eq("exception path"),
            anyString(), anyString(),
            eq(false), contains("provider connect timeout"));
    }

    @Test
    void orphanInsertFailureDoesNotThrow() {
        when(gateway.cancel(any(), any())).thenReturn(true);
        when(jdbc.update(contains("INSERT INTO acc_orphan_tracking_nos"),
            any(Object[].class)))
            .thenThrow(new DataAccessResourceFailureException("db down"));

        // best-effort：不抛
        service.compensateOrphanTracking(TENANT, "SANDBOX", "SBXM004", "SBX004",
            "C", "O", "R", "reason", null, null);

        // cancel 仍被调用过
        verify(gateway, times(1)).cancel(any(), any());
    }

    @Test
    void sandboxCancelRemovesActiveTracking() {
        // SandboxCarrierGateway 自带 ACTIVE 集合 → submit 加入 → cancel 移除
        SandboxCarrierGateway sandbox = new SandboxCarrierGateway();
        CarrierGateway.SubmitContext ctx = new CarrierGateway.SubmitContext(
            TENANT, "CUST", "ORD-SBX", "REF", "EU-AIR-UPS", "US",
            new java.math.BigDecimal("1"), 1, Map.of());
        CarrierGateway.Issuance iss = sandbox.submit(ctx);
        String master = iss.carrierMasterTrackingNo();
        // 第一次 cancel：返回 true（成功移除）
        assertThat(sandbox.cancel(TENANT, master)).isTrue();
        // 第二次 cancel：已不在集合，返回 false
        assertThat(sandbox.cancel(TENANT, master)).isFalse();
    }
}
