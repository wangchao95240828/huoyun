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

    /** 拉 cartons + 缺失尺寸兜底。 */
    private List<Map<String, Object>> fetchCartonsForShipments(List<String> shipmentIds) {
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
        for (Map<String, Object> ct : cartons) {
            if (ct.get("length_cm") == null) ct.put("length_cm", 30);
            if (ct.get("width_cm") == null)  ct.put("width_cm", 30);
            if (ct.get("height_cm") == null) ct.put("height_cm", 30);
            if (ct.get("weight_kg") == null) ct.put("weight_kg", 10);
        }
        return cartons;
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
            // A4 portrait: 595 × 842 pt
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
            // 用内嵌 PDF 标准字体 Helvetica + 备用 CourierBold 显示中文需 Type0，
            // 但 PDFBox 默认 Helvetica 不支持中文。这里给一个简化版，对应中英文混排：
            // 用 Helvetica + 把客户名/备注的中文字符显示为 ASCII fallback 或省略中文，
            // 真要支持中文需要嵌入 NotoSansCJK 字体。
            org.apache.pdfbox.pdmodel.font.PDFont font =
                new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            org.apache.pdfbox.pdmodel.font.PDFont bold =
                new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);

            float y = 800f;
            cs.setFont(bold, 16);
            cs.beginText();
            cs.newLineAtOffset(40, y);
            cs.showText("Loading Sheet - " + safeAscii(plan.get("plan_no")));
            cs.endText();
            y -= 24;

            cs.setFont(font, 10);
            String[] header1 = {
                "Container: " + safeAscii(plan.get("container_code")),
                "Size: " + plan.get("container_length_cm") + " x "
                    + plan.get("container_width_cm") + " x "
                    + plan.get("container_height_cm") + " cm",
                "Max weight: " + plan.get("container_max_weight_kg") + " kg",
            };
            for (String s : header1) {
                cs.beginText(); cs.newLineAtOffset(40, y); cs.showText(s); cs.endText();
                y -= 14;
            }
            y -= 4;
            String[] header2 = {
                "Fitted: " + plan.get("fitted_count")
                    + " / Unfitted: " + plan.get("unfitted_count")
                    + " / Utilization: "
                    + String.format("%.1f%%", ((Number) plan.get("volume_utilization")).doubleValue() * 100),
                "Weight used: " + plan.get("weight_used_kg") + " kg",
                "Gravity center (x,y,z): "
                    + plan.get("gravity_center_x_cm") + ", "
                    + plan.get("gravity_center_y_cm") + ", "
                    + plan.get("gravity_center_z_cm") + " cm",
            };
            for (String s : header2) {
                cs.beginText(); cs.newLineAtOffset(40, y); cs.showText(s); cs.endText();
                y -= 14;
            }
            y -= 14;

            // 表格 header
            cs.setFont(bold, 9);
            float[] cols = {40, 105, 180, 250, 310, 380, 440, 510};
            String[] heads = {"SKU", "Customer", "X (cm)", "Y (cm)", "Z (cm)",
                              "L x W x H", "Weight (kg)", "Flags"};
            for (int i = 0; i < heads.length; i++) {
                cs.beginText(); cs.newLineAtOffset(cols[i], y); cs.showText(heads[i]); cs.endText();
            }
            y -= 12;
            cs.setLineWidth(0.5f);
            cs.moveTo(40, y + 6); cs.lineTo(555, y + 6); cs.stroke();
            cs.setFont(font, 8);

            for (Map<String, Object> it : items) {
                if (y < 40) {
                    cs.close();
                    page = new org.apache.pdfbox.pdmodel.PDPage(
                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                    doc.addPage(page);
                    cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
                    cs.setFont(font, 8);
                    y = 800;
                }
                String[] row = {
                    safeAscii(it.get("sku")),
                    safeAscii(it.get("customer_code")),
                    fmt(it.get("x_cm")), fmt(it.get("y_cm")), fmt(it.get("z_cm")),
                    fmt(it.get("placed_length_cm")) + "x" + fmt(it.get("placed_width_cm"))
                        + "x" + fmt(it.get("placed_height_cm")),
                    fmt(it.get("input_weight_kg")),
                    (Boolean.TRUE.equals(it.get("placed")) ? "" : "[X]")
                    + (Boolean.TRUE.equals(it.get("this_side_up")) ? "[UP]" : "")
                    + (Boolean.TRUE.equals(it.get("fragile")) ? "[FR]" : ""),
                };
                for (int i = 0; i < row.length; i++) {
                    cs.beginText(); cs.newLineAtOffset(cols[i], y); cs.showText(row[i]); cs.endText();
                }
                y -= 10;
            }
            cs.close();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成 PDF 失败: " + ex.getMessage());
        }
    }

    private static String safeAscii(Object o) {
        if (o == null) return "";
        String s = o.toString();
        // 临时 fallback: 非 ASCII 用 ? 替代（避免 Helvetica 渲染中文挂掉）
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c < 128 ? c : '?');
        }
        return sb.toString();
    }

    private static String fmt(Object o) {
        if (o == null) return "-";
        if (o instanceof Number n) return String.format("%.1f", n.doubleValue());
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
