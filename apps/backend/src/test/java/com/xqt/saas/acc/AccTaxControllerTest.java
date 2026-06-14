package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccTaxControllerTest {

    @Test
    void calc_taxExclusive_addsTax() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(BigDecimal.class), any(Object[].class)))
            .thenReturn(new BigDecimal("0.13"));
        AccTaxController c = new AccTaxController(jdbc);
        Map<String, Object> r = c.calc(Map.of("amount", 100, "taxCode", "CN_VAT_13"));
        assertThat((BigDecimal) r.get("taxableAmount")).isEqualByComparingTo("100");
        assertThat((BigDecimal) r.get("taxAmount")).isEqualByComparingTo("13.00");
        assertThat((BigDecimal) r.get("totalAmount")).isEqualByComparingTo("113.00");
    }

    @Test
    void calc_taxInclusive_extractsTax() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(BigDecimal.class), any(Object[].class)))
            .thenReturn(new BigDecimal("0.13"));
        AccTaxController c = new AccTaxController(jdbc);
        Map<String, Object> r = c.calc(Map.of("amount", 113, "taxCode", "CN_VAT_13", "includesTax", "true"));
        assertThat((BigDecimal) r.get("taxableAmount")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) r.get("taxAmount")).isEqualByComparingTo("13.00");
        assertThat((BigDecimal) r.get("totalAmount")).isEqualByComparingTo("113");
    }

    @Test
    void calc_missingAmount_throws() {
        AccTaxController c = new AccTaxController(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> c.calc(Map.of("taxCode", "VAT_13")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("amount 必填");
    }

    @Test
    void calc_invalidTaxCode_throws() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(BigDecimal.class), any(Object[].class)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        AccTaxController c = new AccTaxController(jdbc);
        assertThatThrownBy(() -> c.calc(Map.of("amount", 100, "taxCode", "BAD_CODE")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("找不到税率");
    }
}
