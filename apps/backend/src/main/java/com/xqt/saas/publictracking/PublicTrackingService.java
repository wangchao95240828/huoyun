package com.xqt.saas.publictracking;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 复刻 acc/api/Track.php 的单单号公开轨迹查询。
 *
 * 与 customer-api 的 {@code POST /tracking/query} 区别：
 *   - 不要求 API 签名（与旧 Track.php 一致）
 *   - 单条 trackNo
 *   - 响应字段对齐旧响应：done / ReferenceNo / TrackNo / TrackStatus / TrackMsg / TrackTime / Track
 */
@Service
public class PublicTrackingService {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PublicTrackingRepository repository;

    public PublicTrackingService(PublicTrackingRepository repository) {
        this.repository = repository;
    }

    public PublicTrackingResponses.TrackingResult query(String trackNo) {
        if (trackNo == null || trackNo.isBlank()) {
            return PublicTrackingResponses.TrackingResult.error("快件单号不能为空");
        }
        String normalized = trackNo.trim();
        if (normalized.length() > 50) {
            return PublicTrackingResponses.TrackingResult.error("快件单号长度不合法");
        }
        Map<String, Object> shipment = repository.findShipmentByTrackingOrNo(normalized);
        if (shipment == null) {
            // 旧版 alert('找不到该快件！')
            return PublicTrackingResponses.TrackingResult.error("找不到该快件！");
        }

        String shipmentId = (String) shipment.get("shipment_id");
        List<Map<String, Object>> events = repository.findTrackingEvents(shipmentId);

        List<PublicTrackingResponses.TrackEvent> track = new ArrayList<>(events.size());
        Map<String, Object> latest = events.isEmpty() ? null : events.get(events.size() - 1);
        for (Map<String, Object> ev : events) {
            track.add(new PublicTrackingResponses.TrackEvent(
                formatTime(ev.get("event_time")),
                stringOrNull(ev.get("location")),
                stringOrNull(ev.get("raw_status"))
            ));
        }
        String trackTime = latest != null ? formatTime(latest.get("event_time")) : null;
        String trackMsg = latest != null ? stringOrNull(latest.get("raw_status")) : null;
        String trackStatus = latest != null
            ? stringOrNull(latest.get("normalized_status"))
            : (String) shipment.get("status");

        return PublicTrackingResponses.TrackingResult.ok(
            (String) shipment.get("customer_ref") != null
                ? (String) shipment.get("customer_ref")
                : (String) shipment.get("shipment_no"),
            (String) shipment.get("first_tracking_no"),
            trackStatus,
            trackMsg,
            trackTime,
            track
        );
    }

    private String formatTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime().format(TIME_FMT);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(TIME_FMT);
        }
        return value.toString();
    }

    private String stringOrNull(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
