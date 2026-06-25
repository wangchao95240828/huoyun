package com.xqt.saas.acc;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * R-2 + R-7: xlsx 批量导入/补收
 *
 * 接口:
 *   POST /api/acc/batch-import/orders        (multipart) — R-2 批量导入运单 xlsx
 *   POST /api/acc/batch-import/restate-ar    (multipart) — R-7 批量补收运费 xlsx
 *
 * xlsx 第一行 = header, 字段名要匹配 (大小写不敏感):
 *
 * orders.xlsx:
 *   customer_code | product | account | weight | country | postcode | address | name |
 *   phone | declare_name | declare_qty | declare_price | hs_code
 *
 * restate-ar.xlsx:
 *   tracking_no | new_amount | new_weight (可选) | remark (可选)
 */
@RestController
@RequestMapping("/api/acc/batch-import")
public class AccBatchImportController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccBatchImportController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** R-2: 批量导入运单 xlsx — 返预览 (preview) 或 commit (执行 INSERT). */
    @PostMapping("/orders")
    public Map<String, Object> importOrders(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean commit
    ) {
        List<Map<String, Object>> rows = readXlsx(file);
        List<Map<String, Object>> validRows = new ArrayList<>();
        List<Map<String, Object>> errorRows = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            List<String> errs = validateOrderRow(row);
            if (errs.isEmpty()) {
                validRows.add(row);
            } else {
                Map<String, Object> err = new LinkedHashMap<>(row);
                err.put("_rowIndex", i + 2);  // +2 because header is row 1, data starts row 2
                err.put("_errors", errs);
                errorRows.add(err);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("validCount", validRows.size());
        result.put("errorCount", errorRows.size());
        result.put("preview", validRows.subList(0, Math.min(10, validRows.size())));
        result.put("errors", errorRows);

        if (commit && !validRows.isEmpty()) {
            int inserted = doInsertOrders(validRows);
            result.put("inserted", inserted);
            // 收集 insert 失败原因
            List<Map<String, Object>> insertFails = validRows.stream()
                .filter(r -> r.containsKey("_insertError"))
                .map(r -> Map.<String, Object>of(
                    "customer_code", r.get("customer_code"),
                    "error", r.get("_insertError")))
                .toList();
            if (!insertFails.isEmpty()) result.put("insertFails", insertFails);
        } else {
            result.put("inserted", 0);
            result.put("note", commit ? "无有效行可插入" : "preview only — 设 commit=true 才真插入");
        }
        return result;
    }

    /** R-7: 批量补收运费 xlsx — 按 tracking_no 找原 charge, 写差值 charge. */
    @PostMapping("/restate-ar")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restateAr(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "DELTA") String mode  // DELTA / OVERWRITE
    ) {
        List<Map<String, Object>> rows = readXlsx(file);
        int processed = 0, skipped = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String trackingNo = strOf(row.get("tracking_no"));
            String newAmtStr = strOf(row.get("new_amount"));
            String remark = strOf(row.get("remark"));
            if (trackingNo == null || newAmtStr == null) {
                details.add(Map.of("row", i + 2, "skip", "tracking_no 或 new_amount 缺失"));
                skipped++;
                continue;
            }
            BigDecimal newAmount;
            try {
                newAmount = new BigDecimal(newAmtStr);
            } catch (NumberFormatException ex) {
                details.add(Map.of("row", i + 2, "skip", "new_amount 非数字: " + newAmtStr));
                skipped++;
                continue;
            }
            // 找原 freight charge (走 cartons.carrier_tracking_no → shipment → charges)
            Map<String, Object> origCharge;
            try {
                java.util.List<Map<String, Object>> matchRows = jdbc.queryForList("""
                    SELECT ch.id::text AS id, ch.amount, ch.currency, ch.shipment_id::text AS shipment_id,
                           ch.charge_item_id::text AS charge_item_id, ch.customer_id::text AS customer_id,
                           ch.order_id::text AS order_id
                      FROM charges ch
                      JOIN cartons c ON c.shipment_id = ch.shipment_id
                      JOIN charge_items ci ON ci.id = ch.charge_item_id
                     WHERE c.tracking_no = ?
                       AND ch.side = 'AR' AND ci.category = 'FREIGHT'
                       AND ch.status::text <> 'VOID'
                     ORDER BY ch.created_at DESC LIMIT 1
                    """, trackingNo);
                if (matchRows.isEmpty()) {
                    details.add(Map.of("row", i + 2, "skip", "找不到 tracking_no=" + trackingNo + " 的运费 charge"));
                    skipped++;
                    continue;
                }
                origCharge = matchRows.get(0);
            } catch (DataAccessException ex) {
                details.add(Map.of("row", i + 2, "skip", "找不到 tracking_no=" + trackingNo + " 的运费 charge"));
                skipped++;
                continue;
            }
            BigDecimal oldAmount = (BigDecimal) origCharge.get("amount");
            String chargeItemId = (String) origCharge.get("charge_item_id");
            String shipmentId = (String) origCharge.get("shipment_id");
            String customerId = (String) origCharge.get("customer_id");
            String orderId = (String) origCharge.get("order_id");
            String origChargeId = (String) origCharge.get("id");
            String currency = (String) origCharge.get("currency");

            if ("OVERWRITE".equalsIgnoreCase(mode)) {
                jdbc.update("UPDATE charges SET status='VOID'::charge_status WHERE id=?::uuid", origChargeId);
                insertCharge(shipmentId, chargeItemId, "AR", newAmount, currency, customerId, orderId,
                    origChargeId, "OVERWRITE: " + (remark == null ? "" : remark));
                details.add(Map.of("row", i + 2, "tracking", trackingNo,
                    "mode", "OVERWRITE", "old", oldAmount, "new", newAmount));
            } else {
                BigDecimal delta = newAmount.subtract(oldAmount);
                if (delta.signum() == 0) {
                    details.add(Map.of("row", i + 2, "tracking", trackingNo, "skip", "金额相同 0 差值"));
                    skipped++;
                    continue;
                }
                // DELTA 用 ADJUST charge_item 避免撞 unique key
                String adjustItemId = jdbc.queryForList(
                    "SELECT id::text FROM charge_items WHERE code='ADJUST' LIMIT 1", String.class)
                    .stream().findFirst().orElse(chargeItemId);
                insertCharge(shipmentId, adjustItemId, "AR", delta, currency, customerId, orderId,
                    origChargeId, "DELTA: " + (remark == null ? "" : remark));
                details.add(Map.of("row", i + 2, "tracking", trackingNo,
                    "mode", "DELTA", "old", oldAmount, "new", newAmount, "delta", delta));
            }
            processed++;
        }
        return Map.of("total", rows.size(), "processed", processed, "skipped", skipped, "details", details);
    }

    // ─── helpers ─────────────────────────────────

    private List<Map<String, Object>> readXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("file 必填");
        String name = file.getOriginalFilename();
        if (name == null || (!name.toLowerCase().endsWith(".xlsx") && !name.toLowerCase().endsWith(".xls"))) {
            throw ApiException.badRequest("仅支持 .xlsx / .xls 文件");
        }
        // EasyExcel 3.x ReadListener 不暴露 invokeHeadMap, 用第一行手动当 headers
        List<String[]> raw = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), new ReadListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext ctx) {
                    String[] cells = new String[data.size()];
                    for (int i = 0; i < cells.length; i++) cells[i] = data.getOrDefault(i, "");
                    raw.add(cells);
                }
                @Override public void doAfterAllAnalysed(AnalysisContext ctx) {}
            }).sheet().headRowNumber(0).doRead();  // headRowNumber=0 让 invoke 第一行也进
        } catch (IOException ex) {
            throw ApiException.badRequest("xlsx 读取失败: " + ex.getMessage());
        }
        if (raw.size() < 2) throw ApiException.badRequest("xlsx 至少需要 header + 1 行数据");

        String[] headers = raw.get(0);
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i] == null ? "col" + i : headers[i].trim().toLowerCase();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = 1; r < raw.size(); r++) {
            String[] cells = raw.get(r);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String v = i < cells.length ? cells[i] : null;
                row.put(headers[i], v == null ? null : v.trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> validateOrderRow(Map<String, Object> row) {
        List<String> errs = new ArrayList<>();
        if (strOf(row.get("customer_code")) == null) errs.add("customer_code 必填");
        if (strOf(row.get("product")) == null) errs.add("product 必填");
        String weight = strOf(row.get("weight"));
        try {
            if (weight == null || Double.parseDouble(weight) <= 0) errs.add("weight 必须 > 0");
        } catch (NumberFormatException ex) { errs.add("weight 非数字"); }
        if (strOf(row.get("country")) == null) errs.add("country 必填");
        String hs = strOf(row.get("hs_code"));
        if (hs != null && !hs.matches("^\\d{6,10}$")) errs.add("hs_code 必须 6-10 位纯数字");
        String postcode = strOf(row.get("postcode"));
        if (postcode != null && postcode.length() > 12) errs.add("postcode 太长");
        return errs;
    }

    private int doInsertOrders(List<Map<String, Object>> rows) {
        int n = 0;
        // 显式设 tenant context (前端没自动设)
        try {
            // 从 customers 反查 tenant (任何 customer 都行)
            String tenantId = jdbc.queryForObject(
                "SELECT tenant_id::text FROM customers LIMIT 1", String.class);
            if (tenantId != null) {
                jdbc.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', false)");
            }
        } catch (DataAccessException ignored) {}
        for (Map<String, Object> row : rows) {
            // 简化版: 只插 orders + 1 行 declaration 进 metadata.acc_compat
            String orderNo = "BATCH-" + System.currentTimeMillis() + "-" + n;
            Map<String, Object> meta = new LinkedHashMap<>();
            Map<String, Object> accCompat = new LinkedHashMap<>();
            accCompat.put("product", row.get("product"));
            accCompat.put("channelAccount", row.get("account"));
            accCompat.put("weight", row.get("weight"));
            accCompat.put("country", row.get("country"));
            Map<String, Object> recv = new LinkedHashMap<>();
            recv.put("name", row.get("name"));
            recv.put("phone", row.get("phone"));
            recv.put("address", row.get("address"));
            recv.put("postcode", row.get("postcode"));
            recv.put("country", row.get("country"));
            accCompat.put("receiver", recv);
            List<Map<String, Object>> dec = new ArrayList<>();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", row.get("declare_name"));
            d.put("quantity", row.get("declare_qty"));
            d.put("price", row.get("declare_price"));
            d.put("hsCode", row.get("hs_code"));
            d.put("origin", "CN");
            dec.add(d);
            accCompat.put("declare", dec);
            meta.put("acc_compat", accCompat);
            try {
                jdbc.update("""
                    INSERT INTO orders (tenant_id, customer_id, order_no, status, metadata, source)
                    VALUES (
                      current_setting('app.current_tenant_id')::uuid,
                      (SELECT id FROM customers WHERE code = ? LIMIT 1), ?, 'DRAFT', ?::jsonb, 'IMPORT'
                    )
                    """, row.get("customer_code"), orderNo, json.toJson(meta));
                n++;
            } catch (DataAccessException ex) {
                // 留行级错误到 row, 后续可返给用户
                row.put("_insertError", ex.getMessage());
            }
        }
        return n;
    }

    private void insertCharge(String shipmentId, String chargeItemId, String side,
                               BigDecimal amount, String currency, String customerId,
                               String orderId, String sourceChargeId, String remark) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source_charge_id", sourceChargeId);
        evidence.put("remark", remark);
        evidence.put("via", "batch-import");
        jdbc.update("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
              evidence, customer_id, order_id
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid,
              ?::charge_side, 'ESTIMATED', ?, ?, ?::jsonb, ?::uuid, ?::uuid
            )
            """, shipmentId, chargeItemId, side, currency, amount,
            json.toJson(evidence), customerId, orderId);
    }

    private static String strOf(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
