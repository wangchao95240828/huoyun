package com.xqt.saas.framework.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MultiStageApprovalServiceTest {

    @Test
    void requiredStages_byAmount() {
        MultiStageApprovalService svc = new MultiStageApprovalService(mock(JdbcTemplate.class));
        assertThat(svc.requiredStages(new BigDecimal("100"))).isEqualTo(1);
        assertThat(svc.requiredStages(new BigDecimal("49999.99"))).isEqualTo(1);
        assertThat(svc.requiredStages(new BigDecimal("50000"))).isEqualTo(2);
        assertThat(svc.requiredStages(new BigDecimal("499999"))).isEqualTo(2);
        assertThat(svc.requiredStages(new BigDecimal("500000"))).isEqualTo(3);
        assertThat(svc.requiredStages(new BigDecimal("1000000"))).isEqualTo(3);
        assertThat(svc.requiredStages(null)).isEqualTo(1);
    }

    @Test
    void decide_invalidDecision_throws() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MultiStageApprovalService svc = new MultiStageApprovalService(jdbc);
        assertThatThrownBy(() -> svc.decide("id", "MAYBE", "comment", "tenant", "user"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("APPROVE 或 REJECT");
    }

    @Test
    void decide_alreadyResolved_throws() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(any(String.class), any(Object[].class)))
            .thenReturn(Map.of("status", "APPROVED", "required_count", 2,
                "requested_by", "other-user", "resource", "x", "action", "y"));
        MultiStageApprovalService svc = new MultiStageApprovalService(jdbc);
        assertThatThrownBy(() -> svc.decide("id", "APPROVE", "ok", "tenant", "user"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已 APPROVED");
    }

    @Test
    void decide_selfApprove_throws() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(any(String.class), any(Object[].class)))
            .thenReturn(Map.of("status", "PENDING", "required_count", 2,
                "requested_by", "user-1", "resource", "x", "action", "y"));
        MultiStageApprovalService svc = new MultiStageApprovalService(jdbc);
        assertThatThrownBy(() -> svc.decide("id", "APPROVE", "ok", "tenant", "user-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能审批自己");
    }
}
