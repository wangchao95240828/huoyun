package com.xqt.saas.common;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AuditService(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void log(AuthPrincipal actor, String entityType, String entityId, String action, Object beforeData, Object afterData) {
        jdbc.update("""
            INSERT INTO audit_logs (tenant_id, actor_id, entity_type, entity_id, action, before_data, after_data)
            VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?::jsonb, ?::jsonb)
            """,
            actor.tenantId(),
            actor.userId(),
            entityType,
            entityId,
            action,
            json.toJson(beforeData),
            json.toJson(afterData)
        );
    }
}
