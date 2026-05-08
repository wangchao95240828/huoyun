package com.xqt.saas.orders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        public Save {
            metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public Map<String, Object> metadata() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
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
        public Line {
            metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        public Map<String, Object> metadata() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }
}
