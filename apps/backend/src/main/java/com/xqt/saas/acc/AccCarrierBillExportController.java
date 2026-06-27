package com.xqt.saas.acc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 承运商账单 & 成本明细 — 2 种业内通用 xlsx 格式.
 *
 * 1. FedEx-B价账单 风格: GET /api/acc/partner-invoices/{id}/export-fedex-style
 *    14 列 — Shipment Date / Customer Ref / Tracking / Master / Zip / Zone /
 *           Rated Weight / 备注 / 单价 / 单件运费 / 附加费 / 燃油 / 总运费
 *
 * 2. 成本拆分明细 风格 (tw-TW000008 样式): GET /api/acc/bills/{id}/export-cost-detail
 *    13 列 — 账单日期 / 主单 / 子单 / 产品 / 费用 / 费用名称 / 费用明细 /
 *           账单计费重 / 客户预报重 / 目的地邮编 / 分区 / 预报尺寸 / 账单尺寸
 */
@RestController
public class AccCarrierBillExportController {
    private final JdbcTemplate jdbc;

    public AccCarrierBillExportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/acc/partner-invoices/{id}/export-fedex-style")
    public ResponseEntity<byte[]> exportFedexStyle(@PathVariable String id) throws IOException {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                SELECT pi.invoice_no,
                       pi.invoice_date,
                       l.carton_id,
                       c.express_no AS shipment_no,
                       c.customer_ref,
                       l.tracking_no,
                       l.recipient_state,
                       l.recipient_postal_code,
                       l.zone_code,
                       l.rated_weight,
                       l.unit_price,
                       l.line_amount,
                       l.surcharge_amount,
                       l.fuel_amount,
                       l.total_amount,
                       l.remark
                  FROM partner_invoice_lines l
                  JOIN partner_invoices pi ON pi.id = l.invoice_id
             LEFT JOIN cartons ct ON ct.id = l.carton_id
             LEFT JOIN shipments c ON c.id = ct.shipment_id
                 WHERE pi.id = ?::uuid
                 ORDER BY l.created_at
                """, id);
        } catch (org.springframework.dao.DataAccessException ex) {
            // partner_invoice_lines 列结构可能差异, 退化到最小列
            rows = jdbc.queryForList("""
                SELECT pi.invoice_no, pi.invoice_date, l.*
                  FROM partner_invoice_lines l
                  JOIN partner_invoices pi ON pi.id = l.invoice_id
                 WHERE pi.id = ?::uuid ORDER BY l.created_at
                """, id);
        }
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("FedEx-B价账单");
            CellStyle hdr = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true);
            hdr.setFont(f);
            hdr.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hdr.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                "Shipment Date", "Original Customer Reference", "Express or Ground Tracking",
                "TDMasterTrackingID", "Recipient State", "Recipient Zip Code",
                "Zone Code", "Rated Weight Amount", "收费备注",
                "单价运费", "单件运费", "附加费", "燃油", "总运费"
            };
            Row r0 = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = r0.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hdr);
                sh.setColumnWidth(i, 4400);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row rr = sh.createRow(r++);
                setStr(rr, 0, formatDateCompact(row.get("invoice_date")));
                setStr(rr, 1, str(row.get("customer_ref")));
                setStr(rr, 2, str(row.get("tracking_no")));
                setStr(rr, 3, str(row.get("shipment_no")));
                setStr(rr, 4, str(row.get("recipient_state")));
                setStr(rr, 5, str(row.get("recipient_postal_code")));
                setStr(rr, 6, str(row.get("zone_code")));
                setNum(rr, 7, row.get("rated_weight"));
                setStr(rr, 8, str(row.get("remark")));
                setNum(rr, 9, row.get("unit_price"));
                setNum(rr, 10, row.get("line_amount"));
                setNum(rr, 11, row.get("surcharge_amount"));
                setNum(rr, 12, row.get("fuel_amount"));
                setNum(rr, 13, row.get("total_amount"));
            }

            wb.write(out);
            return wrap(out.toByteArray(), "FedEx-B价账单-" + rows.get(0).get("invoice_no") + ".xlsx");
        }
    }

    @GetMapping("/api/acc/bills/{id}/export-cost-detail")
    public ResponseEntity<byte[]> exportCostDetail(@PathVariable String id) throws IOException {
        Map<String, Object> invoice;
        try {
            invoice = jdbc.queryForMap("""
                SELECT i.invoice_no, i.bill_date AS invoice_date,
                       c.code AS customer_code, c.name AS customer_name
                  FROM invoices i
                  JOIN customers c ON c.id = i.customer_id
                 WHERE i.id = ?::uuid
                """, id);
        } catch (org.springframework.dao.DataAccessException ex) {
            return ResponseEntity.notFound().build();
        }

        // 按"成本拆分明细"风格: 一个 charge 拆出 (运费/燃油/附加费/特殊优惠 etc) 多行
        // 走 charges + 内嵌 evidence/breakdown
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT i.bill_date AS bill_date,
                   s.shipment_no AS master_no,
                   COALESCE(ct.carton_no, s.shipment_no) AS child_no,
                   cn.name AS channel_name,
                   ch.amount AS amount,
                   ci.name AS fee_name,
                   ci.code AS fee_code,
                   ch.charge_weight AS billed_weight,
                   o.weight_kg AS reported_weight,
                   o.receiver_postal_code AS postal_code,
                   ch.evidence
              FROM invoice_lines il
              JOIN invoices i ON i.id = il.invoice_id
              JOIN charges ch ON ch.id = il.charge_id
         LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
         LEFT JOIN shipments s ON s.id = ch.shipment_id
         LEFT JOIN cartons ct ON ct.shipment_id = s.id
         LEFT JOIN orders o ON o.id = ch.order_id
         LEFT JOIN channels cn ON cn.id = o.channel_id
             WHERE i.id = ?::uuid
               AND ch.status::text != 'VOID'
             ORDER BY s.shipment_no, ct.carton_no, ci.code
            """, id);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("成本账单明细");
            CellStyle hdr = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true);
            hdr.setFont(f);
            hdr.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hdr.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                "账单日期", "主单号", "子单号", "产品", "费用", "费用名称", "费用明细",
                "账单计费重", "客户预报重", "目的地邮编", "分区", "预报尺寸", "账单尺寸"
            };
            Row r0 = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = r0.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hdr);
                sh.setColumnWidth(i, 4400);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row rr = sh.createRow(r++);
                setStr(rr, 0, str(row.get("bill_date")));
                setStr(rr, 1, str(row.get("master_no")));
                setStr(rr, 2, str(row.get("child_no")));
                setStr(rr, 3, str(row.get("channel_name")));
                setNum(rr, 4, row.get("amount"));
                setStr(rr, 5, str(row.get("fee_name")));
                setStr(rr, 6, str(row.get("fee_code")));
                setNum(rr, 7, row.get("billed_weight"));
                setNum(rr, 8, row.get("reported_weight"));
                setStr(rr, 9, str(row.get("postal_code")));
                setStr(rr, 10, "");  // 分区 (Zone) — 未来从 evidence 抽
                setStr(rr, 11, ""); setStr(rr, 12, "");
            }

            wb.write(out);
            String fn = "成本账单明细-" + invoice.get("invoice_no") + ".xlsx";
            return wrap(out.toByteArray(), fn);
        }
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String formatDateCompact(Object o) {
        if (o == null) return "";
        String s = o.toString().replace("-", "").replace(" ", "").trim();
        if (s.length() >= 8) return s.substring(0, 8);
        return s;
    }

    private static void setStr(Row row, int col, String v) {
        Cell c = row.createCell(col); c.setCellValue(v == null ? "" : v);
    }

    private static void setNum(Row row, int col, Object v) {
        Cell c = row.createCell(col);
        if (v == null) { c.setBlank(); return; }
        if (v instanceof Number n) { c.setCellValue(n.doubleValue()); return; }
        try { c.setCellValue(new BigDecimal(v.toString()).doubleValue()); }
        catch (Exception e) { c.setCellValue(v.toString()); }
    }

    private ResponseEntity<byte[]> wrap(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String enc = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + enc);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
