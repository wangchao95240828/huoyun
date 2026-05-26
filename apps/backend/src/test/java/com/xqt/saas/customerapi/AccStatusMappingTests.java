package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccStatusMappingTests {

    @Test
    void mapsKnownStatusesToStableCodes() {
        assertThat(AccStatusMapping.toAccCode("DRAFT")).isZero();
        assertThat(AccStatusMapping.toAccCode("SUBMITTED")).isEqualTo(1);
        assertThat(AccStatusMapping.toAccCode("ORDERED")).isEqualTo(1);
        assertThat(AccStatusMapping.toAccCode("ACCEPTED")).isEqualTo(2);
        assertThat(AccStatusMapping.toAccCode("IN_WAREHOUSE")).isEqualTo(2);
        assertThat(AccStatusMapping.toAccCode("DELIVERED")).isEqualTo(7);
        assertThat(AccStatusMapping.toAccCode("CANCELLED")).isEqualTo(8);
    }

    @Test
    void unknownAndNullReturnNotFound() {
        assertThat(AccStatusMapping.toAccCode(null)).isEqualTo(AccStatusMapping.NOT_FOUND);
        assertThat(AccStatusMapping.toAccCode("SOMETHING_ELSE")).isEqualTo(AccStatusMapping.NOT_FOUND);
    }

    @Test
    void exceptionMapsToNegativeTwo() {
        assertThat(AccStatusMapping.toAccCode("EXCEPTION")).isEqualTo(-2);
    }
}
