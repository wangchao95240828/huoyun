package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自定义字段框架。
 *
 *   GET  /api/acc/custom-fields?table=orders     某表的所有自定义字段定义
 *   POST /api/acc/custom-fields                  新增定义
 *   PUT  /api/acc/custom-fields/{id}             修改
 *   DEL  /api/acc/custom-fields/{id}             停用
 *   POST /api/acc/custom-fields/validate         校验业务侧填值
 *
 * 业务侧用法：每条 entity 把自定义字段值存到 metadata jsonb 列。
 */
@RestController
@RequestMapping("/api/acc/custom-fields")
public class AccCustomFieldsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccCustomFieldsController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String table) {
        String sql = "SELECT id::text, table_name, field_key, field_label, field_type,"
            + " is_required, is_unique, options, validation, sort_order, is_active"
            + " FROM acc_custom_fields WHERE is_active = true";
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (table != null && !table.isBlank()) {
            sql += " AND table_name = ?";
            params.add(table);
        }
        sql += " ORDER BY table_name, sort_order, field_label";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String fieldKey = (String) body.get("fieldKey");
        String fieldLabel = (String) body.get("fieldLabel");
        String fieldType = (String) body.get("fieldType");
        if (tableName == null || !List.of("orders","customers","shipments","charges","cartons","partners").contains(tableName)) {
            throw ApiException.badRequest("tableName 必须为允许表名 (orders/customers/shipments/charges/cartons/partners)");
        }
        if (fieldKey == null || !fieldKey.matches("[a-z][a-z0-9_]{0,30}")) {
            throw ApiException.badRequest("fieldKey 必须为小写字母+下划线 (a-z, 0-9, _)，1-31 位");
        }
        if (fieldLabel == null || fieldLabel.isBlank()) {
            throw ApiException.badRequest("字段显示名必填");
        }
        if (fieldType == null || !List.of("text","number","date","select","boolean","textarea").contains(fieldType)) {
            throw ApiException.badRequest("fieldType 必须为 text/number/date/select/boolean/textarea");
        }
        if ("select".equals(fieldType)) {
            Object opts = body.get("options");
            if (!(opts instanceof List<?>) || ((List<?>) opts).isEmpty()) {
                throw ApiException.badRequest("select 类型必须提供 options 数组");
            }
        }
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO acc_custom_fields (
                  table_name, field_key, field_label, field_type, is_required, is_unique,
                  options, validation, sort_order
                ) VALUES (?, ?, ?, ?, ?::boolean, ?::boolean, ?::jsonb, ?::jsonb, ?::int)
                RETURNING id::text
                """, String.class, tableName, fieldKey, fieldLabel, fieldType,
                     body.get("isRequired") instanceof Boolean r ? r : false,
                     body.get("isUnique") instanceof Boolean u ? u : false,
                     body.get("options") == null ? null : json.toJson(body.get("options")),
                     body.get("validation") == null ? null : json.toJson(body.get("validation")),
                     body.get("sortOrder") instanceof Number n ? n.intValue() : 0);
            return Map.of("id", id, "tableName", tableName, "fieldKey", fieldKey);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.badRequest("该表已有同 key 字段: " + fieldKey);
        }
    }

    /**
     * 校验业务侧提交的值是否符合自定义字段定义。
     * body: { tableName, values: {key: value, ...} }
     */
    @PostMapping("/validate")
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        Map<String, Object> values = body.get("values") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        if (tableName == null) throw ApiException.badRequest("tableName 必填");
        List<Map<String, Object>> defs = jdbc.queryForList("""
            SELECT field_key, field_label, field_type, is_required, options, validation
              FROM acc_custom_fields WHERE table_name = ? AND is_active = true
            """, tableName);
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (Map<String, Object> def : defs) {
            String key = (String) def.get("field_key");
            String label = (String) def.get("field_label");
            String type = (String) def.get("field_type");
            Boolean required = (Boolean) def.get("is_required");
            Object v = values.get(key);
            if (Boolean.TRUE.equals(required) && (v == null || v.toString().isBlank())) {
                errors.add(label + " 必填");
                continue;
            }
            if (v == null) continue;
            switch (type) {
                case "number" -> {
                    try { Double.parseDouble(v.toString()); }
                    catch (Exception ex) { errors.add(label + " 必须为数字"); }
                }
                case "date" -> {
                    if (!v.toString().matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                        errors.add(label + " 必须为日期格式 YYYY-MM-DD");
                    }
                }
                case "boolean" -> {
                    if (!(v instanceof Boolean)) errors.add(label + " 必须为 boolean");
                }
            }
        }
        return Map.of("ok", errors.isEmpty(), "errors", errors,
            "definitions", defs.size());
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            UPDATE acc_custom_fields
               SET field_label = coalesce(?, field_label),
                   is_required = coalesce(?::boolean, is_required),
                   sort_order = coalesce(?::int, sort_order)
             WHERE id = ?::uuid
            """,
            (String) body.get("fieldLabel"),
            body.get("isRequired"),
            body.get("sortOrder"),
            id);
        return Map.of("id", id, "updated", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        int n = jdbc.update("UPDATE acc_custom_fields SET is_active = false WHERE id = ?::uuid", id);
        if (n == 0) throw ApiException.notFound("找不到该字段定义");
        return Map.of("id", id, "deactivated", true);
    }
}
