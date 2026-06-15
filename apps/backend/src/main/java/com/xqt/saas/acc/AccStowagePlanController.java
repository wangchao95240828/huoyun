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

    /**
     * 多柜分配 — CP-SAT 1D 分柜 + 每柜 3D 求解。
     * body: { containers: [Container], container_max_count?, items, route?, enable_lifo?, packing_factor? }
     */
    @PostMapping("/multi")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> multiPack(@RequestBody Map<String, Object> body) {
        if (!(body.get("containers") instanceof List<?>)) {
            throw ApiException.badRequest("containers 必须为数组");
        }
        if (!(body.get("items") instanceof List<?>)) {
            throw ApiException.badRequest("items 必须为数组");
        }
        Map<String, Object> result = callSolverMulti(body);

        // 落 N 个 plans
        List<Map<String, Object>> subPlans = (List<Map<String, Object>>) result.get("plans");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Map<String, Object>> bySku = new java.util.HashMap<>();
        for (Map<String, Object> it : items) bySku.put((String) it.get("sku"), it);

        List<String> planIds = new java.util.ArrayList<>();
        long ts = System.currentTimeMillis();
        for (int i = 0; i < subPlans.size(); i++) {
            Map<String, Object> p = subPlans.get(i);
            String planNo = "STOWAGE-MULTI-" + ts + "-" + (i + 1);
            String pid = persistPlan(planNo, p, bySku);
            planIds.add(pid);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalContainersUsed", result.get("total_containers_used"));
        out.put("assignmentStatus", result.get("assignment_solver_status"));
        out.put("assignmentObjective", result.get("assignment_objective"));
        out.put("planIds", planIds);
        out.put("unfittedOverall", result.get("unfitted_overall"));
        out.put("warnings", result.get("warnings"));
        return out;
    }

    /** 抽出单柜方案持久化 — 给 single + multi 复用。 */
    @SuppressWarnings("unchecked")
    private String persistPlan(String planNo, Map<String, Object> result,
                                Map<String, Map<String, Object>> bySku) {
        String containerCode = (String) result.get("container_code");
        Object cl = result.get("container_length_cm");
        Object cw = result.get("container_width_cm");
        Object ch = result.get("container_height_cm");
        Object cmw = result.get("container_max_weight_kg");
        // 从 placements 反推柜规格 (multi 模式 sub_result 没传 container 字段)，
        // 没有的话用默认 40HC
        if (cl == null) cl = 1200;
        if (cw == null) cw = 230;
        if (ch == null) ch = 260;
        if (cmw == null) cmw = result.get("weight_max_kg");
        if (cmw == null) cmw = 26000;

        String planId = jdbc.queryForObject("""
            INSERT INTO stowage_plans (
              tenant_id, plan_no, container_code,
              container_length_cm, container_width_cm, container_height_cm, container_max_weight_kg,
              route, enable_lifo, status,
              fitted_count, unfitted_count, volume_utilization, weight_used_kg,
              gravity_center_x_cm, gravity_center_y_cm, gravity_center_z_cm,
              gravity_quadrants, warnings, unfitted_skus, solved_at
            ) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, '[]'::jsonb, true, 'SOLVED',
                      ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now())
            RETURNING id::text
            """, String.class,
            defaultTenantId, planNo, containerCode, cl, cw, ch, cmw,
            result.get("fitted_count"), result.get("unfitted_count"),
            result.get("volume_utilization"), result.get("weight_used_kg"),
            ((List<?>) result.get("gravity_center")).get(0),
            ((List<?>) result.get("gravity_center")).get(1),
            ((List<?>) result.get("gravity_center")).get(2),
            toJson(result.get("gravity_quadrants")),
            toJson(result.get("warnings")),
            toJson(result.get("unfitted")));

        List<Map<String, Object>> placements = (List<Map<String, Object>>) result.get("placements");
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
        return planId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callSolverMulti(Map<String, Object> req) {
        try {
            String body = json.writeValueAsString(req);
            HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(solverUrl + "/pack/multi"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
            if (solverToken != null && !solverToken.isBlank()) {
                b.header("X-Ingest-Token", solverToken);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw ApiException.badRequest(
                    "多柜求解器返回 " + resp.statusCode() + ": "
                    + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
            return json.readValue(resp.body(), Map.class);
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) {
            LOG.error("调用多柜求解器失败", ex);
            throw ApiException.badRequest("调用多柜求解器失败: " + ex.getMessage());
        }
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

        List<Map<String, Object>> cartons = fetchCartonsForShipments(shipmentIds);
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("container", container);
        wrap.put("items", cartons);
        wrap.put("route", route);
        wrap.put("enableLifo", enableLifo);
        return create(wrap);
    }

    /**
     * 多柜模式 + shipmentIds 展开 — 拉 cartons → /pack/multi 求解 → 落多个 plan
     * body: { containers: [...], container_max_count?, shipmentIds, route?,
     *         enableLifo?, packingFactor?, customerCohesionWeight? }
     */
    @PostMapping("/multi/auto")
    @SuppressWarnings("unchecked")
    public Map<String, Object> multiAutoFromShipments(@RequestBody Map<String, Object> body) {
        if (!(body.get("containers") instanceof List<?>)) {
            throw ApiException.badRequest("containers 必须为数组");
        }
        List<String> shipmentIds = body.get("shipmentIds") instanceof List<?> l
            ? (List<String>) l : List.of();
        if (shipmentIds.isEmpty()) throw ApiException.badRequest("shipmentIds 必填");

        List<Map<String, Object>> cartons = fetchCartonsForShipments(shipmentIds);
        Map<String, Object> wrap = new LinkedHashMap<>(body);
        wrap.put("items", cartons);
        wrap.remove("shipmentIds");
        // 默认 packing_factor 0.65 (保守，避免 3D 校验后大量 unfit)
        if (!wrap.containsKey("packing_factor")) {
            Object pf = wrap.remove("packingFactor");
            wrap.put("packing_factor", pf != null ? pf : 0.65);
        }
        if (!wrap.containsKey("enable_lifo")) {
            Object el = wrap.remove("enableLifo");
            wrap.put("enable_lifo", el != null ? el : true);
        }
        if (!wrap.containsKey("customer_cohesion_weight")) {
            Object cw = wrap.remove("customerCohesionWeight");
            wrap.put("customer_cohesion_weight", cw != null ? cw : 1.0);
        }
        return multiPack(wrap);
    }

    /**
     * 拉 cartons + 缺失尺寸兜底（默认 30×30×30 cm, 10kg）。
     *
     * 输入接受三种 UUID：
     *   1. shipment.id 直接                 (最准)
     *   2. order.id   → 通过 shipment_order_links 翻译成 shipment_id
     *   3. shipment.shipment_no / order.order_no 也接受 (字符串非 UUID)
     *
     * 错误消息明确说哪个 ID 找不到，避免运营猜。
     */
    private List<Map<String, Object>> fetchCartonsForShipments(List<String> inputIds) {
        java.util.Set<String> shipmentIds = new java.util.LinkedHashSet<>();
        java.util.List<String> notFound = new java.util.ArrayList<>();

        for (String input : inputIds) {
            if (input == null || input.isBlank()) continue;
            String trimmed = input.trim();
            String sid = resolveShipmentId(trimmed);
            if (sid != null) {
                shipmentIds.add(sid);
            } else {
                notFound.add(trimmed);
            }
        }
        if (!notFound.isEmpty()) {
            throw ApiException.badRequest(
                "找不到以下 ID 对应的 shipment（接受 shipment.id / shipment_no / order.id / order_no）: "
                + String.join(", ", notFound));
        }
        if (shipmentIds.isEmpty()) {
            throw ApiException.badRequest("shipmentIds 全部为空");
        }

        List<String> ids = new java.util.ArrayList<>(shipmentIds);
        String inClause = String.join(",", ids.stream().map(s -> "?").toList());
        List<Map<String, Object>> cartons = jdbc.queryForList(
            "SELECT c.carton_no AS sku, c.actual_weight_kg AS weight_kg,"
            + "       c.length_cm, c.width_cm, c.height_cm,"
            + "       s.customer_id::text AS customer_id"
            + "  FROM cartons c JOIN shipments s ON s.id = c.shipment_id"
            + " WHERE c.tenant_id = ?::uuid AND c.shipment_id::text IN (" + inClause + ")"
            + " ORDER BY s.customer_id, c.carton_no",
            join(defaultTenantId, ids));
        if (cartons.isEmpty()) {
            throw ApiException.badRequest(
                "找到 " + ids.size() + " 个 shipment 但都没有 cartons 数据。"
                + "请先在「配载中心 → 配载管理」给这些运单录入 cartons，"
                + "或在前端拣选已签收的 shipments");
        }
        int filled = 0, missing = 0;
        for (Map<String, Object> ct : cartons) {
            if (ct.get("length_cm") == null) { ct.put("length_cm", 30); missing++; }
            else filled++;
            if (ct.get("width_cm") == null)  ct.put("width_cm", 30);
            if (ct.get("height_cm") == null) ct.put("height_cm", 30);
            if (ct.get("weight_kg") == null) ct.put("weight_kg", 10);
        }
        if (missing > 0) {
            LOG.warn("配载拉 cartons: {} 件有真实尺寸, {} 件用默认 30×30×30 cm 兜底", filled, missing);
        }
        return cartons;
    }

    /**
     * 把任意 ID 形式解析为 shipment.id。
     * 支持: shipment.id (uuid) / shipment.shipment_no / order.id (uuid) / order.order_no
     * 返回 null = 都找不到。
     */
    private String resolveShipmentId(String input) {
        boolean looksUuid = input.length() == 36 && input.indexOf('-') > 0;

        // 1. shipment.id 直接
        if (looksUuid) {
            try {
                Integer cnt = jdbc.queryForObject(
                    "SELECT count(*) FROM shipments WHERE id=?::uuid AND tenant_id=?::uuid",
                    Integer.class, input, defaultTenantId);
                if (cnt != null && cnt > 0) return input;
            } catch (DataAccessException ignored) {}
        }
        // 2. shipment.shipment_no
        try {
            String sid = jdbc.queryForObject(
                "SELECT id::text FROM shipments WHERE shipment_no=? AND tenant_id=?::uuid LIMIT 1",
                String.class, input, defaultTenantId);
            if (sid != null) return sid;
        } catch (DataAccessException ignored) {}
        // 3. order.id → shipment_order_links
        if (looksUuid) {
            try {
                String sid = jdbc.queryForObject(
                    "SELECT shipment_id::text FROM shipment_order_links WHERE order_id=?::uuid"
                    + " AND tenant_id=?::uuid LIMIT 1",
                    String.class, input, defaultTenantId);
                if (sid != null) return sid;
            } catch (DataAccessException ignored) {}
        }
        // 4. order.order_no → orders.id → shipment_order_links
        try {
            String sid = jdbc.queryForObject(
                "SELECT sol.shipment_id::text FROM orders o"
                + " JOIN shipment_order_links sol ON sol.order_id = o.id"
                + " WHERE o.order_no=? AND o.tenant_id=?::uuid LIMIT 1",
                String.class, input, defaultTenantId);
            if (sid != null) return sid;
        } catch (DataAccessException ignored) {}

        return null;
    }

    /**
     * 装柜单 PDF — 含柜号、客户分组、卸货顺序、每件 sku 坐标。
     * 仓库装柜员照单摆放。
     */
    @GetMapping("/{id}/loading-sheet")
    public org.springframework.http.ResponseEntity<byte[]> loadingSheet(@PathVariable String id) {
        Map<String, Object> plan;
        try {
            plan = jdbc.queryForMap(
                "SELECT * FROM stowage_plans WHERE id=?::uuid AND tenant_id=?::uuid",
                id, defaultTenantId);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("配载方案不存在");
        }
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT spi.sku, c.code AS customer_code, c.name AS customer_name,"
            + " spi.x_cm, spi.y_cm, spi.z_cm,"
            + " spi.placed_length_cm, spi.placed_width_cm, spi.placed_height_cm,"
            + " spi.input_weight_kg, spi.placed, spi.this_side_up, spi.fragile,"
            + " spi.rotation_type"
            + " FROM stowage_plan_items spi"
            + " LEFT JOIN customers c ON c.id = spi.customer_id"
            + " WHERE spi.plan_id = ?::uuid"
            + " ORDER BY spi.placed DESC, c.code, spi.z_cm, spi.x_cm, spi.y_cm",
            id);

        byte[] pdf = buildLoadingSheetPdf(plan, items);
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition",
                "inline; filename=\"loading-" + plan.get("plan_no") + ".pdf\"")
            .body(pdf);
    }

    private byte[] buildLoadingSheetPdf(Map<String, Object> plan, List<Map<String, Object>> items) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            // 加载嵌入的 NotoSansSC 简体中文字体 (TrueType outline, PDFBox 支持)
            org.apache.pdfbox.pdmodel.font.PDFont cjk;
            try (java.io.InputStream is = getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
                if (is == null) throw new RuntimeException("找不到字体资源 /fonts/NotoSansSC-Regular.ttf");
                cjk = org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, is, true);
            }

            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);

            float y = 800f;
            cs.setFont(cjk, 16);
            cs.beginText();
            cs.newLineAtOffset(40, y);
            cs.showText("装柜单 — " + s(plan.get("plan_no")));
            cs.endText();
            y -= 24;

            cs.setFont(cjk, 10);
            String[] header1 = {
                "柜号: " + s(plan.get("container_code")),
                "柜内尺寸: " + plan.get("container_length_cm") + " × "
                    + plan.get("container_width_cm") + " × "
                    + plan.get("container_height_cm") + " cm",
                "载重上限: " + plan.get("container_max_weight_kg") + " kg",
            };
            for (String str : header1) {
                cs.beginText(); cs.newLineAtOffset(40, y); cs.showText(str); cs.endText();
                y -= 14;
            }
            y -= 4;
            String[] header2 = {
                "装入: " + plan.get("fitted_count")
                    + " 件 / 未装: " + plan.get("unfitted_count")
                    + " 件 / 利用率: "
                    + String.format("%.1f%%", ((Number) plan.get("volume_utilization")).doubleValue() * 100),
                "使用重量: " + plan.get("weight_used_kg") + " kg",
                "重心 (x,y,z): "
                    + plan.get("gravity_center_x_cm") + ", "
                    + plan.get("gravity_center_y_cm") + ", "
                    + plan.get("gravity_center_z_cm") + " cm",
            };
            for (String str : header2) {
                cs.beginText(); cs.newLineAtOffset(40, y); cs.showText(str); cs.endText();
                y -= 14;
            }
            y -= 14;

            // 表格表头
            cs.setFont(cjk, 9);
            float[] cols = {40, 110, 175, 215, 255, 295, 380, 450, 510};
            String[] heads = {"SKU", "客户", "X(cm)", "Y(cm)", "Z(cm)",
                              "长×宽×高(cm)", "重量(kg)", "旋转", "标记"};
            for (int i = 0; i < heads.length && i < cols.length; i++) {
                cs.beginText(); cs.newLineAtOffset(cols[i], y); cs.showText(heads[i]); cs.endText();
            }
            y -= 12;
            cs.setLineWidth(0.5f);
            cs.moveTo(40, y + 6); cs.lineTo(555, y + 6); cs.stroke();
            cs.setFont(cjk, 8);

            String currentCustomer = null;
            for (Map<String, Object> it : items) {
                if (y < 40) {
                    cs.close();
                    page = new org.apache.pdfbox.pdmodel.PDPage(
                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                    doc.addPage(page);
                    cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
                    cs.setFont(cjk, 8);
                    y = 800;
                }
                String custLabel = s(it.get("customer_name"));
                if (custLabel.isEmpty()) custLabel = s(it.get("customer_code"));
                if (custLabel.isEmpty()) custLabel = "(无客户)";
                // 客户分组分隔行
                if (!custLabel.equals(currentCustomer)) {
                    currentCustomer = custLabel;
                    cs.setFont(cjk, 9);
                    cs.beginText(); cs.newLineAtOffset(40, y);
                    cs.showText("▌ 客户: " + custLabel);
                    cs.endText();
                    y -= 12;
                    cs.setFont(cjk, 8);
                }
                String flags = (!Boolean.TRUE.equals(it.get("placed")) ? "✗未装 " : "")
                    + (Boolean.TRUE.equals(it.get("this_side_up")) ? "↑朝上 " : "")
                    + (Boolean.TRUE.equals(it.get("fragile")) ? "易碎 " : "");
                String[] row = {
                    s(it.get("sku")),
                    truncate(custLabel, 10),
                    fmt(it.get("x_cm")), fmt(it.get("y_cm")), fmt(it.get("z_cm")),
                    fmt(it.get("placed_length_cm")) + "×" + fmt(it.get("placed_width_cm"))
                        + "×" + fmt(it.get("placed_height_cm")),
                    fmt(it.get("input_weight_kg")),
                    it.get("rotation_type") == null ? "-" : it.get("rotation_type").toString(),
                    flags,
                };
                for (int i = 0; i < row.length && i < cols.length; i++) {
                    cs.beginText(); cs.newLineAtOffset(cols[i], y);
                    cs.showText(row[i] == null ? "" : row[i]);
                    cs.endText();
                }
                y -= 11;
            }
            // 页脚
            if (y > 30) {
                y -= 8;
                cs.setFont(cjk, 7);
                cs.beginText(); cs.newLineAtOffset(40, y);
                cs.showText("⚠ 装柜规则: 重物在下, 易碎在上, LIFO 先卸客户在外侧 (靠门). 标记↑朝上不可倒置.");
                cs.endText();
            }
            cs.close();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成 PDF 失败: " + ex.getMessage());
        }
    }

    private static String s(Object o) { return o == null ? "" : o.toString(); }

    private static String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() <= max ? str : str.substring(0, max);
    }

    private static String fmt(Object o) {
        if (o == null) return "-";
        if (o instanceof Number n) return String.format("%.0f", n.doubleValue());
        return o.toString();
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
