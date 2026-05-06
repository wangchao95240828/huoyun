package com.xqt.saas.common;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonSupport {
    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> rows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::row).toList();
    }

    public Map<String, Object> row(Map<String, Object> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        row.forEach((key, value) -> mapped.put(key, value(value)));
        return mapped;
    }

    public Object value(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID || value instanceof Timestamp || value instanceof OffsetDateTime || value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof Array array) {
            try {
                Object raw = array.getArray();
                if (raw instanceof Object[] values) {
                    return Arrays.stream(values).map(this::value).toList();
                }
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if ("org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return value.toString();
        }
        return value;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize JSON payload", ex);
        }
    }
}
