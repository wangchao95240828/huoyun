package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 制单中心 → 打印工具栏（占位实现，返回结构化数据让前端渲染 HTML 打印）
 *
 *  POST /api/acc/orders/print-label          面单
 *  POST /api/acc/orders/print-invoice        商业发票
 *  POST /api/acc/orders/print-battery-letter 电池声明信
 *  POST /api/acc/orders/print-handover       交接清单
 *  POST /api/acc/orders/print-a4-all         A4 所有文档（合并 4 种）
 *  POST /api/acc/orders/print-simple-label   简易标签
 *  POST /api/acc/orders/print-master-label   多主单标签
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrderPrintController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccOrderPrintController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostMapping("/print-label")
    public Map<String, Object> printLabel(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "LABEL");
    }

    @PostMapping("/print-invoice")
    public Map<String, Object> printInvoice(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "INVOICE");
    }

    @PostMapping("/print-battery-letter")
    public Map<String, Object> printBatteryLetter(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "BATTERY_LETTER");
    }

    @PostMapping("/print-handover")
    public Map<String, Object> printHandover(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "HANDOVER");
    }

    @PostMapping("/print-a4-all")
    public Map<String, Object> printA4All(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "A4_ALL");
    }

    @PostMapping("/print-simple-label")
    public Map<String, Object> printSimpleLabel(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "SIMPLE_LABEL");
    }

    @PostMapping("/print-master-label")
    public Map<String, Object> printMasterLabel(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "MASTER_LABEL");
    }

    /** 2发票和2电池信（ACC 工具栏专项: 2 张商业发票 + 2 张电池信合并 PDF）. */
    @PostMapping("/print-invoice2-battery2")
    public Map<String, Object> printInvoice2Battery2(@RequestBody Map<String, Object> body) {
        return buildPrintPayload(body, "INVOICE_2_BATTERY_2");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPrintPayload(Map<String, Object> body, String docType) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        List<Map<String, Object>> docs = new java.util.ArrayList<>();
        List<String> notFound = new java.util.ArrayList<>();
        for (String id : ids) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT o.id::text AS id, o.order_no, o.customer_ref,"
                    + "       o.metadata, c.name AS customer_name,"
                    + "       c.code AS customer_code,"
                    + "       (SELECT s.id::text FROM shipment_order_links sol"
                    + "          JOIN shipments s ON s.id = sol.shipment_id"
                    + "          WHERE sol.order_id = o.id LIMIT 1) AS shipment_id"
                    + " FROM orders o"
                    + " LEFT JOIN customers c ON c.id = o.customer_id"
                    + " WHERE o.id = ?::uuid", id);
                if (rows.isEmpty()) { notFound.add(id); continue; }
                docs.add(rows.get(0));
            } catch (org.springframework.dao.DataAccessException ex) {
                notFound.add(id);
            }
        }
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("docType", docType);
        resp.put("documents", docs);
        resp.put("printedAt", java.time.OffsetDateTime.now().toString());
        resp.put("count", docs.size());
        if (!notFound.isEmpty()) resp.put("notFound", notFound);
        return resp;
    }
}
