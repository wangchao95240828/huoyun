package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S4：AccOrdersController.list 的 sellCharge / costCharge / branch 列正确投影。
 *
 * 验证 SQL 已包含 charges 聚合 + organizations join；本测试 mock jdbc 模拟
 * SQL 已返回 sell_charge/cost_charge/branch_name，确认 project() 映射到前端字段名。
 */
class AccOrdersListProjectionTest {

    private static Map<String, Object> orderRow(BigDecimal sell, BigDecimal cost, String branchName) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", "00000000-0000-0000-0000-000000000001");
        m.put("order_no", "ORD-001");
        m.put("customer_ref", "REF-001");
        m.put("status", "SUBMITTED");
        m.put("created_at", OffsetDateTime.parse("2026-05-29T08:00:00+08:00"));
        m.put("audit_status", "DRAFT");
        m.put("audited_at", null);
        m.put("audit_name", null);
        m.put("customer_name", "客户 A");
        m.put("branch_name", branchName);
        m.put("country", "US");
        m.put("track_no", "TRK-123");
        m.put("piece_count", 2L);
        m.put("charge_weight", new BigDecimal("3.500"));
        m.put("sell_charge", sell);
        m.put("cost_charge", cost);
        m.put("metadata", "{\"acc_compat\":{\"product\":\"EU-AIR-UPS\"}}");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stubAndCall(JdbcTemplate jdbc, AccOrdersController c,
                                                          Map<String, Object> row, String keyword) {
        when(jdbc.queryForObject(contains("count(*)"), eq(Long.class),
            any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(contains("sell_charge"), any(Object[].class)))
            .thenReturn(List.of(row));
        Map<String, Object> result = c.list(1, 20, keyword, null, null, null,
            // 28 advanced search params
            null, null, null, null, null, null, null, null, null,    // trackingNo..channelCode (9)
            null, null, null, null,                                  // weightFrom..declaredValueTo (4)
            null, null, null, null,                                  // chargeWeight + fee (4)
            null, null, null, null,                                  // address/house/remark/deliveryArea (4)
            null, null, null, null, null, null, null);               // submitted/addName/created/updated (7)
        return (List<Map<String, Object>>) result.get("data");
    }

    @Test
    void listProjectsSellAndCostAndBranch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccOrdersController c = new AccOrdersController(jdbc,
            new JsonSupport(new ObjectMapper()),
            mock(CascadeChecker.class), mock(FieldGate.class), new BranchAccessFilter());

        List<Map<String, Object>> items = stubAndCall(jdbc, c,
            orderRow(new BigDecimal("280.00"), new BigDecimal("180.00"), "上海分公司"), "ORD");

        assertThat(items).hasSize(1);
        Map<String, Object> row = items.get(0);
        assertThat(row.get("sellCharge")).isEqualTo(new BigDecimal("280.00"));
        assertThat(row.get("costCharge")).isEqualTo(new BigDecimal("180.00"));
        assertThat(row.get("branch")).isEqualTo("上海分公司");
        assertThat(row.get("product")).isEqualTo("EU-AIR-UPS");
        assertThat(row.get("trackNo")).isEqualTo("TRK-123");
        assertThat(row.get("piece")).isEqualTo(2L);
        assertThat(row.get("chargeWeight")).isEqualTo(new BigDecimal("3.500"));
    }

    @Test
    void listNullBranchProjectsEmptyString() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccOrdersController c = new AccOrdersController(jdbc,
            new JsonSupport(new ObjectMapper()),
            mock(CascadeChecker.class), mock(FieldGate.class), new BranchAccessFilter());

        List<Map<String, Object>> items = stubAndCall(jdbc, c,
            orderRow(BigDecimal.ZERO, BigDecimal.ZERO, null), null);
        // branch=null → 投影为空串而非 null
        assertThat(items.get(0).get("branch")).isEqualTo("");
    }
}
