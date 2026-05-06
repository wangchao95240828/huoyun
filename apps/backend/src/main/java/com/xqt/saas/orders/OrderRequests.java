package com.xqt.saas.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class OrderRequests {
    private OrderRequests() {
    }

    public record Search(
        String keyword,
        String customerId,
        String customerCode,
        String status,
        Integer page,
        Integer pageSize
    ) {
    }

    public record Save(
        String orderNo,
        String customerId,
        String customerCode,
        String serviceId,
        String customerRef,
        String status,
        String orderEntryType,
        Map<String, Object> metadata,
        List<Line> lines
    ) {
    }

    public record Line(
        Integer lineNo,
        String itemName,
        String sku,
        BigDecimal quantity,
        BigDecimal declaredValue,
        String declaredCurrency,
        BigDecimal weightKg,
        Map<String, Object> metadata
    ) {
    }
}
