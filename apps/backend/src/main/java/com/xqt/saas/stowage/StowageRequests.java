package com.xqt.saas.stowage;

import java.math.BigDecimal;
import java.util.List;

public final class StowageRequests {
    private StowageRequests() {
    }

    /**
     * 对应 ACC act=Sync 请求体。字段名沿用旧系统大小写以便对照样本。
     */
    public record SyncRequest(
        String no,
        String theDate,
        Integer type,
        String category,           // ACC: 分类名称（不是 ID）
        Integer count,
        Integer piece,
        Integer quantity,
        BigDecimal weight,
        BigDecimal volume,
        BigDecimal declaredValue,
        BigDecimal taxAmount,
        String departureTime,
        String departurePort,
        String arrivalTime,
        String arrivalPort,
        String remark,
        String addName,
        String addTime,
        String modifyTime,
        List<String> items         // 子单号数组
    ) {
    }
}
