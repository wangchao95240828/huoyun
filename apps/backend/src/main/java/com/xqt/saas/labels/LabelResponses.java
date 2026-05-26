package com.xqt.saas.labels;

import java.util.List;

public final class LabelResponses {
    private LabelResponses() {
    }

    /**
     * 对应 ACC act=Label 响应里的单条结果：
     *   成功 method=0：{ No, TrackNo, Type, PDF (base64), TrackNoList? }
     *   成功 method=1：{ No, TrackNo, Type, URL,           TrackNoList? }
     *   失败：       { No, Error }
     */
    public record LabelEntry(
        String no,
        String trackNo,
        String type,
        String pdf,            // base64，method=0 时填
        String url,            // method=1 时填
        List<String> trackNoList,
        String error
    ) {
        public static LabelEntry ok(String no, String trackNo, String type, String pdfBase64,
                                    String url, List<String> trackNoList) {
            return new LabelEntry(no, trackNo, type, pdfBase64, url, trackNoList, null);
        }

        public static LabelEntry error(String no, String message) {
            return new LabelEntry(no, null, null, null, null, null, message);
        }
    }

    public record LabelBatch(
        List<LabelEntry> data
    ) {
    }

    /**
     * 对应 ACC api/getNewLabel.php 输出：
     *   { success: true,  trackNo: '...', label: base64, size: N }
     *   { success: false, message: '...' }
     */
    public record RelabelResult(
        boolean success,
        String trackNo,
        String label,
        Integer size,
        String message
    ) {
        public static RelabelResult ok(String trackNo, String labelBase64, int size) {
            return new RelabelResult(true, trackNo, labelBase64, size, null);
        }

        public static RelabelResult error(String message) {
            return new RelabelResult(false, null, null, null, message);
        }
    }
}
