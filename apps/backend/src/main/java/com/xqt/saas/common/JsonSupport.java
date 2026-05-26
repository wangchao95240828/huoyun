package com.xqt.saas.common;

import java.sql.SQLException;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSupport.class);
    private static final String POSTGRES_JSON_OBJECT_CLASS = "org.postgresql.util.PGobject";

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
            } catch (SQLException ex) {
                LOGGER.debug("Unable to read SQL array value", ex);
                return List.of();
            }
        }
        if (POSTGRES_JSON_OBJECT_CLASS.equals(value.getClass().getName())) {
            return value.toString();
        }
        return value;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize JSON payload", ex);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize JSON payload", ex);
        }
    }
}
