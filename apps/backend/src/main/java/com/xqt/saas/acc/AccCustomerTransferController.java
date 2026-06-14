package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户在销售员间的转让 — 对齐 ACC Customer.php L1776 (转让客户)。
 *
 *   POST /api/acc/customer-transfer/batch  body: { customerIds, newSalesmanId }
 *   POST /api/acc/customer-transfer/assign-new body: { salesmanId, customerIds, isPrimary }
 */
@RestController
@RequestMapping("/api/acc/customer-transfer")
public class AccCustomerTransferController {
    private final JdbcTemplate jdbc;

    public AccCustomerTransferController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/batch")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchTransfer(@RequestBody Map<String, Object> body) {
        List<String> customerIds = (List<String>) body.getOrDefault("customerIds", List.of());
        String newSalesmanId = (String) body.get("newSalesmanId");
        if (customerIds.isEmpty()) {
            throw ApiException.badRequest("请至少选择一个客户");
        }
        if (newSalesmanId == null || newSalesmanId.isBlank()) {
            throw ApiException.badRequest("请选择新业务员");
        }
        // 校验业务员存在
        Integer empExists = jdbc.queryForObject(
            "SELECT count(*) FROM acc_employees WHERE id = ?::uuid", Integer.class, newSalesmanId);
        if (empExists == null || empExists == 0) {
            throw ApiException.badRequest("找不到指定的业务员");
        }
        int updated = jdbc.update("""
            UPDATE customers SET salesman_id = ?::uuid, updated_at = now()
             WHERE id = ANY(?::uuid[])
            """, newSalesmanId, (Object) customerIds.toArray(new String[0]));
        return Map.of("transferred", updated, "total", customerIds.size());
    }

    @PostMapping("/assign-new")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> assignNew(@RequestBody Map<String, Object> body) {
        List<String> customerIds = (List<String>) body.getOrDefault("customerIds", List.of());
        String salesmanId = (String) body.get("salesmanId");
        if (customerIds.isEmpty()) {
            throw ApiException.badRequest("请至少选择一个客户");
        }
        if (salesmanId == null || salesmanId.isBlank()) {
            throw ApiException.badRequest("请选择业务员");
        }
        // 找已有客户（避免重复）
        List<String> existing = jdbc.queryForList("""
            SELECT id::text FROM customers WHERE id = ANY(?::uuid[]) AND salesman_id = ?::uuid
            """, String.class, (Object) customerIds.toArray(new String[0]), salesmanId);
        int newCount = customerIds.size() - existing.size();
        int updated = jdbc.update("""
            UPDATE customers SET salesman_id = ?::uuid, updated_at = now()
             WHERE id = ANY(?::uuid[])
            """, salesmanId, (Object) customerIds.toArray(new String[0]));
        return Map.of("newAssigned", newCount, "updated", existing.size(),
            "totalProcessed", updated);
    }
}
