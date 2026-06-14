package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccSmsReconcileServiceTest {

    @Test
    void parseRaw_extractsAmountAndPayer_zhBankSms() {
        AccSmsReconcileService svc = new AccSmsReconcileService(mock(JdbcTemplate.class));
        Map<String, Object> r = svc.parseRaw("您尾号1234的账户于2026-06-14 转入5,000.00元，付款方：DOC-DEMO 客户");
        assertThat((Boolean) r.get("ok")).isTrue();
        assertThat((BigDecimal) r.get("parsedAmount"))
            .isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat((String) r.get("parsedCurrency")).isEqualTo("CNY");
        assertThat((String) r.get("parsedPayer")).contains("DOC-DEMO");
    }

    @Test
    void parseRaw_usdCurrency() {
        AccSmsReconcileService svc = new AccSmsReconcileService(mock(JdbcTemplate.class));
        Map<String, Object> r = svc.parseRaw("USD 1234.56 received from John Doe");
        assertThat((Boolean) r.get("ok")).isTrue();
        assertThat((BigDecimal) r.get("parsedAmount"))
            .isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat((String) r.get("parsedCurrency")).isEqualTo("USD");
    }

    @Test
    void parseRaw_emptyContent_returnsError() {
        AccSmsReconcileService svc = new AccSmsReconcileService(mock(JdbcTemplate.class));
        Map<String, Object> r = svc.parseRaw("");
        assertThat((Boolean) r.get("ok")).isFalse();
        assertThat((String) r.get("error")).contains("空");
    }

    @Test
    void parseRaw_eurCurrency() {
        AccSmsReconcileService svc = new AccSmsReconcileService(mock(JdbcTemplate.class));
        Map<String, Object> r = svc.parseRaw("EUR 999.00 received");
        assertThat((Boolean) r.get("ok")).isTrue();
        assertThat((String) r.get("parsedCurrency")).isEqualTo("EUR");
    }
}
