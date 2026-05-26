package com.xqt.saas.publictracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.xqt.saas.publictracking.PublicTrackingResponses.TrackEvent;
import com.xqt.saas.publictracking.PublicTrackingResponses.TrackingResult;
import org.junit.jupiter.api.Test;

class PublicTrackingServiceTests {

    @Test
    void notFoundMessageMatchesAccLegacy() {
        // ACC: alert('找不到该快件！')
        PublicTrackingRepository repo = mock(PublicTrackingRepository.class);
        when(repo.findShipmentByTrackingOrNo("UNKNOWN")).thenReturn(null);

        TrackingResult result = new PublicTrackingService(repo).query("UNKNOWN");

        assertThat(result.done()).isFalse();
        assertThat(result.message()).isEqualTo("找不到该快件！");
    }

    @Test
    void emptyTrackNoReturnsValidationError() {
        PublicTrackingRepository repo = mock(PublicTrackingRepository.class);
        TrackingResult result = new PublicTrackingService(repo).query("");
        assertThat(result.done()).isFalse();
        assertThat(result.message()).contains("不能为空");
    }

    @Test
    void rejectsOversizedTrackNo() {
        // ACC: getArg('No',2,1,1,'快件单号',50) 限制 50 字符
        PublicTrackingRepository repo = mock(PublicTrackingRepository.class);
        String tooLong = "X".repeat(51);
        TrackingResult result = new PublicTrackingService(repo).query(tooLong);
        assertThat(result.done()).isFalse();
        assertThat(result.message()).contains("长度不合法");
    }

    @Test
    void aggregatesEventsAndPickslatestAsTrackTime() {
        PublicTrackingRepository repo = mock(PublicTrackingRepository.class);
        when(repo.findShipmentByTrackingOrNo(any())).thenReturn(new java.util.HashMap<>(Map.of(
            "shipment_id", "ship-1",
            "tenant_id", "tenant-1",
            "shipment_no", "SHP-1",
            "customer_ref", "ORD-1",
            "status", "IN_TRANSIT",
            "destination_country", "US",
            "first_tracking_no", "1Z999AA1"
        )));
        when(repo.findTrackingEvents("ship-1")).thenReturn(List.of(
            Map.of(
                "event_time", java.sql.Timestamp.valueOf("2026-05-13 08:00:00"),
                "raw_status", "Picked up",
                "normalized_status", "CREATED",
                "location", "SHENZHEN",
                "source", "CARRIER_API",
                "tracking_no", "1Z999AA1"
            ),
            Map.of(
                "event_time", java.sql.Timestamp.valueOf("2026-05-15 14:30:00"),
                "raw_status", "In transit",
                "normalized_status", "IN_TRANSIT",
                "location", "HKG",
                "source", "CARRIER_API",
                "tracking_no", "1Z999AA1"
            )
        ));

        TrackingResult result = new PublicTrackingService(repo).query("ORD-1");

        assertThat(result.done()).isTrue();
        assertThat(result.referenceNo()).isEqualTo("ORD-1");
        assertThat(result.trackNo()).isEqualTo("1Z999AA1");
        assertThat(result.trackStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.trackMsg()).isEqualTo("In transit");
        assertThat(result.trackTime()).isEqualTo("2026-05-15 14:30");
        assertThat(result.track()).hasSize(2);
        assertThat(result.track()).extracting(TrackEvent::activity)
            .containsExactly("Picked up", "In transit");
    }
}
