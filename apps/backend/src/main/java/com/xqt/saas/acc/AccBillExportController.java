package com.xqt.saas.acc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 客户账单 xlsx 导出 — 奥沃星样式
 *   - Sheet 1: 订单列表 (40 列)
 *   - Sheet 2: 货件明细 (32 列, 装箱+申报明细)
 *   - 顶部 2 行: 公司名 (中) + 英文名
 *   - 第 3 行: 表头
 *   - 差异列 = 系统运费 - 应收运费 (修改差额)
 *
 * GET /api/acc/bills/{id}/export-奥沃星样式.xlsx
 */
@RestController
@RequestMapping("/api/acc/bills")
public class AccBillExportController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccBillExportController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/{id}/export-xlsx")
    public ResponseEntity<byte[]> exportInvoice(@PathVariable String id) {
        // 拿账单基本信息
        Map<String, Object> invoice;
        try {
            invoice = jdbc.queryForMap("""
                SELECT ci.id::text, ci.invoice_no, ci.currency, ci.total_amount, ci.invoice_date,
                       c.code AS customer_code, c.name AS customer_name,
                       c.contacts AS contact_name
                  FROM customer_invoices ci
                  JOIN customers c ON c.id = ci.customer_id
                 WHERE ci.id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("找不到账单: " + id);
        }

        // 关联 charges + orders + shipments + cartons (订单列表)
        List<Map<String, Object>> orderRows = jdbc.queryForList("""
            SELECT
              o.order_no                                         AS company_order_no,
              o.customer_ref                                     AS customer_ref,
              o.created_at::date                                 AS the_date,
              cu.name                                            AS customer_name,
              ct.tracking_no                                     AS tracking_no,
              ct.carrier_master_tracking_no                      AS carrier_tracking,
              ch_name.name                                       AS channel_name,
              s.destination_country                              AS country,
              o.metadata #>> '{acc_compat,receiver,address}'     AS address,
              o.metadata #>> '{acc_compat,receiver,name}'        AS recipient,
              o.metadata #>> '{acc_compat,receiver,postcode}'    AS postcode,
              (o.metadata #>> '{acc_compat,piece}')::numeric     AS pieces,
              (o.metadata #>> '{acc_compat,volume}')::numeric    AS volume_cbm,
              (o.metadata #>> '{acc_compat,weight}')::numeric    AS actual_weight,
              (o.metadata #>> '{acc_compat,packageType}')        AS pkg_type,
              -- 系统运费 (来自 evidence 里 prepay_amount 或第一笔 FREIGHT charge)
              (SELECT sum(ch.amount) FROM charges ch
                JOIN charge_items ci2 ON ci2.id = ch.charge_item_id
               WHERE ch.shipment_id = s.id AND ch.side='AR' AND ci2.category='FREIGHT'
                 AND ch.status::text <> 'VOID')                  AS freight_amount,
              -- 应收运费 = 走账单的实际 amount sum
              (SELECT sum(cil.amount) FROM customer_invoice_lines cil
               WHERE cil.invoice_id = ?::uuid
                 AND cil.charge_id IN (SELECT id FROM charges WHERE shipment_id = s.id))
                                                                  AS billed_amount,
              -- 燃油
              (SELECT sum(ch.amount) FROM charges ch
                JOIN charge_items ci3 ON ci3.id = ch.charge_item_id
               WHERE ch.shipment_id = s.id AND ch.side='AR' AND ci3.code='FUEL'
                 AND ch.status::text <> 'VOID')                  AS fuel_amount
            FROM customer_invoice_lines cil
            JOIN charges ch ON ch.id = cil.charge_id
            JOIN orders o ON o.id = ch.order_id
            JOIN shipments s ON s.id = ch.shipment_id
            JOIN customers cu ON cu.id = ch.customer_id
            LEFT JOIN channels ch_name ON ch_name.id = s.channel_id
            LEFT JOIN cartons ct ON ct.shipment_id = s.id
            WHERE cil.invoice_id = ?::uuid
            GROUP BY o.id, cu.name, ct.tracking_no, ct.carrier_master_tracking_no,
                     ch_name.name, s.id
            ORDER BY o.created_at
            """, id, id);

        // 货件明细 (装箱单 / 申报) — DISTINCT order 避免多笔 charges 笛卡尔积重复
        List<Map<String, Object>> detailRows = jdbc.queryForList("""
            WITH billed_orders AS (
              SELECT DISTINCT o.id AS order_id, o.order_no, o.created_at,
                     o.metadata, s.id AS shipment_id, s.destination_country,
                     cu.name AS customer_name, ch_name.name AS channel_name
                FROM customer_invoice_lines cil
                JOIN charges ch ON ch.id = cil.charge_id
                JOIN orders o ON o.id = ch.order_id
                JOIN shipments s ON s.id = ch.shipment_id
                JOIN customers cu ON cu.id = ch.customer_id
                LEFT JOIN channels ch_name ON ch_name.id = s.channel_id
               WHERE cil.invoice_id = ?::uuid
            )
            SELECT
              bo.order_no                                        AS company_order_no,
              bo.created_at::date                                AS the_date,
              bo.customer_name                                   AS customer_name,
              ct.tracking_no                                     AS tracking_no,
              bo.channel_name                                    AS channel_name,
              bo.destination_country                             AS country,
              bo.metadata #>> '{acc_compat,receiver,address}'    AS address,
              bo.metadata #>> '{acc_compat,receiver,name}'       AS recipient,
              bo.metadata #>> '{acc_compat,receiver,postcode}'   AS postcode,
              pkg.no                                             AS pkg_no,
              (pkg.weight)::numeric                              AS pkg_weight,
              (pkg.length)::numeric                              AS pkg_length,
              (pkg.width)::numeric                               AS pkg_width,
              (pkg.height)::numeric                              AS pkg_height,
              pkg.name                                           AS pkg_name_en,
              pkg.cnName                                         AS pkg_name_cn,
              pkg.hsCode                                         AS hs_code,
              pkg.quantity                                       AS quantity,
              pkg.price                                          AS price,
              pkg.material                                       AS material
            FROM billed_orders bo
            LEFT JOIN cartons ct ON ct.shipment_id = bo.shipment_id
            LEFT JOIN LATERAL jsonb_to_recordset(
              bo.metadata #> '{acc_compat,packageList}'
            ) AS pkg(no text, name text, cnName text, weight text, length text, width text, height text,
                     hsCode text, quantity text, price text, material text) ON true
            WHERE pkg.no IS NOT NULL
            ORDER BY bo.created_at, pkg.no
            """, id);

        // 用 POI 生成 xlsx
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle title = wb.createCellStyle();
            Font tFont = wb.createFont();
            tFont.setBold(true);
            tFont.setFontHeightInPoints((short) 14);
            title.setFont(tFont);

            CellStyle header = wb.createCellStyle();
            header.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font hFont = wb.createFont();
            hFont.setBold(true);
            header.setFont(hFont);
            header.setBorderTop(BorderStyle.THIN);
            header.setBorderBottom(BorderStyle.THIN);
            header.setBorderLeft(BorderStyle.THIN);
            header.setBorderRight(BorderStyle.THIN);

            CellStyle border = wb.createCellStyle();
            border.setBorderTop(BorderStyle.THIN);
            border.setBorderBottom(BorderStyle.THIN);
            border.setBorderLeft(BorderStyle.THIN);
            border.setBorderRight(BorderStyle.THIN);

            String customerNameCN = (String) invoice.get("customer_name");
            String currency = String.valueOf(invoice.get("currency"));

            // ────── Sheet 1: 订单列表 ──────
            Sheet sheet1 = wb.createSheet("订单列表");
            // R1 公司名 (中)
            Row r1 = sheet1.createRow(0);
            Cell c1 = r1.createCell(0);
            c1.setCellValue(customerNameCN);
            c1.setCellStyle(title);
            sheet1.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 23));
            // R2 英文
            Row r2 = sheet1.createRow(1);
            r2.createCell(0).setCellValue(safeStr(invoice.get("customer_code")));
            sheet1.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 23));
            // R3 表头
            String[] hdr1 = {
                "序号","客户","日期","公司单号","运单号","转单号","渠道产品","类型","国家",
                "收货地址","收货人","邮编","收货时间","件数","材积","方数","分区","货型",
                "收货实重","计费重","计费单位","币种","系统运费","差异","应收运费","燃油/挂号","合计"
            };
            Row r3 = sheet1.createRow(2);
            for (int i = 0; i < hdr1.length; i++) {
                Cell c = r3.createCell(i);
                c.setCellValue(hdr1[i]);
                c.setCellStyle(header);
            }
            // R4+ data
            int rowIdx = 3;
            int seq = 1;
            BigDecimal totalDiff = BigDecimal.ZERO;
            for (Map<String, Object> r : orderRows) {
                Row row = sheet1.createRow(rowIdx++);
                BigDecimal sysF = asBd(r.get("freight_amount"));
                BigDecimal billed = asBd(r.get("billed_amount"));
                BigDecimal diff = billed.subtract(sysF);
                totalDiff = totalDiff.add(diff);
                BigDecimal fuel = asBd(r.get("fuel_amount"));
                BigDecimal total = billed.add(fuel);

                setCell(row, 0, seq++, border);
                setCell(row, 1, safeStr(r.get("customer_name")), border);
                setCell(row, 2, safeStr(r.get("the_date")), border);
                setCell(row, 3, safeStr(r.get("company_order_no")), border);
                setCell(row, 4, safeStr(r.get("tracking_no")), border);
                setCell(row, 5, safeStr(r.get("carrier_tracking")), border);
                setCell(row, 6, safeStr(r.get("channel_name")), border);
                setCell(row, 7, safeStr(r.get("pkg_type")), border);
                setCell(row, 8, safeStr(r.get("country")), border);
                setCell(row, 9, safeStr(r.get("address")), border);
                setCell(row, 10, safeStr(r.get("recipient")), border);
                setCell(row, 11, safeStr(r.get("postcode")), border);
                setCell(row, 12, "", border);  // 收货时间 (tracking events)
                setCell(row, 13, asBd(r.get("pieces")).intValue(), border);
                setCell(row, 14, asBd(r.get("volume_cbm")).multiply(new BigDecimal("1000000")).doubleValue(), border);
                setCell(row, 15, asBd(r.get("volume_cbm")).doubleValue(), border);
                setCell(row, 16, "", border);  // 分区
                setCell(row, 17, "", border);  // 货型
                setCell(row, 18, asBd(r.get("actual_weight")).doubleValue(), border);
                setCell(row, 19, asBd(r.get("actual_weight")).doubleValue(), border);
                setCell(row, 20, "千克", border);
                setCell(row, 21, currency, border);
                setCell(row, 22, sysF.doubleValue(), border);
                setCell(row, 23, diff.doubleValue(), border);
                setCell(row, 24, billed.doubleValue(), border);
                setCell(row, 25, fuel.doubleValue(), border);
                setCell(row, 26, total.doubleValue(), border);
            }
            // 合计行
            Row sumRow = sheet1.createRow(rowIdx);
            setCell(sumRow, 0, "合计", header);
            setCell(sumRow, 22, (Object) null, header);
            setCell(sumRow, 23, totalDiff.doubleValue(), header);

            // 列宽
            int[] widths1 = {6,18,12,16,18,18,14,8,8,30,12,10,12,8,8,8,8,8,10,10,10,8,12,10,12,12,12};
            for (int i = 0; i < widths1.length; i++) sheet1.setColumnWidth(i, widths1[i] * 256);

            // ────── Sheet 2: 货件明细 ──────
            Sheet sheet2 = wb.createSheet("货件明细");
            Row s2r1 = sheet2.createRow(0);
            s2r1.createCell(0).setCellValue(customerNameCN);
            s2r1.getCell(0).setCellStyle(title);
            sheet2.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 18));
            Row s2r2 = sheet2.createRow(1);
            s2r2.createCell(0).setCellValue(safeStr(invoice.get("customer_code")));
            sheet2.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 18));
            String[] hdr2 = {
                "序号","客户","日期","公司单号","运单号","渠道产品","类型","国家",
                "收货地址","收货人","邮编","装箱单号","转单号","货件重量","长度","宽度","高度",
                "英文品名","中文品名","海关编码","数量","价格","材质"
            };
            Row s2r3 = sheet2.createRow(2);
            for (int i = 0; i < hdr2.length; i++) {
                Cell c = s2r3.createCell(i);
                c.setCellValue(hdr2[i]);
                c.setCellStyle(header);
            }
            int rowIdx2 = 3, seq2 = 1;
            for (Map<String, Object> r : detailRows) {
                Row row = sheet2.createRow(rowIdx2++);
                setCell(row, 0, seq2++, border);
                setCell(row, 1, safeStr(r.get("customer_name")), border);
                setCell(row, 2, safeStr(r.get("the_date")), border);
                setCell(row, 3, safeStr(r.get("company_order_no")), border);
                setCell(row, 4, safeStr(r.get("tracking_no")), border);
                setCell(row, 5, safeStr(r.get("channel_name")), border);
                setCell(row, 6, "包裹", border);
                setCell(row, 7, safeStr(r.get("country")), border);
                setCell(row, 8, safeStr(r.get("address")), border);
                setCell(row, 9, safeStr(r.get("recipient")), border);
                setCell(row, 10, safeStr(r.get("postcode")), border);
                setCell(row, 11, safeStr(r.get("pkg_no")), border);
                setCell(row, 12, safeStr(r.get("tracking_no")), border);
                setCell(row, 13, asBd(r.get("pkg_weight")).doubleValue(), border);
                setCell(row, 14, asBd(r.get("pkg_length")).doubleValue(), border);
                setCell(row, 15, asBd(r.get("pkg_width")).doubleValue(), border);
                setCell(row, 16, asBd(r.get("pkg_height")).doubleValue(), border);
                setCell(row, 17, safeStr(r.get("pkg_name_en")), border);
                setCell(row, 18, safeStr(r.get("pkg_name_cn")), border);
                setCell(row, 19, safeStr(r.get("hs_code")), border);
                setCell(row, 20, safeStr(r.get("quantity")), border);
                setCell(row, 21, safeStr(r.get("price")), border);
                setCell(row, 22, safeStr(r.get("material")), border);
            }
            int[] widths2 = {6,16,12,16,18,14,8,8,30,12,10,12,18,10,8,8,8,16,14,14,8,8,14};
            for (int i = 0; i < widths2.length; i++) sheet2.setColumnWidth(i, widths2[i] * 256);

            wb.write(out);
            byte[] bytes = out.toByteArray();

            String fileName = (customerNameCN == null ? "账单" : customerNameCN) + "_" +
                LocalDate.now().toString() + ".xlsx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.add("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (IOException ex) {
            throw ApiException.badRequest("xlsx 生成失败: " + ex.getMessage());
        }
    }

    private static String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
    private static BigDecimal asBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception ex) { return BigDecimal.ZERO; }
    }
    private static void setCell(Row row, int col, Object val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val == null) { c.setBlank(); }
        else if (val instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(val.toString());
        if (style != null) c.setCellStyle(style);
    }
}
