package com.xqt.saas.flows;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.common.ListResponse;
import com.xqt.saas.common.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessFlowService {
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final JsonSupport json;

    public BusinessFlowService(JdbcTemplate jdbc, RequestContext context, JsonSupport json) {
        this.jdbc = jdbc;
        this.context = context;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ListResponse<BusinessFlowView> list(AuthPrincipal auth) {
        context.setTenant(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              id,
              flow_code,
              customer_direction,
              name,
              entry_channels,
              default_steps,
              settlement_model,
              active,
              notes,
              created_at,
              updated_at
            FROM business_flows
            WHERE tenant_id = ?::uuid
              AND deleted_at IS NULL
            ORDER BY flow_code
            """, auth.tenantId());
        return new ListResponse<>(rows.stream().map(this::view).toList());
    }

    private BusinessFlowView view(Map<String, Object> row) {
        Map<String, Object> mapped = json.row(row);
        return new BusinessFlowView(
            text(mapped, "id"),
            text(mapped, "flow_code"),
            text(mapped, "customer_direction"),
            text(mapped, "name"),
            textList(mapped.get("entry_channels")),
            textList(mapped.get("default_steps")),
            text(mapped, "settlement_model"),
            Boolean.TRUE.equals(mapped.get("active")),
            text(mapped, "notes"),
            text(mapped, "created_at"),
            text(mapped, "updated_at")
        );
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private List<String> textList(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        if (value instanceof String[] values) {
            return List.of(values);
        }
        return List.of();
    }
}
