package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CarrierGateway.Issuance;
import com.xqt.saas.customerapi.CarrierGateway.SubmitContext;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 渠道取号路由：acc_channel_accounts.provider_code 数据驱动 + strict 禁兜底 + sandbox evidence。
 */
class CarrierGatewayRoutingTest {

    private CarrierGatewayRegistry registry(JdbcTemplate jdbc) {
        NoopCarrierGateway noop = new NoopCarrierGateway();
        SandboxCarrierGateway sandbox = new SandboxCarrierGateway();
        return new CarrierGatewayRegistry(List.of(noop, sandbox), noop, jdbc);
    }

    // 1. acc_channel_accounts.provider_code=SANDBOX → 路由到 SandboxCarrierGateway
    @Test
    void routesByChannelAccountProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenReturn("SANDBOX");

        CarrierGateway gw = registry(jdbc).forChannel("tenant-1", "EU-AIR-UPS");

        assertThat(gw.gatewayKey()).isEqualTo("SANDBOX");
    }

    // 2. 非 strict + 无配置 → Noop 兜底
    @Test
    void fallsBackToNoopWhenNotStrict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(contains("last_mile_method"), eq(String.class), any(), any()))
            .thenThrow(new EmptyResultDataAccessException(1));

        CarrierGateway gw = registry(jdbc).forChannel("tenant-1", "UNKNOWN");

        assertThat(gw.gatewayKey()).isEqualTo("NOOP");
    }

    // 3. strict + 无配置 → 抛错（生产禁兜底）
    @Test
    void strictModeRejectsUnconfiguredChannel() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenThrow(new EmptyResultDataAccessException(1));
        CarrierGatewayRegistry reg = registry(jdbc);
        ReflectionTestUtils.setField(reg, "strictGateway", true);

        assertThatThrownBy(() -> reg.forChannel("tenant-1", "EU-AIR-UPS"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未配置可用取号接口");
    }

    // 4. strict + 已配置 SANDBOX → 正常路由（不抛）
    @Test
    void strictModeAllowsConfiguredProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenReturn("SANDBOX");
        CarrierGatewayRegistry reg = registry(jdbc);
        ReflectionTestUtils.setField(reg, "strictGateway", true);

        assertThat(reg.forChannel("tenant-1", "EU-AIR-UPS").gatewayKey()).isEqualTo("SANDBOX");
    }

    // 5. SandboxCarrierGateway.submit → evidence 含 request + response
    @Test
    void sandboxSubmitStoresRequestResponseEvidence() {
        SandboxCarrierGateway sandbox = new SandboxCarrierGateway();
        Issuance iss = sandbox.submit(new SubmitContext(
            "tenant-1", "CUST001", "DOC-1", "ORD-1", "EU-AIR-UPS", "US",
            new BigDecimal("2.5"), 1, Map.of("name", "John")));

        assertThat(iss.carrierTrackingNo()).startsWith("SBX");
        assertThat(iss.raw()).containsKey("request");
        assertThat(iss.raw()).containsKey("response");
        assertThat(iss.raw().get("provider")).isEqualTo("SANDBOX");
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) iss.raw().get("response");
        assertThat(resp.get("status")).isEqualTo("ACCEPTED");
        assertThat(resp.get("trackingNumber")).isEqualTo(iss.carrierTrackingNo());
    }
}
