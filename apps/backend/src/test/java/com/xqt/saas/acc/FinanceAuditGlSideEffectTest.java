package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;

class FinanceAuditGlSideEffectTest {

    @Test
    void supports_onlyInvoiceAndPayment() {
        FinanceAuditGlSideEffect se = new FinanceAuditGlSideEffect(mock(JdbcTemplate.class));
        assertThat(se.supports("customer_invoices")).isTrue();
        assertThat(se.supports("partner_payments")).isTrue();
        assertThat(se.supports("charges")).isFalse();
        assertThat(se.supports("orders")).isFalse();
        assertThat(se.supports("acc_fines")).isFalse();
    }
}
