package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import org.springframework.dao.DataAccessException;
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

@RestController
@RequestMapping("/api/acc/tasks")
public class AccScheduledTasksController {
    private static final String TABLE = "acc_scheduled_tasks";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccScheduledTasksController(JdbcTemplate jdbc, JsonSupport json,
                                        CascadeChecker cascadeChecker, FieldGate fieldGate) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_scheduled_tasks WHERE ?::text IS NULL OR task_name ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, task_name, task_type, cron_expr, is_enabled,
                       last_run_at, next_run_at, remark,
                       audit_status, audited_at, audit_name, created_at
                FROM acc_scheduled_tasks
                WHERE ?::text IS NULL OR task_name ILIKE ?
                ORDER BY task_name
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_scheduled_tasks WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String taskName = (String) body.get("taskName");
        if (taskName == null || taskName.isBlank()) {
            throw ApiException.badRequest("任务名称必填");
        }
        Object cronExpr = body.get("cronExpr");
        if (cronExpr != null && !cronExpr.toString().isBlank()
            && cronExpr.toString().split("\\s+").length < 5) {
            throw ApiException.badRequest("Cron 表达式至少 5 段");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_scheduled_tasks (tenant_id, task_name, task_type, cron_expr, is_enabled,
                                             config_json, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::text, ?::text, ?::boolean,
                    ?::text, ?::text)
            RETURNING id::text
            """, String.class, taskName, body.get("taskType"), body.get("cronExpr"),
            body.get("isEnabled"), body.get("configJson"), body.get("remark"));
        return Map.of("id", id, "taskName", taskName);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_scheduled_tasks WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("定时任务已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_scheduled_tasks SET
              task_name = coalesce(?, task_name),
              cron_expr = coalesce(?::text, cron_expr),
              is_enabled = coalesce(?::boolean, is_enabled),
              config_json = coalesce(?::text, config_json),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("taskName"), (String) allowed.get("cronExpr"),
            allowed.get("isEnabled"), (String) allowed.get("configJson"),
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_scheduled_tasks WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("定时任务已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_scheduled_tasks WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("taskName", row.get("task_name"));
        out.put("taskType", row.get("task_type"));
        out.put("cronExpr", row.get("cron_expr"));
        out.put("isEnabled", row.get("is_enabled"));
        out.put("lastRunAt", json.value(row.get("last_run_at")));
        out.put("nextRunAt", json.value(row.get("next_run_at")));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}
