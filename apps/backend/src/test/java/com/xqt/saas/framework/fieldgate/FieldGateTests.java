package com.xqt.saas.framework.fieldgate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FieldGateTests {

    @Test
    void pendingStatusAllowsAllFields() {
        FieldGate gate = init();
        FieldGate.FilterResult result = gate.filterAllowedFields("charges", "PENDING",
            Map.of("amount", 100, "currency", "USD"));
        assertThat(result.allowed()).containsKeys("amount", "currency");
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void auditedStatusRejectsAmountForCharges() {
        FieldGate gate = init();
        FieldGate.FilterResult result = gate.filterAllowedFields("charges", "AUDITED",
            Map.of("amount", 200, "status", "LOCKED", "remark", "ok"));
        assertThat(result.allowed()).containsKeys("status", "remark");
        assertThat(result.rejected()).contains("amount");
    }

    @Test
    void auditedCustomersAllowsNameAndCreditNotCode() {
        FieldGate gate = init();
        FieldGate.FilterResult result = gate.filterAllowedFields("customers", "AUDITED",
            Map.of("code", "NEW-CODE", "name", "new name", "credit_limit", 50000));
        assertThat(result.allowed()).containsKeys("name", "credit_limit");
        assertThat(result.rejected()).contains("code");
    }

    @Test
    void unregisteredEntityAuditedRejectsEverything() {
        FieldGate gate = init();
        FieldGate.FilterResult result = gate.filterAllowedFields("not_registered", "AUDITED",
            Map.of("name", "x"));
        assertThat(result.allowed()).isEmpty();
        assertThat(result.rejected()).contains("name");
    }

    private FieldGate init() {
        FieldGate gate = new FieldGate();
        gate.init();
        return gate;
    }
}
