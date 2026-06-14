package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
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
 * 多套打印模板 — 对齐 ACC 模板管理。
 *
 *   GET  /api/acc/print-templates?category=LABEL    按类型列表
 *   POST /api/acc/print-templates                   创建
 *   PUT  /api/acc/print-templates/{id}              修改（含 set-default）
 *   POST /api/acc/print-templates/{id}/render       带 vars 渲染预览（{var} 替换）
 */
@RestController
@RequestMapping("/api/acc/print-templates")
public class AccPrintTemplatesController {
    private final JdbcTemplate jdbc;

    public AccPrintTemplatesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String category) {
        String sql = "SELECT id::text, code, name, category, page_size, orientation, "
            + "page_width_mm, page_height_mm, is_default, is_active, created_at "
            + "FROM acc_print_templates WHERE is_active = true";
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql += " AND category = ?";
            params.add(category);
        }
        sql += " ORDER BY category, is_default DESC, name";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String category = (String) body.get("category");
        if (code == null || code.isBlank()) throw ApiException.badRequest("模板代码必填");
        if (name == null || name.isBlank()) throw ApiException.badRequest("模板名称必填");
        if (category == null || !List.of("LABEL","INVOICE","HANDOVER","BATTERY_LETTER","PACKING_LIST","MASTER_LABEL").contains(category)) {
            throw ApiException.badRequest("category 必须为 LABEL/INVOICE/HANDOVER/BATTERY_LETTER/PACKING_LIST/MASTER_LABEL");
        }
        String templateHtml = (String) body.get("templateHtml");
        if (templateHtml == null || templateHtml.length() < 10) {
            throw ApiException.badRequest("templateHtml 不能为空且至少 10 字符");
        }
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO acc_print_templates (
                  code, name, category, page_size, page_width_mm, page_height_mm,
                  orientation, template_html, is_default
                ) VALUES (?, ?, ?, ?, ?::numeric, ?::numeric, ?, ?, ?::boolean)
                RETURNING id::text
                """, String.class, code, name, category,
                     body.getOrDefault("pageSize", "A4"),
                     body.get("pageWidthMm"), body.get("pageHeightMm"),
                     body.getOrDefault("orientation", "PORTRAIT"),
                     templateHtml,
                     body.get("isDefault") instanceof Boolean b ? b : false);
            return Map.of("id", id, "code", code);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.badRequest("模板代码已存在: " + code);
        }
    }

    @PutMapping("/{id}/set-default")
    public Map<String, Object> setDefault(@PathVariable String id) {
        Map<String, Object> tpl;
        try {
            tpl = jdbc.queryForMap("SELECT category FROM acc_print_templates WHERE id=?::uuid", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该模板");
        }
        // 取消同 category 其它默认
        jdbc.update("UPDATE acc_print_templates SET is_default = false WHERE category = ? AND id <> ?::uuid",
            tpl.get("category"), id);
        jdbc.update("UPDATE acc_print_templates SET is_default = true WHERE id = ?::uuid", id);
        return Map.of("id", id, "isDefault", true);
    }

    @PostMapping("/{id}/render")
    @SuppressWarnings("unchecked")
    public Map<String, Object> render(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String html;
        try {
            html = jdbc.queryForObject(
                "SELECT template_html FROM acc_print_templates WHERE id=?::uuid", String.class, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该模板");
        }
        Map<String, Object> vars = body.get("vars") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            html = html.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return Map.of("rendered", html);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        int n = jdbc.update("UPDATE acc_print_templates SET is_active = false WHERE id = ?::uuid", id);
        if (n == 0) throw ApiException.notFound("找不到该模板");
        return Map.of("id", id, "deactivated", true);
    }
}
