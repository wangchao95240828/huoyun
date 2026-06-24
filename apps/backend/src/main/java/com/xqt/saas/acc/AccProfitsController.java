package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/profits — 利润查询。前端列：no / customerName / productName / country
 * / chargeWeight / channelWeight / revenue / cost / profit / theDate
 *
 * 数据来源：按 shipment 聚合 charges 表 AR / AP 差值。视图聚合，无 CRUD。
 * 另外 `/summary?dateFrom=&dateTo=&groupBy=` 给前端 "利润汇总" 弹窗用。
 */
@RestController
@RequestMapping("/api/acc/profits")
public class AccProfitsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

        private final BranchAccessFilter branchAccess;

public AccProfitsController(JdbcTemplate jdbc, JsonSupport json,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String mode
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            // mode 决定额外过滤条件：
            //   UNFINISHED → status != COMPLETED
            //   OVERDUE    → status != COMPLETED AND created_at < (now - 30 days)
            //   LOWPROFIT  → AR - AP < 50 (低利阈值，可后续做成配置)
            String modeFilter = "";
            if ("UNFINISHED".equalsIgnoreCase(mode)) {
                modeFilter = " AND s.status <> 'COMPLETED'";
            } else if ("OVERDUE".equalsIgnoreCase(mode)) {
                modeFilter = " AND s.status <> 'COMPLETED' AND s.created_at < (now() - interval '30 days')";
            } else if ("LOWPROFIT".equalsIgnoreCase(mode)) {
                modeFilter = " AND (coalesce((SELECT sum(ch.amount) FROM charges ch WHERE ch.shipment_id = s.id AND ch.side='AR' AND ch.settlement_status <> 'VOID'), 0)"
                           + " - coalesce((SELECT sum(ch.amount) FROM charges ch WHERE ch.shipment_id = s.id AND ch.side='AP' AND ch.settlement_status <> 'VOID'), 0)) < 50";
            }

            // 每个 shipment 一行：AR/AP sum + 客户/渠道/国家。计费重/渠道重都来自 cartons。
            var access = branchAccess.forCurrent("s");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(DISTINCT s.id) FROM shipments s"
                + " WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)"
                + "   AND (?::date IS NULL OR s.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR s.created_at < (?::date + 1))"
                + "   AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id)"
                + modeFilter
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  s.id::text       AS id,
                  s.shipment_no,
                  s.customer_ref,
                  s.destination_country,
                  s.created_at,
                  cu.name           AS customer_name,
                  cn.name           AS channel_name,
                  coalesce((
                    SELECT sum(c.chargeable_weight_kg) FROM cartons c WHERE c.shipment_id = s.id
                  ), 0) AS charge_weight,
                  coalesce((
                    SELECT sum(c.actual_weight_kg) FROM cartons c WHERE c.shipment_id = s.id
                  ), 0) AS channel_weight,
                  -- P0-B6 修复: 利润 SQL 必须用审核时刻 freeze 的汇率快照(target_amount),
                  -- 而不是 charges.amount 原始币种值. 否则汇率波动后历史利润漂移.
                  -- 优先 snap.target_amount (CNY), 没快照 fallback ch.amount.
                  coalesce((
                    SELECT sum(coalesce(snap.target_amount, ch.amount))
                    FROM charges ch
                    LEFT JOIN exchange_rate_snapshots snap
                      ON snap.entity_type = 'charges' AND snap.entity_id = ch.id
                       AND snap.target_currency = 'CNY'
                    WHERE ch.shipment_id = s.id AND ch.side = 'AR'
                      AND ch.settlement_status <> 'VOID'
                  ), 0) AS revenue,
                  coalesce((
                    SELECT sum(coalesce(snap.target_amount, ch.amount))
                    FROM charges ch
                    LEFT JOIN exchange_rate_snapshots snap
                      ON snap.entity_type = 'charges' AND snap.entity_id = ch.id
                       AND snap.target_currency = 'CNY'
                    WHERE ch.shipment_id = s.id AND ch.side = 'AP'
                      AND ch.settlement_status <> 'VOID'
                  ), 0) AS cost,
                  -- 运单级赔偿（apply_amount 是公司实际支付，已审才入账）
                  coalesce((
                    SELECT sum(r.apply_amount) FROM acc_reparations r
                    WHERE r.shipment_id = s.id AND r.audit_status = 'AUDITED'
                  ), 0) AS reparation
                FROM shipments s
                LEFT JOIN customers cu ON cu.id = s.customer_id
                LEFT JOIN channels cn  ON cn.id = s.channel_id
                WHERE 1=1
                """
                + " AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)"
                + " AND (?::date IS NULL OR s.created_at >= ?::date)"
                + " AND (?::date IS NULL OR s.created_at < (?::date + 1))"
                + " AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id)"
                + modeFilter
                + access.sql()
                + " ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
                buildProfitsListParams(search, dateFrom, dateTo, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            // 不再静默吞 — 让前端看到具体错误, 不然 SQL bug 永远被掩盖
            org.slf4j.LoggerFactory.getLogger(AccProfitsController.class)
                .error("profits SQL failed: {}", ex.getMessage(), ex);
            throw ApiException.badRequest("利润查询失败: " + ex.getMessage());
        }
    }

    /**
     * /summary?dateFrom=&dateTo=&groupBy={customer|channel|country|day}
     *
     * 真实期间利润：profit = revenue(AR) - cost(AP) + adjustments - reparation
     *   adjustments = finance_txns(调账/退款/返利) + fines(罚款)
     *     方向：side='CUSTOMER' → +amount（公司收）；side='SUPPLIER' → -amount（公司付）
     *
     * 维度可分摊性：
     *   customer / day：可按 customer_id / the_date 聚合 finance_txns + fines
     *   channel / country：finance_txns/fines 无渠道/国家信息，不分摊（返 0 + 注释说明）
     */
    @GetMapping("/summary")
    public Map<String, Object> summary(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(defaultValue = "customer") String groupBy
    ) {
        String groupCol = switch (groupBy) {
            case "channel" -> "cn.name";
            case "country" -> "s.destination_country";
            case "day" -> "to_char(s.created_at, 'YYYY-MM-DD')";
            default -> "cu.name";
        };
        try {
            // 1) 按维度聚合 charges + reparations。两层聚合：先 per-shipment 计算
            //    AR/AP/reparation，再按维度求和。避免子查询引用 ungrouped column 的问题。
            String innerSql = """
                SELECT
                  """ + groupCol + """
                                  AS group_label,
                  s.id AS shipment_id,
                  coalesce(sum(CASE WHEN ch.side='AR' AND ch.settlement_status <> 'VOID'
                                    THEN ch.amount END), 0) AS revenue,
                  coalesce(sum(CASE WHEN ch.side='AP' AND ch.settlement_status <> 'VOID'
                                    THEN ch.amount END), 0) AS cost,
                  coalesce((
                    SELECT sum(r.apply_amount)
                    FROM acc_reparations r
                    WHERE r.shipment_id = s.id AND r.audit_status = 'AUDITED'
                  ), 0) AS reparation
                FROM shipments s
                LEFT JOIN charges ch ON ch.shipment_id = s.id
                LEFT JOIN customers cu ON cu.id = s.customer_id
                LEFT JOIN channels cn  ON cn.id = s.channel_id
                WHERE (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                GROUP BY""" + " " + groupCol + ", s.id";
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  group_label,
                  count(*) AS shipment_count,
                  sum(revenue) AS revenue,
                  sum(cost) AS cost,
                  sum(reparation) AS reparation
                FROM (""" + innerSql + """
                ) sub
                GROUP BY group_label
                ORDER BY sum(revenue) DESC NULLS LAST
                LIMIT 200
                """, dateFrom, dateFrom, dateTo, dateTo);

            // 2) finance_txns + fines 调整项：仅 customer / day 维度可准确分摊
            Map<String, BigDecimal> adjustmentsByLabel = new LinkedHashMap<>();
            if ("customer".equals(groupBy)) {
                aggregateAdjustments(adjustmentsByLabel,
                    "SELECT cu.name AS label,"
                  + " coalesce(sum(CASE WHEN t.side='CUSTOMER' THEN t.amount ELSE -t.amount END), 0) AS adj"
                  + " FROM acc_finance_txns t LEFT JOIN customers cu ON cu.id = t.customer_id"
                  + " WHERE t.audit_status = 'AUDITED'"
                  + "   AND (?::date IS NULL OR t.the_date >= ?::date)"
                  + "   AND (?::date IS NULL OR t.the_date <= ?::date)"
                  + " GROUP BY cu.name", dateFrom, dateTo);
                aggregateAdjustments(adjustmentsByLabel,
                    "SELECT cu.name AS label,"
                  + " coalesce(sum(CASE WHEN f.side='CUSTOMER' THEN f.amount ELSE -f.amount END), 0) AS adj"
                  + " FROM acc_fines f LEFT JOIN customers cu ON cu.id = f.customer_id"
                  + " WHERE f.audit_status = 'AUDITED'"
                  + "   AND (?::date IS NULL OR f.the_date >= ?::date)"
                  + "   AND (?::date IS NULL OR f.the_date <= ?::date)"
                  + " GROUP BY cu.name", dateFrom, dateTo);
            } else if ("day".equals(groupBy)) {
                aggregateAdjustments(adjustmentsByLabel,
                    "SELECT to_char(t.the_date, 'YYYY-MM-DD') AS label,"
                  + " coalesce(sum(CASE WHEN t.side='CUSTOMER' THEN t.amount ELSE -t.amount END), 0) AS adj"
                  + " FROM acc_finance_txns t"
                  + " WHERE t.audit_status = 'AUDITED'"
                  + "   AND (?::date IS NULL OR t.the_date >= ?::date)"
                  + "   AND (?::date IS NULL OR t.the_date <= ?::date)"
                  + " GROUP BY to_char(t.the_date, 'YYYY-MM-DD')", dateFrom, dateTo);
                aggregateAdjustments(adjustmentsByLabel,
                    "SELECT to_char(f.the_date, 'YYYY-MM-DD') AS label,"
                  + " coalesce(sum(CASE WHEN f.side='CUSTOMER' THEN f.amount ELSE -f.amount END), 0) AS adj"
                  + " FROM acc_fines f"
                  + " WHERE f.audit_status = 'AUDITED'"
                  + "   AND (?::date IS NULL OR f.the_date >= ?::date)"
                  + "   AND (?::date IS NULL OR f.the_date <= ?::date)"
                  + " GROUP BY to_char(f.the_date, 'YYYY-MM-DD')", dateFrom, dateTo);
            }
            // channel/country 维度：adjustments 无可分摊键，保留 0

            List<Map<String, Object>> groups = rows.stream().map(r -> {
                Map<String, Object> out = new LinkedHashMap<>();
                BigDecimal rev = (BigDecimal) r.get("revenue");
                BigDecimal cost = (BigDecimal) r.get("cost");
                BigDecimal reparation = (BigDecimal) r.get("reparation");
                if (reparation == null) reparation = BigDecimal.ZERO;
                String label = (String) r.get("group_label");
                BigDecimal adj = adjustmentsByLabel.getOrDefault(label, BigDecimal.ZERO);
                out.put("label", label);
                out.put("count", r.get("shipment_count"));
                out.put("revenue", rev);
                out.put("cost", cost);
                out.put("adjustments", adj);
                out.put("reparation", reparation);
                out.put("profit", rev.subtract(cost).add(adj).subtract(reparation));
                return out;
            }).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("groupBy", groupBy);
            // 维度限制说明：channel / country 维度 adjustments 永远 0（数据无键可分摊）
            result.put("adjustmentsSupported",
                "customer".equals(groupBy) || "day".equals(groupBy));
            result.put("groups", groups);
            return result;
        } catch (DataAccessException ex) {
            return Map.of("groupBy", groupBy, "groups", List.of(), "adjustmentsSupported", false);
        }
    }

    /** 把一条聚合 SQL 的结果累加到 adjustmentsByLabel（多条 SQL 合并成 customer/day 维度总调整）。 */
    private void aggregateAdjustments(Map<String, BigDecimal> bucket, String sql,
                                       String dateFrom, String dateTo) {
        try {
            for (Map<String, Object> row : jdbc.queryForList(sql,
                dateFrom, dateFrom, dateTo, dateTo)) {
                String label = (String) row.get("label");
                if (label == null) continue;
                BigDecimal adj = (BigDecimal) row.get("adj");
                if (adj == null) continue;
                bucket.merge(label, adj, BigDecimal::add);
            }
        } catch (DataAccessException ignored) {
            // 表缺失或 SQL 失败不影响主 summary 返回
        }
    }

    private static Object[] buildProfitsListParams(String search, String dateFrom, String dateTo,
                                                    BranchAccessFilter.AccessClause access,
                                                    int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal revenue = (BigDecimal) row.get("revenue");
        BigDecimal cost = (BigDecimal) row.get("cost");
        BigDecimal reparation = (BigDecimal) row.get("reparation");
        if (reparation == null) reparation = BigDecimal.ZERO;
        out.put("id", row.get("id"));
        out.put("no", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("productName", row.get("channel_name"));
        out.put("country", row.get("destination_country"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("channelWeight", row.get("channel_weight"));
        out.put("revenue", revenue);
        out.put("cost", cost);
        out.put("reparation", reparation);
        // 运单级利润：AR - AP - 赔偿；finance_txns/fines 按客户/供应商级聚合，
        // 不再按运单分摊（口径不一致）。期间利润见 /summary 端点。
        out.put("profit", revenue.subtract(cost).subtract(reparation));
        out.put("theDate", json.value(row.get("created_at")));
        return out;
    }

    // ════════ P0-R1 + P0-R2 + P0-R3-R7 报表端点 ════════

    /** R1 运营日报: 今日/本周/今日签收/异常单 4 个 KPI 一次返回 */
    @GetMapping("/operations-daily")
    public Map<String, Object> operationsDaily() {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            // 今日提取 (今天创建)
            out.put("todayPickup", jdbc.queryForObject(
                "SELECT count(*) FROM shipments WHERE created_at::date = current_date", Long.class));
            // 本周提取
            out.put("weekPickup", jdbc.queryForObject(
                "SELECT count(*) FROM shipments WHERE created_at >= date_trunc('week', current_date)", Long.class));
            // 今日签收
            out.put("todayDelivered", jdbc.queryForObject(
                "SELECT count(*) FROM shipments WHERE delivered_at::date = current_date", Long.class));
            // 异常单 (status IN EXCEPTION/DETAINED/RETURNING/CLAIMING)
            out.put("abnormalCount", jdbc.queryForObject("""
                SELECT count(*) FROM shipments
                WHERE status IN ('EXCEPTION','DETAINED','RETURNING','CLAIMING')
                """, Long.class));
            // 在途
            out.put("inTransit", jdbc.queryForObject(
                "SELECT count(*) FROM shipments WHERE status = 'IN_TRANSIT'", Long.class));
            return out;
        } catch (DataAccessException ex) {
            return Map.of("error", ex.getMessage());
        }
    }

    /** R2+R4 客户分析: 按 customer 聚合 piece/weight/profit/利润率/主要国家 */
    @GetMapping("/customer-analysis")
    public Map<String, Object> customerAnalysis(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false, defaultValue = "20") Integer limit) {
        try {
            int lim = Math.max(1, Math.min(200, limit));
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cu.id::text AS customer_id, cu.code AS customer_code, cu.name AS customer_name,
                       count(DISTINCT s.id) AS order_count,
                       coalesce(sum((SELECT count(*) FROM cartons WHERE shipment_id = s.id)), 0) AS piece_count,
                       coalesce(sum((SELECT sum(actual_weight_kg) FROM cartons WHERE shipment_id = s.id)), 0) AS total_weight,
                       coalesce(sum((SELECT sum(coalesce(snap.target_amount, ch.amount))
                                     FROM charges ch
                                     LEFT JOIN exchange_rate_snapshots snap
                                       ON snap.entity_type='charges' AND snap.entity_id=ch.id::text AND snap.target_currency='CNY'
                                     WHERE ch.shipment_id=s.id AND ch.side='AR' AND ch.settlement_status<>'VOID')), 0) AS revenue,
                       coalesce(sum((SELECT sum(coalesce(snap.target_amount, ch.amount))
                                     FROM charges ch
                                     LEFT JOIN exchange_rate_snapshots snap
                                       ON snap.entity_type='charges' AND snap.entity_id=ch.id::text AND snap.target_currency='CNY'
                                     WHERE ch.shipment_id=s.id AND ch.side='AP' AND ch.settlement_status<>'VOID')), 0) AS cost,
                       (SELECT s2.destination_country FROM shipments s2
                          WHERE s2.customer_id = cu.id
                          GROUP BY s2.destination_country
                          ORDER BY count(*) DESC LIMIT 1) AS main_country
                FROM shipments s
                JOIN customers cu ON cu.id = s.customer_id
                WHERE (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                GROUP BY cu.id, cu.code, cu.name
                ORDER BY revenue DESC LIMIT ?
                """, dateFrom, dateFrom, dateTo, dateTo, lim);
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>();
                java.math.BigDecimal rev = (java.math.BigDecimal) r.get("revenue");
                java.math.BigDecimal cost = (java.math.BigDecimal) r.get("cost");
                java.math.BigDecimal profit = rev.subtract(cost);
                java.math.BigDecimal profitRate = rev.signum() > 0
                    ? profit.multiply(new java.math.BigDecimal("100")).divide(rev, 2, java.math.RoundingMode.HALF_UP)
                    : java.math.BigDecimal.ZERO;
                o.put("customerCode", r.get("customer_code"));
                o.put("customerName", r.get("customer_name"));
                o.put("orderCount", r.get("order_count"));
                o.put("pieceCount", r.get("piece_count"));
                o.put("totalWeight", r.get("total_weight"));
                o.put("revenue", rev);
                o.put("cost", cost);
                o.put("profit", profit);
                o.put("profitRate", profitRate);
                o.put("mainCountry", r.get("main_country"));
                return o;
            }).toList(), "total", rows.size());
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0, "error", ex.getMessage());
        }
    }

    /** R3 业务员业绩 by 业务员×月 */
    @GetMapping("/salesman-performance")
    public Map<String, Object> salesmanPerformance(@RequestParam String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw com.xqt.saas.common.ApiException.badRequest("month 格式 YYYY-MM");
        }
        String monthStart = month + "-01";
        String monthEnd = java.time.LocalDate.parse(monthStart).plusMonths(1).toString();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                WITH emp_sales AS (
                  SELECT s.seller_id AS employee_id,
                         count(DISTINCT s.id) AS order_count,
                         coalesce(sum((SELECT count(*) FROM cartons c WHERE c.shipment_id = s.id)), 0) AS piece_count,
                         coalesce(sum((SELECT coalesce(sum(coalesce(snap.target_amount, ch.amount)), 0)
                                       FROM charges ch
                                       LEFT JOIN exchange_rate_snapshots snap
                                         ON snap.entity_type='charges' AND snap.entity_id=ch.id::text AND snap.target_currency='CNY'
                                       WHERE ch.shipment_id=s.id AND ch.side='AR' AND ch.settlement_status<>'VOID')), 0) AS sales
                  FROM shipments s
                  WHERE s.seller_id IS NOT NULL
                    AND s.created_at >= ?::date AND s.created_at < ?::date
                  GROUP BY s.seller_id
                ),
                emp_com AS (
                  SELECT employee_id, amount FROM acc_commissions WHERE the_month = ?
                )
                SELECT e.id::text AS employee_id, e.name AS employee_name,
                       coalesce(es.order_count, 0) AS order_count,
                       coalesce(es.piece_count, 0) AS piece_count,
                       coalesce(es.sales, 0) AS sales,
                       coalesce(ec.amount, 0) AS commission
                FROM acc_employees e
                LEFT JOIN emp_sales es ON es.employee_id = e.id
                LEFT JOIN emp_com ec ON ec.employee_id = e.id
                WHERE coalesce(es.order_count, 0) > 0 OR coalesce(ec.amount, 0) > 0
                ORDER BY sales DESC
                """, monthStart, monthEnd, month);
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("employeeName", r.get("employee_name"));
                o.put("orderCount", r.get("order_count"));
                o.put("pieceCount", r.get("piece_count"));
                o.put("sales", r.get("sales"));
                o.put("commission", r.get("commission"));
                return o;
            }).toList(), "month", month, "total", rows.size());
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0, "error", ex.getMessage());
        }
    }

    /** R5 账期已到客户列表 (按客户 settlement+payment_due_date) */
    @GetMapping("/expired-bills")
    public Map<String, Object> expiredBills() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cu.code AS customer_code, cu.name AS customer_name,
                       cu.settlement_type, cu.formula_bill,
                       count(ci.id) AS invoice_count,
                       coalesce(sum(ci.unpaid_amount), 0) AS total_unpaid,
                       min(ci.due_date) AS earliest_due
                FROM customer_invoices ci
                JOIN customers cu ON cu.id = ci.customer_id
                WHERE ci.due_date IS NOT NULL
                  AND ci.due_date < current_date
                  AND ci.unpaid_amount > 0
                  AND ci.status IN ('DRAFT','PENDING','CONFIRMED')
                GROUP BY cu.id, cu.code, cu.name, cu.settlement_type, cu.formula_bill
                ORDER BY earliest_due
                """);
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("customerCode", r.get("customer_code"));
                o.put("customerName", r.get("customer_name"));
                o.put("settlementType", r.get("settlement_type"));
                o.put("invoiceCount", r.get("invoice_count"));
                o.put("totalUnpaid", r.get("total_unpaid"));
                Object due = r.get("earliest_due");
                o.put("earliestDue", json.value(due));
                java.time.LocalDate dueDate = null;
                try {
                    if (due instanceof java.sql.Date) dueDate = ((java.sql.Date) due).toLocalDate();
                    else if (due instanceof java.time.LocalDate) dueDate = (java.time.LocalDate) due;
                    else if (due instanceof java.sql.Timestamp) dueDate = ((java.sql.Timestamp) due).toLocalDateTime().toLocalDate();
                    else if (due != null) {
                        String s = due.toString();
                        if (s.length() >= 10) dueDate = java.time.LocalDate.parse(s.substring(0, 10));
                    }
                } catch (Exception ignored) {}
                o.put("daysOverdue", dueDate != null
                    ? java.time.temporal.ChronoUnit.DAYS.between(dueDate, java.time.LocalDate.now())
                    : 0);
                return o;
            }).toList(), "total", rows.size());
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0, "error", ex.getMessage());
        }
    }

    /** R6 应付账龄 by partner 排行 (走 partner_invoices) */
    @GetMapping("/payable-aging-by-partner")
    public Map<String, Object> payableAgingByPartner() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.code AS partner_code, p.name AS partner_name,
                       count(pi.id) AS invoice_count,
                       coalesce(sum(CASE WHEN pi.due_date >= current_date - 30 THEN pi.unpaid_amount END), 0) AS d0_30,
                       coalesce(sum(CASE WHEN pi.due_date >= current_date - 60 AND pi.due_date < current_date - 30 THEN pi.unpaid_amount END), 0) AS d30_60,
                       coalesce(sum(CASE WHEN pi.due_date >= current_date - 90 AND pi.due_date < current_date - 60 THEN pi.unpaid_amount END), 0) AS d60_90,
                       coalesce(sum(CASE WHEN pi.due_date < current_date - 90 THEN pi.unpaid_amount END), 0) AS d90_plus,
                       coalesce(sum(pi.unpaid_amount), 0) AS total_unpaid
                FROM partner_invoices pi
                JOIN partners p ON p.id = pi.partner_id
                WHERE pi.unpaid_amount > 0
                  AND pi.status NOT IN ('VOID','CLOSED')
                GROUP BY p.id, p.code, p.name
                ORDER BY total_unpaid DESC
                """);
            return Map.of("data", rows, "total", rows.size());
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0, "error", ex.getMessage());
        }
    }

    /** R7 异常率 KPI: 扣件/退件/赔偿率 */
    @GetMapping("/abnormal-rate")
    public Map<String, Object> abnormalRate(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo) {
        try {
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM shipments
                WHERE (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                """, Long.class, dateFrom, dateFrom, dateTo, dateTo);
            Long detained = jdbc.queryForObject("""
                SELECT count(DISTINCT shipment_id) FROM acc_detains
                WHERE (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                """, Long.class, dateFrom, dateFrom, dateTo, dateTo);
            Long returned = jdbc.queryForObject("""
                SELECT count(*) FROM return_orders
                WHERE (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                """, Long.class, dateFrom, dateFrom, dateTo, dateTo);
            Long claimed = jdbc.queryForObject("""
                SELECT count(*) FROM acc_reparations
                WHERE (?::date IS NULL OR created_at >= ?::date)
                  AND (?::date IS NULL OR created_at < (?::date + 1))
                """, Long.class, dateFrom, dateFrom, dateTo, dateTo);
            double t = total == null || total == 0 ? 1.0 : total.doubleValue();
            return Map.of(
                "totalShipments", total == null ? 0 : total,
                "detained", detained == null ? 0 : detained,
                "returned", returned == null ? 0 : returned,
                "claimed", claimed == null ? 0 : claimed,
                "detainedRate", String.format("%.2f%%", (detained == null ? 0 : detained) / t * 100),
                "returnedRate", String.format("%.2f%%", (returned == null ? 0 : returned) / t * 100),
                "claimedRate", String.format("%.2f%%", (claimed == null ? 0 : claimed) / t * 100),
                "totalAbnormalRate", String.format("%.2f%%",
                    ((detained == null ? 0 : detained) + (returned == null ? 0 : returned) + (claimed == null ? 0 : claimed)) / t * 100)
            );
        } catch (DataAccessException ex) {
            return Map.of("error", ex.getMessage());
        }
    }
}
