package com.dws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IotResponse {

    private int status;
    private String info;
    private String action;

    @Builder.Default
    private String voiceText = null;

    private List<OptionItem> options;

    @Builder.Default
    private List<PrintLabel> printLabels = null;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionItem {
        private String label;
        private Object value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintLabel {
        @Builder.Default
        private String printerName = null;

        @Builder.Default
        private Integer printQty = null;

        @Builder.Default
        private String type = null;

        @Builder.Default
        private String labelUrl = null;

        @Builder.Default
        private String labelBase64 = null;
    }
}
