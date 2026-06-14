package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccPeriodClosingControllerTest {

    @Test
    void preview_invalidPeriodFormat_throws() {
        AccPeriodClosingController c = new AccPeriodClosingController(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> c.preview(Map.of("period", "2025-13")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("YYYY-MM 格式");
        assertThatThrownBy(() -> c.preview(Map.of("period", "2025")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("YYYY-MM 格式");
        assertThatThrownBy(() -> c.preview(Map.of("period", "")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void status_invalidPeriod_throws() {
        AccPeriodClosingController c = new AccPeriodClosingController(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> c.status("invalid"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("YYYY-MM");
    }
}
