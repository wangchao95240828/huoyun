package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMS 自动核销端点。
 *
 *   POST /api/acc/received-sms/parse   预览 raw_content 解析效果
 *   POST /api/acc/received-sms/{id}/reconcile  尝试匹配并自动核销
 *   POST /api/acc/received-sms/batch-reconcile?status=PENDING  批量
 */
@RestController
@RequestMapping("/api/acc/received-sms")
public class AccSmsReconcileController {
    private final AccSmsReconcileService service;
    private final JdbcTemplate jdbc;

    public AccSmsReconcileController(AccSmsReconcileService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody Map<String, Object> body) {
        String raw = body.get("rawContent") == null ? null : body.get("rawContent").toString();
        if (raw == null || raw.isBlank()) throw ApiException.badRequest("rawContent 必填");
        return service.parseRaw(raw);
    }

    @PostMapping("/{id}/reconcile")
    public Map<String, Object> reconcile(@PathVariable String id) {
        return service.reconcile(id);
    }

    @PostMapping("/batch-reconcile")
    public Map<String, Object> batchReconcile(@RequestParam(required = false) Integer limit) {
        int max = limit == null ? 20 : Math.min(limit, 100);
        List<String> pendingIds = jdbc.queryForList("""
            SELECT id::text FROM acc_received_sms
             WHERE audit_status = 'PENDING'
             ORDER BY sms_time ASC LIMIT ?
            """, String.class, max);
        int matched = 0, pending = 0, error = 0;
        java.util.List<Map<String, Object>> details = new java.util.ArrayList<>();
        for (String id : pendingIds) {
            try {
                Map<String, Object> r = service.reconcile(id);
                String status = (String) r.get("status");
                if ("MATCHED_AUTO".equals(status)) matched++;
                else pending++;
                details.add(r);
            } catch (Exception ex) {
                error++;
                details.add(Map.of("smsId", id, "status", "ERROR", "error", ex.getMessage()));
            }
        }
        return Map.of("total", pendingIds.size(), "matched", matched,
                      "pending", pending, "error", error, "details", details);
    }
}
