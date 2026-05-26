package com.xqt.saas.publictracking;

import java.util.List;

public final class PublicTrackingResponses {
    private PublicTrackingResponses() {
    }

    /** 对应 ACC api/Track.php 输出里 Track 数组的单条事件。 */
    public record TrackEvent(
        String time,
        String location,
        String activity
    ) {
    }

    /**
     * 对应 ACC api/Track.php 完整响应：
     *   成功：{ done:true, ReferenceNo, TrackNo, TrackStatus, TrackMsg, TrackTime, Track:[{Time,Location,Activity}] }
     *   失败：{ done:false, message:'...' }
     */
    public record TrackingResult(
        boolean done,
        String referenceNo,
        String trackNo,
        String trackStatus,
        String trackMsg,
        String trackTime,
        List<TrackEvent> track,
        String message
    ) {
        public static TrackingResult ok(String referenceNo, String trackNo,
                                        String status, String msg, String time,
                                        List<TrackEvent> track) {
            return new TrackingResult(true, referenceNo, trackNo, status, msg, time, track, null);
        }

        public static TrackingResult error(String message) {
            return new TrackingResult(false, null, null, null, null, null, null, message);
        }
    }
}
