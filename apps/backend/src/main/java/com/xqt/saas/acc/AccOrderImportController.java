package com.xqt.saas.acc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ACC 制单中心 → 导入快件 (Import.php)
 *
 *  POST /api/acc/orders/import-excel    Excel/CSV 上传批量建单
 *  GET  /api/acc/orders/import-template 下载 CSV 模板
 *  GET  /api/acc/orders/import-history  导入历史
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrderImportController {

    private static final String[] TEMPLATE_HEADER = {
        "客户编码", "客户单号", "目的国(2字)", "收件人", "收件电话", "收件地址",
        "邮编", "城市", "省/洲", "重量(kg)", "件数", "申报币种", "申报价值",
        "英文品名", "中文品名", "电池代码", "渠道代码", "备注"
    };

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccOrderImportController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 下载 CSV 模板 (UTF-8 BOM + 表头) */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadTemplate() {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", TEMPLATE_HEADER)).append("\n");
        // 一行示例
        sb.append("CUST-001,EX20260601001,US,John Doe,+1-555-1234,123 Main St,07001,Newark,NJ,5.5,1,USD,200,Phone Case,手机壳,UN3481,EU-AIR-UPS,test\n");
        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=utf-8"));
        headers.setContentDispositionFormData("attachment",
            "order-import-template.csv");
        return new ResponseEntity<>(body, headers, 200);
    }

    /** 上传 CSV/Excel 批量建单 */
    @PostMapping("/import-excel")
    @Transactional
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("导入文件必填");
        String name = file.getOriginalFilename();
        // ACC Import.php L308: 请上传正规 xls/csv/txt 文件
        if (name == null || !name.toLowerCase().matches(".*\\.(xls|xlsx|csv|txt)$")) {
            throw ApiException.badRequest("请上传正规的 xls/xlsx/txt/csv 文件");
        }
        long size = file.getSize();
        // 上限 50MB
        if (size > 50L * 1024 * 1024) {
            throw ApiException.badRequest("文件大小不能超过 50MB（当前 " + (size / 1024 / 1024) + " MB）");
        }
        int created = 0, failed = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        // ACC ExpressBatch.php L1573: 文件内单号重复检测（CSV 第 2 列 customerNo）
        java.util.Map<String, Integer> seenNos = new java.util.HashMap<>();
        try (InputStream is = new ByteArrayInputStream(file.getBytes());
             java.io.BufferedReader r = new java.io.BufferedReader(
                 new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String header = r.readLine();
            if (header != null && header.startsWith("﻿")) header = header.substring(1);
            String line;
            int rowIdx = 1;
            while ((line = r.readLine()) != null) {
                rowIdx++;
                if (line.isBlank()) continue;
                String[] cells = splitCsv(line);
                if (cells.length < 18) {
                    failed++;
                    errors.add("第 " + rowIdx + " 行：列数不足 (期望 18 列)");
                    continue;
                }
                // ACC L1573: 单号本批内重复
                String custNo = cells[1].trim();
                if (!custNo.isEmpty()) {
                    Integer prev = seenNos.put(custNo.toLowerCase(), rowIdx);
                    if (prev != null) {
                        failed++;
                        errors.add("第 " + rowIdx + " 行：单号 [" + custNo + "] 与第 " + prev + " 行重复");
                        continue;
                    }
                }
                try {
                    insertOrder(cells);
                    created++;
                } catch (Exception ex) {
                    failed++;
                    errors.add("第 " + rowIdx + " 行：" + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("读取失败：" + ex.getMessage());
        }
        // 落 import 历史
        try {
            jdbc.update(
                "INSERT INTO acc_files ("
                + "  tenant_id, file_name, file_type, mime_type, size_bytes,"
                + "  uploader_name, remark, status, metadata"
                + ") VALUES ("
                + "  (SELECT id FROM tenants WHERE code='xqt' LIMIT 1),"
                + "  ?, 'order_import', ?, ?, 'system', ?, 'ACTIVE',"
                + "  jsonb_build_object('created', ?::int, 'failed', ?::int)"
                + ")",
                name, file.getContentType(), size,
                "导入 " + created + " 条成功 / " + failed + " 失败",
                created, failed);
        } catch (DataAccessException ignored) {}
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("created", created);
        resp.put("failed", failed);
        resp.put("errors", errors);
        return resp;
    }

    private void insertOrder(String[] c) {
        String customerCode = c[0].trim();
        String customerNo = c[1].trim();
        String country = c[2].trim();
        String recipient = c[3].trim();
        String phone = c[4].trim();
        String address = c[5].trim();
        String postcode = c[6].trim();
        String city = c[7].trim();
        String province = c[8].trim();
        BigDecimal weight = parseDec(c[9]);
        Integer pieces = parseInt(c[10]);
        String currency = c[11].trim();
        BigDecimal declared = parseDec(c[12]);
        String matEn = c[13].trim();
        String matCn = c[14].trim();
        String batteryCode = c[15].trim();
        String channelCode = c[16].trim();
        String remark = c[17].trim();

        // 客户 lookup
        String customerId = jdbc.queryForObject(
            "SELECT id::text FROM customers WHERE code = ? LIMIT 1",
            String.class, customerCode);
        if (customerId == null) throw new IllegalArgumentException("客户编码不存在: " + customerCode);

        // 生成 order_no
        String orderNo = "ORD-IMP-" + System.currentTimeMillis() % 1_000_000_000L + "-"
            + Integer.toHexString((int)(Math.random()*0xFFFF));

        Map<String, Object> acc = new LinkedHashMap<>();
        acc.put("country", country);
        acc.put("weight", weight);
        acc.put("piece", pieces);
        acc.put("currency", currency);
        acc.put("declaredValue", declared);
        acc.put("materialsEn", matEn);
        acc.put("materialsCn", matCn);
        acc.put("batteryCode", batteryCode);
        acc.put("product", channelCode);
        acc.put("remark", remark);
        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("consignee", recipient);
        receiver.put("phone", phone);
        receiver.put("address", address);
        receiver.put("postcode", postcode);
        receiver.put("city", city);
        receiver.put("province", province);
        receiver.put("country", country);
        acc.put("receiver", receiver);

        Map<String, Object> metadata = Map.of("acc_compat", acc, "imported", true);

        jdbc.update(
            "INSERT INTO orders ("
            + "  tenant_id, order_no, customer_id, status, source, customer_ref, metadata"
            + ") VALUES ("
            + "  (SELECT id FROM tenants WHERE code='xqt' LIMIT 1),"
            + "  ?, ?::uuid, 'DRAFT', 'LOCAL', ?, ?::jsonb"
            + ")",
            orderNo, customerId, customerNo, json.toJson(metadata));
    }

    /** 导入历史列表 */
    @GetMapping("/import-history")
    public Map<String, Object> history(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_files WHERE file_type='order_import'", Long.class);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text AS id, file_name, mime_type, size_bytes,"
                + "       uploader_name, remark, metadata->>'created' AS created,"
                + "       metadata->>'failed' AS failed, created_at"
                + " FROM acc_files"
                + " WHERE file_type='order_import'"
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                limit, offset);
            return AccPaging.result(rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", r.get("id"));
                o.put("fileName", r.get("file_name"));
                o.put("sizeBytes", r.get("size_bytes"));
                o.put("uploaderName", r.get("uploader_name"));
                o.put("created", r.get("created"));
                o.put("failed", r.get("failed"));
                o.put("remark", r.get("remark"));
                o.put("createdAt", json.value(r.get("created_at")));
                return o;
            }).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private static String[] splitCsv(String line) {
        // 简化 CSV 解析（不处理引号内逗号）
        return line.split(",", -1);
    }

    private static BigDecimal parseDec(String s) {
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; }
    }
    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
}
