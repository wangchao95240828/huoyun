package com.xqt.saas.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.labels.LabelGateway.LabelArtifact;
import com.xqt.saas.labels.LabelGateway.PrintContext;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 面单 provider 路由：acc_channel_accounts.provider_code 数据驱动 + strict 禁 Noop + sandbox evidence。
 */
class LabelGatewayRoutingTest {

    private LabelGatewayRegistry registry(JdbcTemplate jdbc) {
        NoopLabelGateway noop = new NoopLabelGateway();
        SandboxLabelGateway sandbox = new SandboxLabelGateway();
        return new LabelGatewayRegistry(List.of(noop, sandbox), noop, jdbc);
    }

    @Test
    void routesByProviderCode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenReturn("SANDBOX");

        LabelGateway gw = registry(jdbc).forChannel("tenant-1", "EU-AIR-UPS");

        assertThat(gw).isInstanceOf(SandboxLabelGateway.class);
    }

    @Test
    void fallsBackToNoopWhenNotStrict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenThrow(new EmptyResultDataAccessException(1));

        LabelGateway gw = registry(jdbc).forChannel("tenant-1", "UNKNOWN");

        assertThat(gw).isInstanceOf(NoopLabelGateway.class);
    }

    @Test
    void strictRejectsUnconfigured() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("acc_channel_accounts"), eq(String.class), any(), any()))
            .thenThrow(new EmptyResultDataAccessException(1));
        LabelGatewayRegistry reg = registry(jdbc);
        ReflectionTestUtils.setField(reg, "strictGateway", true);

        assertThatThrownBy(() -> reg.forChannel("tenant-1", "UNKNOWN"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未配置可用面单接口");
    }

    @Test
    void sandboxPrintStoresEvidence() {
        SandboxLabelGateway sandbox = new SandboxLabelGateway();
        LabelArtifact art = sandbox.print(new PrintContext(
            "tenant-1", "CUST001", "ship-1", "SHP-1", "ORD-1",
            "EU-AIR-UPS", "US", "PDF", Map.of()));

        assertThat(art.mainTrackingNo()).startsWith("SBX-LBL-");
        assertThat(art.labelType()).isEqualTo("PDF");
        assertThat(art.subTrackingNos()).hasSize(1);
        assertThat(art.raw()).containsKey("request");
        assertThat(art.raw()).containsKey("response");
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) art.raw().get("response");
        assertThat(resp.get("status")).isEqualTo("OK");
        assertThat(resp.get("mainTrackingNumber")).isEqualTo(art.mainTrackingNo());
    }

    @Test
    void sandboxZplOnRequest() {
        SandboxLabelGateway sandbox = new SandboxLabelGateway();
        LabelArtifact art = sandbox.print(new PrintContext(
            "tenant-1", "CUST001", "ship-1", "SHP-1", "ORD-1",
            "EU-AIR-UPS", "US", "ZPL", Map.of()));

        assertThat(art.labelType()).isEqualTo("ZPL");
        assertThat(new String(art.content())).startsWith("^XA");
    }
}
