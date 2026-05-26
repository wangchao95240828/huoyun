package com.xqt.saas.labels;

import java.util.List;

public final class LabelRequests {
    private LabelRequests() {
    }

    /**
     * 对应 ACC act=Label 请求体：
     *   No:     单字符串或数组
     *   Type:   PDF（默认）/ ZPL
     *   Method: 0 = 直接返回 base64；非 0 = 保存到文件并返回 URL
     */
    public record GenerateLabel(
        String no,
        List<String> nos,
        String type,
        Integer method
    ) {
        public List<String> resolved() {
            if (nos != null && !nos.isEmpty()) return nos;
            if (no != null && !no.isBlank()) return List.of(no);
            return List.of();
        }

        public String resolvedType() {
            return (type == null || type.isBlank()) ? "PDF" : type.toUpperCase();
        }

        public boolean saveToFile() {
            return method != null && method != 0;
        }
    }

    /**
     * 对应 ACC api/getNewLabel.php 请求：
     *   trackNo:     新或旧子单号
     *   printWidth:  打印宽度，<=0 时默认 100
     *   printHeight: 打印高度，<=0 时默认 150
     */
    public record RelabelPdf(
        String trackNo,
        Integer printWidth,
        Integer printHeight
    ) {
    }
}
