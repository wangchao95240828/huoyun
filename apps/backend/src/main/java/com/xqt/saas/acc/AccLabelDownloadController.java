package com.xqt.saas.acc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 端 label 下载（走 Bearer auth）。客户端 customer-api 走
 * {@link com.xqt.saas.labels.LabelController}（HMAC 鉴权），运营/管理人员从
 * /api/acc/orders 列表点"下载面单"则用这里。
 *
 * 两种取法：
 *   GET /api/acc/labels/file/{date}/{hash}.{ext}     直接按存储路径
 *   GET /api/acc/labels/by-tracking?trackingNo=X     按运单号查最近一张
 *   GET /api/acc/labels/by-order/{orderId}           按订单查关联 shipment 最近一张
 */
@RestController
@RequestMapping("/api/acc/labels")
public class AccLabelDownloadController {
    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    public AccLabelDownloadController(
        JdbcTemplate jdbc,
        @Value("${app.labels.storage.root:./files}") String rootDir
    ) {
        this.jdbc = jdbc;
        this.storageRoot = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    /**
     * 直接按 hash 取文件。路径与 LocalFileLabelStorage.publicUrl 一致。
     */
    @GetMapping("/file/{date}/{name}")
    public ResponseEntity<byte[]> download(@PathVariable String date, @PathVariable String name) {
        // 解析 hash + 扩展名（防路径穿越只允许 [a-f0-9]{4,64}\.ext）
        if (!name.matches("^[a-f0-9]{4,64}\\.[a-z0-9]{2,5}$")) {
            throw ApiException.badRequest("invalid label name");
        }
        if (!date.matches("^\\d{8}$")) {
            throw ApiException.badRequest("invalid date");
        }
        Path file = storageRoot.resolve(date).resolve(name).normalize();
        // 防止 normalize 后跳出 storageRoot
        if (!file.startsWith(storageRoot)) {
            throw ApiException.badRequest("path traversal denied");
        }
        return serve(file, name);
    }

    /** 按 carton.tracking_no 取最近一张 label。常用从订单列表行内按钮触发。 */
    @GetMapping("/by-tracking")
    public ResponseEntity<byte[]> byTracking(@RequestParam("trackingNo") String trackingNo) {
        Map<String, Object> row = findLatestByTracking(trackingNo);
        return serveFromRow(row);
    }

    /** 按 order id 取关联 shipment 最近一张 label。 */
    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<byte[]> byOrder(@PathVariable String orderId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                SELECT lf.storage_path, lf.file_ext, lf.tracking_no
                  FROM label_files lf
                  JOIN shipment_order_links sol ON sol.shipment_id = lf.shipment_id
                 WHERE sol.order_id = ?::uuid
                 ORDER BY lf.created_at DESC LIMIT 1
                """, orderId);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("no label for order " + orderId);
        }
        return serveFromRow(row);
    }

    private Map<String, Object> findLatestByTracking(String trackingNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT storage_path, file_ext, tracking_no FROM label_files
             WHERE tracking_no = ?
             ORDER BY created_at DESC LIMIT 1
            """, trackingNo);
        if (rows.isEmpty()) {
            throw ApiException.notFound("no label for tracking " + trackingNo);
        }
        return rows.get(0);
    }

    private ResponseEntity<byte[]> serveFromRow(Map<String, Object> row) {
        String storagePath = (String) row.get("storage_path");
        String ext = (String) row.get("file_ext");
        String trackingNo = (String) row.get("tracking_no");
        Path file = Paths.get(storagePath);
        String downloadName = (trackingNo == null ? "label" : trackingNo) + "." + ext;
        return serve(file, downloadName);
    }

    private ResponseEntity<byte[]> serve(Path file, String filename) {
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("label file not found on disk");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (Exception ex) {
            throw ApiException.badRequest("read failed: " + ex.getMessage());
        }
        MediaType type = filename.toLowerCase().endsWith(".pdf")
            ? MediaType.APPLICATION_PDF
            : MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + filename + "\"")
            .body(bytes);
    }
}
