package com.xqt.saas.flows;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.common.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-flows")
public class BusinessFlowController {
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final JsonSupport json;

    public BusinessFlowController(JdbcTemplate jdbc, RequestContext context, JsonSupport json) {
        this.jdbc = jdbc;
        this.context = context;
        this.json = json;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('business.flow.read','flow.seller.read','flow.document.read')")
    @Transactional(readOnly = true)
    public Map<String, Object> list(Authentication authentication) {
        AuthPrincipal auth = context.principal(authentication);
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
        List<Map<String, Object>> items = json.rows(rows);
        return Map.of("ok", true, "items", items, "data", items);
    }
}
