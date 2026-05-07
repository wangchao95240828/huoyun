package com.xqt.saas.orders;

import java.util.List;
import java.util.Map;

public final class OrderResponses {
    private OrderResponses() {
    }

    public record OrderView(
        String id,
        String orderNo,
        String customerId,
        String customerCode,
        String customerName,
        String customerRef,
        String status,
        String source,
        String customerDirection,
        String orderEntryType,
        String serviceMode,
        String serviceCode,
        String serviceName,
        Object metadata,
        String createdAt,
        String updatedAt,
        String createdByName,
        String updatedByName,
        List<OrderLineView> lines
    ) {
    }

    public record OrderLineView(
        String id,
        Integer lineNo,
        String itemName,
        String sku,
        Object quantity,
        Object declaredValue,
        String declaredCurrency,
        Object weightKg,
        Object metadata,
        String createdAt,
        String updatedAt
    ) {
    }

    static OrderView orderView(Map<String, Object> row, List<OrderLineView> lines) {
        return new OrderView(
            text(row, "id"),
            text(row, "order_no"),
            text(row, "customer_id"),
            text(row, "customer_code"),
            text(row, "customer_name"),
            text(row, "customer_ref"),
            text(row, "status"),
            text(row, "source"),
            text(row, "customer_direction"),
            text(row, "order_entry_type"),
            text(row, "service_mode"),
            text(row, "service_code"),
            text(row, "service_name"),
            row.get("metadata"),
            text(row, "created_at"),
            text(row, "updated_at"),
            text(row, "created_by_name"),
            text(row, "updated_by_name"),
            lines
        );
    }

    static OrderLineView orderLineView(Map<String, Object> row) {
        return new OrderLineView(
            text(row, "id"),
            integer(row.get("line_no")),
            text(row, "item_name"),
            text(row, "sku"),
            row.get("quantity"),
            row.get("declared_value"),
            text(row, "declared_currency"),
            row.get("weight_kg"),
            row.get("metadata"),
            text(row, "created_at"),
            text(row, "updated_at")
        );
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
