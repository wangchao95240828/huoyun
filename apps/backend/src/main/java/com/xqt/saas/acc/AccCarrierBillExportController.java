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
 * 承运商账单 FedEx-B 价账单风格 xlsx 导出 (18 列).
 *
 * GET /api/acc/partner-invoices/{id}/export-fedex-style
 *
 * 18 列 (对齐 FedEx-B 价账单.xls):
 *   1. Shipment Date           YYYYMMDD
 *   2. Original Customer Reference  客户单号
 *   3. Express or Ground Tracking ID  子单号
 *   4. TDMasterTrackingID      主单号
 *   5. Recipient State         州
 *   6. Recipient Zip Code      邮编
 *   7. Zone Code               区码
 *   8. Rated Weight Amount     计费重 lb
 *   9. 收费备注                 Performance Pricing / Discount
 *   10. 单价运费                单 lb 价
 *   11. 单件运费                单件运费
 *   12. 附加费                  surcharge
 *   13. 燃油                    fuel
 *   14. 总运费                  合计
 *   15. 实收运费(含佣金)        我们向客户收的总额
 *   16. 附加费备注              说明
 *   17. Multiweight Total Shipment Weight  Master 件总重
 *   18. 补收重量                我们补称差异
 *
 * 也支持按 partner_code + currency 直导(全量 vs 单 invoice):
 *   GET /api/acc/partner-invoices/export-fedex-style?partnerCode=UPS&currency=USD&dateFrom=&dateTo=
 */
@RestController
public class AccCarrierBillExportController {
    private final JdbcTemplate jdbc;

    public AccCarrierBillExportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/acc/partner-invoices/{id}/export-fedex-style")
    public ResponseEntity<byte[]> exportInvoice(@PathVariable String id) throws IOException {
        // 单一 invoice 导出
        List<Map<String, Object>> rows = jdbc.queryForList(buildSql(true), id);
        String invoiceNo;
        try {
            invoiceNo = jdbc.queryForObject(
                "SELECT invoice_no FROM partner_invoices WHERE id = ?::uuid", String.class, id);
        } catch (Exception e) { invoiceNo = "PARTNER-INV"; }
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        return generateXlsx(rows, "FedEx-B价账单-" + invoiceNo + ".xlsx");
    }

    @GetMapping("/api/acc/partner-invoices/export-fedex-style")
    public ResponseEntity<byte[]> exportByFilter(
        @RequestParam(required = false) String partnerCode,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) throws IOException {
        StringBuilder sql = new StringBuilder(buildSql(false));
        List<Object> args = new java.util.ArrayList<>();
        sql.append(" AND 1=1 ");
        if (partnerCode != null && !partnerCode.isBlank()) {
            sql.append(" AND pi.partner_code = ?");
            args.add(partnerCode);
        }
        if (currency != null && !currency.isBlank()) {
            sql.append(" AND pi.currency = ?");
            args.add(currency);
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            sql.append(" AND pi.invoice_date >= ?::date");
            args.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isBlank()) {
            sql.append(" AND pi.invoice_date <= ?::date");
            args.add(dateTo);
        }
        sql.append(" ORDER BY pi.invoice_date, pil.line_no LIMIT 10000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        String fn = "FedEx-B价账单-" + (partnerCode != null ? partnerCode : "ALL") + ".xlsx";
        return generateXlsx(rows, fn);
    }

    /**
     * SQL 构造: cartons + shipments + orders + partner_invoice_lines JOIN.
     */
    private String buildSql(boolean byInvoiceId) {
        String where = byInvoiceId ? "WHERE pi.id = ?::uuid" : "WHERE pi.id IS NOT NULL";
        return """
            SELECT
              pi.invoice_no,
              pi.invoice_date,
              pi.partner_code,
              pi.currency,
              -- carton/shipment 关联
              ct.carton_no                                              AS tracking_id,
              s.shipment_no                                             AS master_tracking_id,
              s.customer_ref                                            AS customer_ref,
              s.destination_country                                     AS recipient_country,
              s.metadata #>> '{acc_compat,receiver,state}'              AS recipient_state,
              s.metadata #>> '{acc_compat,receiver,postcode}'           AS recipient_zip,
              s.metadata #>> '{acc_compat,zone_code}'                   AS zone_code,
              -- 计费重 (kg → lb, 1 kg ≈ 2.205 lb)
              COALESCE(ct.chargeable_weight_kg, ct.weight_kg, 0) * 2.205  AS rated_weight_lb,
              -- 拆账单 line: 走 partner_invoice_lines
              pil.description                                           AS line_description,
              pil.amount                                                AS line_amount,
              pil.tax_amount                                            AS tax_amount,
              pil.metadata->>'unit_rate'                                AS unit_rate,
              pil.metadata->>'category'                                 AS category,
              -- 我们对客户的应收 (含佣金) — 取该 carton/shipment 的 AR charges 合计
              (SELECT COALESCE(SUM(ch.amount), 0)
                 FROM charges ch
                WHERE ch.shipment_id = s.id AND ch.side='AR'
                  AND ch.status::text <> 'VOID')                        AS ar_total,
              -- 补收差异 (实际 vs 预报)
              (COALESCE(ct.chargeable_weight_kg, 0) - COALESCE(ct.weight_kg, 0))  AS weight_diff
            FROM partner_invoices pi
            JOIN partner_invoice_lines pil ON pil.invoice_id = pi.id
       LEFT JOIN cartons ct ON ct.id = pil.carton_id
       LEFT JOIN shipments s ON s.id = COALESCE(pil.shipment_id, ct.shipment_id)
            """ + " " + where + " ";
    }

    private ResponseEntity<byte[]> generateXlsx(List<Map<String, Object>> rows, String filename) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("FedEx-B价账单");
            CellStyle hdr = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true);
            hdr.setFont(f);
            hdr.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hdr.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hdr.setBorderTop(BorderStyle.THIN); hdr.setBorderBottom(BorderStyle.THIN);
            hdr.setBorderLeft(BorderStyle.THIN); hdr.setBorderRight(BorderStyle.THIN);

            String[] headers = {
                "Shipment Date", "Original Customer Reference", "Express or Ground Tracking ID",
                "TDMasterTrackingID", "Recipient State", "Recipient Zip Code",
                "Zone Code", "Rated Weight Amount", "收费备注",
                "单价运费", "单件运费", "附加费", "燃油", "总运费",
                "实收运费(含佣金)", "附加费备注",
                "Multiweight Total Shipment Weight", "补收重量"
            };
            Row r0 = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = r0.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hdr);
                sh.setColumnWidth(i, 4400);
            }

            // 按 shipment 累计 (用于 Multiweight 列)
            java.util.Map<String, BigDecimal> shipmentWeight = new java.util.HashMap<>();
            for (Map<String, Object> row : rows) {
                String key = str(row.get("master_tracking_id"));
                shipmentWeight.merge(key, asBd(row.get("rated_weight_lb")), BigDecimal::add);
            }

            int r = 1;
            for (Map<String, Object> row : rows) {
                Row rr = sh.createRow(r++);
                String cat = str(row.get("category"));
                BigDecimal amt = asBd(row.get("line_amount"));
                BigDecimal tax = asBd(row.get("tax_amount"));

                // 按 category 拆 列 10-13: 单价/单件/附加/燃油
                BigDecimal unitRate = asBd(row.get("unit_rate"));
                BigDecimal freight = BigDecimal.ZERO;
                BigDecimal surcharge = BigDecimal.ZERO;
                BigDecimal fuel = BigDecimal.ZERO;
                if ("FUEL".equalsIgnoreCase(cat) || "FUEL_SURCHARGE".equalsIgnoreCase(cat)) {
                    fuel = amt;
                } else if (cat != null && (cat.contains("SURCHARGE") || cat.contains("ADDITIONAL"))) {
                    surcharge = amt;
                } else {
                    freight = amt;
                }
                BigDecimal total = amt.add(tax);

                setStr(rr, 0, formatDateCompact(row.get("invoice_date")));
                setStr(rr, 1, str(row.get("customer_ref")));
                setStr(rr, 2, str(row.get("tracking_id")));
                setStr(rr, 3, str(row.get("master_tracking_id")));
                setStr(rr, 4, str(row.get("recipient_state")));
                setStr(rr, 5, str(row.get("recipient_zip")));
                setStr(rr, 6, str(row.get("zone_code")));
                setNum(rr, 7, row.get("rated_weight_lb"));
                setStr(rr, 8, str(row.get("line_description")));
                setNum(rr, 9, unitRate);
                setNum(rr, 10, freight);
                setNum(rr, 11, surcharge);
                setNum(rr, 12, fuel);
                setNum(rr, 13, total);
                setNum(rr, 14, row.get("ar_total"));
                setStr(rr, 15, "");  // 附加费备注
                setNum(rr, 16, shipmentWeight.getOrDefault(str(row.get("master_tracking_id")), BigDecimal.ZERO));
                setNum(rr, 17, row.get("weight_diff"));
            }

            wb.write(out);
            return wrap(out.toByteArray(), filename);
        }
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static BigDecimal asBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

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
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == 0) c.setBlank(); else c.setCellValue(d);
            return;
        }
        try {
            BigDecimal bd = new BigDecimal(v.toString());
            if (bd.signum() == 0) c.setBlank(); else c.setCellValue(bd.doubleValue());
        } catch (Exception e) { c.setCellValue(v.toString()); }
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
