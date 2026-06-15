package com.xqt.saas.acc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 3D 配载方案 — Java ↔ Python 求解服务的桥。
 *
 *   POST /api/acc/stowage/plan         body: { container, items, route, enableLifo }
 *                                       → 调 Python /pack → 落 stowage_plans + items
 *   GET  /api/acc/stowage/plan         查列表（支持筛 status / containerCode）
 *   GET  /api/acc/stowage/plan/{id}    查详情含 placements
 *   POST /api/acc/stowage/plan/auto    body: { shipmentIds, containerCode, route, enableLifo }
 *                                       自动从 shipments+cartons 构造 items 求解
 *   POST /api/acc/stowage/plan/{id}/approve   方案审定
 *   DELETE /api/acc/stowage/plan/{id}
 */
@RestController
@RequestMapping("/api/acc/stowage/plan")
public class AccStowagePlanController {
    private static final Logger LOG = LoggerFactory.getLogger(AccStowagePlanController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${xqt.stowage.solver-url:http://127.0.0.1:8100}")
    private String solverUrl;

    @Value("${xqt.stowage.solver-token:}")
    private String solverToken;

    @Value("${xqt.tracking.tenant-id:2bda8c16-7b19-4ce6-ab71-9584f5a140ed}")
    private String defaultTenantId;

    public AccStowagePlanController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String containerCode
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        Long total = jdbc.queryForObject(
            "SELECT count(*) FROM stowage_plans WHERE tenant_id=?::uuid"
            + " AND (?::text IS NULL OR status = ?)"
            + " AND (?::text IS NULL OR container_code ILIKE ?)",
            Long.class, defaultTenantId, status, status,
            containerCode, containerCode == null ? null : "%" + containerCode + "%");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, plan_no, container_code, status,
                   fitted_count, unfitted_count, volume_utilization,
                   weight_used_kg, container_max_weight_kg AS weight_max_kg,
                   gravity_center_x_cm, gravity_center_y_cm, gravity_center_z_cm,
                   created_at, solved_at, approved_at
              FROM stowage_plans
             WHERE tenant_id = ?::uuid
               AND (?::text IS NULL OR status = ?)
               AND (?::text IS NULL OR container_code ILIKE ?)
             ORDER BY created_at DESC LIMIT ? OFFSET ?
            """, defaultTenantId, status, status,
                 containerCode, containerCode == null ? null : "%" + containerCode + "%",
                 limit, offset);
        return AccPaging.result(rows, total == null ? 0 : total);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Map<String, Object> plan;
        try {
            plan = jdbc.queryForMap(
                "SELECT * FROM stowage_plans WHERE id=?::uuid AND tenant_id=?::uuid",
                id, defaultTenantId);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("配载方案不存在");
        }
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id::text AS id, sku, customer_id::text AS customer_id,"
            + " shipment_id::text AS shipment_id, carton_id::text AS carton_id,"
            + " input_length_cm, input_width_cm, input_height_cm, input_weight_kg,"
            + " this_side_up, fragile, load_bearing_kg,"
            + " placed, x_cm, y_cm, z_cm, rotation_type,"
            + " placed_length_cm, placed_width_cm, placed_height_cm, customer_priority"
            + " FROM stowage_plan_items WHERE plan_id=?::uuid ORDER BY placed DESC, sku",
            id);
        Map<String, Object> out = new LinkedHashMap<>(plan);
        out.put("items", items);
        return out;
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> container = (Map<String, Object>) body.get("container");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        List<String> route = body.get("route") instanceof List<?> r
            ? (List<String>) r : List.of();
        boolean enableLifo = !Boolean.FALSE.equals(body.get("enableLifo"));
        if (container == null) throw ApiException.badRequest("container 必填");
        if (items == null || items.isEmpty()) throw ApiException.badRequest("items 不能为空");

        // 调 Python 求解器
        Map<String, Object> solverReq = new LinkedHashMap<>();
        solverReq.put("container", container);
        solverReq.put("items", items);
        solverReq.put("route", route);
        solverReq.put("enable_lifo", enableLifo);

        Map<String, Object> result = callSolver(solverReq);

        // 落 stowage_plans
        String planNo = "STOWAGE-" + System.currentTimeMillis();
        String containerCode = (String) container.get("code");
        String planId = jdbc.queryForObject("""
            INSERT INTO stowage_plans (
              tenant_id, plan_no, container_code,
              container_length_cm, container_width_cm, container_height_cm, container_max_weight_kg,
              route, enable_lifo, status,
              fitted_count, unfitted_count, volume_utilization, weight_used_kg,
              gravity_center_x_cm, gravity_center_y_cm, gravity_center_z_cm,
              gravity_quadrants, warnings, unfitted_skus, solved_at
            ) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'SOLVED',
                      ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now())
            RETURNING id::text
            """, String.class,
            defaultTenantId, planNo, containerCode,
            container.get("length_cm"), container.get("width_cm"),
            container.get("height_cm"), container.get("max_weight_kg"),
            toJson(route), enableLifo,
            result.get("fitted_count"), result.get("unfitted_count"),
            result.get("volume_utilization"), result.get("weight_used_kg"),
            ((List<?>) result.get("gravity_center")).get(0),
            ((List<?>) result.get("gravity_center")).get(1),
            ((List<?>) result.get("gravity_center")).get(2),
            toJson(result.get("gravity_quadrants")),
            toJson(result.get("warnings")),
            toJson(result.get("unfitted")));

        // 落 stowage_plan_items
        List<Map<String, Object>> placements = (List<Map<String, Object>>) result.get("placements");
        // 先用 sku 索引找原 item 的输入参数
        Map<String, Map<String, Object>> bySku = new java.util.HashMap<>();
        for (Map<String, Object> it : items) bySku.put((String) it.get("sku"), it);

        for (Map<String, Object> p : placements) {
            String sku = (String) p.get("sku");
            Map<String, Object> orig = bySku.getOrDefault(sku, Map.of());
            jdbc.update("""
                INSERT INTO stowage_plan_items (
                  tenant_id, plan_id, sku, customer_id,
                  input_length_cm, input_width_cm, input_height_cm, input_weight_kg,
                  this_side_up, fragile, load_bearing_kg, customer_priority,
                  placed, x_cm, y_cm, z_cm, rotation_type,
                  placed_length_cm, placed_width_cm, placed_height_cm
                ) VALUES (?::uuid, ?::uuid, ?, ?::uuid,
                          ?, ?, ?, ?,
                          ?, ?, ?, ?,
                          true, ?, ?, ?, ?,
                          ?, ?, ?)
                """, defaultTenantId, planId, sku, orig.get("customer_id"),
                     orig.get("length_cm"), orig.get("width_cm"),
                     orig.get("height_cm"), orig.get("weight_kg"),
                     Boolean.TRUE.equals(orig.get("this_side_up")),
                     Boolean.TRUE.equals(orig.get("fragile")),
                     orig.getOrDefault("load_bearing", 0),
                     orig.getOrDefault("customer_priority", 0),
                     p.get("x_cm"), p.get("y_cm"), p.get("z_cm"),
                     p.get("rotation_type"),
                     p.get("length_cm"), p.get("width_cm"), p.get("height_cm"));
        }
        // 未装入的也落一行（placed=false）
        for (Object skuObj : (List<?>) result.get("unfitted")) {
            String sku = skuObj.toString();
            Map<String, Object> orig = bySku.getOrDefault(sku, Map.of());
            jdbc.update("""
                INSERT INTO stowage_plan_items (
                  tenant_id, plan_id, sku, customer_id,
                  input_length_cm, input_width_cm, input_height_cm, input_weight_kg,
                  this_side_up, fragile, load_bearing_kg, customer_priority,
                  placed
                ) VALUES (?::uuid, ?::uuid, ?, ?::uuid,
                          ?, ?, ?, ?,
                          ?, ?, ?, ?,
                          false)
                """, defaultTenantId, planId, sku, orig.get("customer_id"),
                     orig.get("length_cm"), orig.get("width_cm"),
                     orig.get("height_cm"), orig.get("weight_kg"),
                     Boolean.TRUE.equals(orig.get("this_side_up")),
                     Boolean.TRUE.equals(orig.get("fragile")),
                     orig.getOrDefault("load_bearing", 0),
                     orig.getOrDefault("customer_priority", 0));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planId", planId);
        out.put("planNo", planNo);
        out.put("status", "SOLVED");
        out.put("result", result);
        return out;
    }

    /** 从 shipments + cartons 自动拉箱子组装 items 求解。 */
    @PostMapping("/auto")
    @SuppressWarnings("unchecked")
    public Map<String, Object> autoFromShipments(@RequestBody Map<String, Object> body) {
        Map<String, Object> container = (Map<String, Object>) body.get("container");
        if (container == null) throw ApiException.badRequest("container 必填");
        List<String> shipmentIds = body.get("shipmentIds") instanceof List<?> l
            ? (List<String>) l : List.of();
        if (shipmentIds.isEmpty()) throw ApiException.badRequest("shipmentIds 必填");
        List<String> route = body.get("route") instanceof List<?> r ? (List<String>) r : List.of();
        boolean enableLifo = !Boolean.FALSE.equals(body.get("enableLifo"));

        // 拉 cartons + customer_id
        String inClause = String.join(",", shipmentIds.stream().map(s -> "?").toList());
        List<Map<String, Object>> cartons = jdbc.queryForList(
            "SELECT c.carton_no AS sku, c.actual_weight_kg AS weight_kg,"
            + "       c.length_cm, c.width_cm, c.height_cm,"
            + "       s.customer_id::text AS customer_id"
            + "  FROM cartons c JOIN shipments s ON s.id = c.shipment_id"
            + " WHERE c.tenant_id = ?::uuid AND c.shipment_id::text IN (" + inClause + ")"
            + " ORDER BY s.customer_id, c.carton_no",
            join(defaultTenantId, shipmentIds));
        if (cartons.isEmpty()) throw ApiException.badRequest("没找到 cartons 数据");
        // 缺失尺寸的兜底（默认 30×30×30 cm, 10kg），生产应在前端校验
        for (Map<String, Object> ct : cartons) {
            if (ct.get("length_cm") == null) ct.put("length_cm", 30);
            if (ct.get("width_cm") == null)  ct.put("width_cm", 30);
            if (ct.get("height_cm") == null) ct.put("height_cm", 30);
            if (ct.get("weight_kg") == null) ct.put("weight_kg", 10);
        }

        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("container", container);
        wrap.put("items", cartons);
        wrap.put("route", route);
        wrap.put("enableLifo", enableLifo);
        return create(wrap);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id) {
        int n = jdbc.update(
            "UPDATE stowage_plans SET status='APPROVED', approved_at=now()"
            + " WHERE id=?::uuid AND status='SOLVED'", id);
        if (n == 0) throw ApiException.badRequest("仅 SOLVED 状态可审定");
        return Map.of("id", id, "status", "APPROVED");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM stowage_plans WHERE id=?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callSolver(Map<String, Object> req) {
        try {
            String body = json.writeValueAsString(req);
            HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(solverUrl + "/pack"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
            if (solverToken != null && !solverToken.isBlank()) {
                b.header("X-Ingest-Token", solverToken);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw ApiException.badRequest(
                    "求解器返回 " + resp.statusCode() + ": "
                    + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
            return json.readValue(resp.body(), Map.class);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("调用配载求解器失败", ex);
            throw ApiException.badRequest("调用配载求解器失败: " + ex.getMessage());
        }
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o == null ? List.of() : o); }
        catch (Exception ex) { return "[]"; }
    }

    private static Object[] join(String tenantId, List<String> rest) {
        Object[] arr = new Object[1 + rest.size()];
        arr[0] = tenantId;
        for (int i = 0; i < rest.size(); i++) arr[i + 1] = rest.get(i);
        return arr;
    }
}
