package com.xqt.saas.customerapi;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerApiRepository {
    private final JdbcTemplate jdbc;

    public CustomerApiRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CustomerApiCredential findByAccessKey(String accessKey) {
        enableServiceRole();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ac.id::text AS credential_id,
              ac.tenant_id::text AS tenant_id,
              ac.owner_id::text AS customer_id,
              c.code AS customer_code,
              ac.access_key,
              ac.secret_hash,
              ac.status
            FROM api_credentials ac
            JOIN customers c ON c.id = ac.owner_id AND c.tenant_id = ac.tenant_id
            WHERE ac.access_key = ?
              AND ac.owner_type = 'CUSTOMER'
            """, accessKey);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new CustomerApiCredential(
            (String) row.get("credential_id"),
            (String) row.get("tenant_id"),
            (String) row.get("customer_id"),
            (String) row.get("customer_code"),
            (String) row.get("access_key"),
            (String) row.get("secret_hash"),
            (String) row.get("status")
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void markCalled(String credentialId) {
        enableServiceRole();
        jdbc.update("""
            UPDATE api_credentials
            SET last_used_at = now()
            WHERE id = ?::uuid
            """, credentialId);
    }

    private void enableServiceRole() {
        jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
    }

    public List<Map<String, Object>> findBalances(String tenantId, String customerId) {
        return jdbc.queryForList("""
            SELECT currency, balance, account_name, status
            FROM financial_accounts
            WHERE tenant_id = ?::uuid
              AND owner_type = 'CUSTOMER'
              AND owner_id = ?::uuid
              AND metadata->>'purpose' = 'customer-api.balance'
              AND status = 'ACTIVE'
            ORDER BY currency
            """, tenantId, customerId);
    }

    public String insertDraftOrder(String tenantId, String customerId, String orderNo,
                                   String customerRef, String metadataJson) {
        return jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref,
              metadata, customer_direction, order_entry_type, service_mode
            ) VALUES (
              ?::uuid, ?, ?::uuid, 'DRAFT', 'LOCAL', ?,
              ?::jsonb, 'DOCUMENT_CUSTOMER', 'API_ORDER', 'DOCUMENT_SHIPPING'
            )
            RETURNING id::text
            """, String.class, tenantId, orderNo, customerId, customerRef, metadataJson);
    }
}
