package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 客服中心 → 收货 → DWS 实物分拣对接。
 *
 * 单一 endpoint：POST /api/acc/dws
 *   action=check  → 校验箱号在制单的 cartons 表里是否登记
 *   action=pickup → 写入实测重量/尺寸/照片，落库 acc_dws_scans
 *   action=update → 二次更新（修正测量）
 *
 * Token 校验：md5(secret + timestamp)，secret 在 app config 里。
 *
 * 返回格式严格遵循 /Users/chaowang/新航线/实物分拣 DWS对接接口-系统.txt：
 *   {status:1|0, info, options:[{label,value}], voice_text}
 */
@RestController
@RequestMapping("/api/acc/dws")
public class AccDwsController {

    /** DWS 对接密钥（实物分拣 DWS对接接口-系统.txt）。生产建议挪到 application.yml。 */
    private static final String DWS_SECRET = "mei432qiao765wu168mnjsio";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccDwsController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostMapping
    public Map<String, Object> dispatch(@RequestBody Map<String, Object> body) {
        String action = str(body.get("action"));
        String token = str(body.get("token"));
        String time = str(body.get("time"));

        if (!validToken(time, token)) {
            return fail("Token 校验失败");
        }

        // DWS 走自己的 md5 token 不带 JWT principal，手动设 tenant 上下文（单租户）
        setSingleTenantContext();

        return switch (action) {
            case "check" -> doCheck(body);
            case "pickup", "update" -> doPickup(body, action);
            default -> fail("不支持的 action: " + action);
        };
    }

    /** 单租户：根据 tenant code 'xqt' 解析 tenant_id 并设入 session 变量，供后续 INSERT 用。 */
    private void setSingleTenantContext() {
        try {
            String tenantId = jdbc.queryForObject(
                "SELECT id::text FROM tenants WHERE code = 'xqt' LIMIT 1", String.class);
            if (tenantId != null) {
                jdbc.queryForObject(
                    "SELECT set_config('app.current_tenant_id', ?, true)",
                    String.class, tenantId);
            }
        } catch (DataAccessException ignored) {}
    }

    // ───── check：根据箱号 + 运单号在 cartons 里查信息 ─────
    private Map<String, Object> doCheck(Map<String, Object> body) {
        String itemNo = str(body.get("item_number"));
        String shipmentNo = str(body.get("shipment_number"));
        if (itemNo == null || itemNo.isBlank()) return fail("item_number 必填");

        Map<String, Object> meta = lookupCartonMeta(itemNo, shipmentNo);
        if (meta == null) {
            // 记一笔 NOT_FOUND 流水方便后续对账
            insertScan(itemNo, shipmentNo, null, null, "check", null, null, null, null,
                "NOT_FOUND", "箱号未找到", body);
            return fail("箱号未在系统中登记: " + itemNo);
        }
        insertScan(itemNo, shipmentNo,
            (String) meta.get("shipment_id"), (String) meta.get("carton_id"),
            "check", null, null, null, null, "OK", null, body);

        return ok("检查成功", buildOptions(meta, null, null));
    }

    // ───── pickup / update：落库 acc_dws_scans 并计算计费重 ─────
    private Map<String, Object> doPickup(Map<String, Object> body, String action) {
        String itemNo = str(body.get("item_number"));
        String shipmentNo = str(body.get("shipment_number"));
        if (itemNo == null || itemNo.isBlank()) return fail("item_number 必填");

        BigDecimal weight = num(body.get("weight"));
        BigDecimal length = num(body.get("length"));
        BigDecimal width  = num(body.get("width"));
        BigDecimal height = num(body.get("height"));

        BigDecimal volumeWeight = (length != null && width != null && height != null)
            ? length.multiply(width).multiply(height)
                .divide(new BigDecimal("6000"), 3, RoundingMode.HALF_UP)
            : null;
        BigDecimal chargeable = (weight != null && volumeWeight != null)
            ? weight.max(volumeWeight)
            : weight;

        String picUrl = str(body.get("pic_url"));
        // base64 大字段不落库（避免行膨胀），存到 raw_payload 即可

        Map<String, Object> meta = lookupCartonMeta(itemNo, shipmentNo);
        if (meta == null) {
            insertScan(itemNo, shipmentNo, null, null, action,
                weight, length, width, height, "NOT_FOUND", "箱号未登记", body);
            return fail("箱号未在系统中登记: " + itemNo);
        }

        // 校验是否已经有 pickup 流水（防重复）
        Long existing = jdbc.queryForObject(
            "SELECT count(*) FROM acc_dws_scans"
            + " WHERE tenant_id = current_setting('app.current_tenant_id')::uuid"
            + "   AND item_number = ? AND action IN ('pickup','update')",
            Long.class, itemNo);
        if ("pickup".equals(action) && existing != null && existing > 0) {
            return fail("箱号已扫过，请使用 action=update 修正");
        }

        // 用计算后的 chargeable 写入流水
        insertScanWithMeasure(itemNo, shipmentNo,
            (String) meta.get("shipment_id"), (String) meta.get("carton_id"),
            action, weight, length, width, height, volumeWeight, chargeable,
            picUrl, "OK", null, body);

        // 当前件数 / 总箱数
        Long currentCount = jdbc.queryForObject(
            "SELECT count(DISTINCT item_number) FROM acc_dws_scans"
            + " WHERE shipment_id = ?::uuid AND action IN ('pickup','update') AND status='OK'",
            Long.class, meta.get("shipment_id"));
        Integer totalBoxes = (Integer) meta.get("total_boxes");
        String pieces = currentCount + "/" + (totalBoxes == null ? "?" : totalBoxes);

        Map<String, Object> resp = ok("收货成功 " + pieces, buildOptions(meta, pieces, chargeable));
        resp.put("voice_text", "收货成功 " + pieces);
        // 全部收齐则建议停流水线
        if (totalBoxes != null && currentCount >= totalBoxes) {
            resp.put("action", "stop");
            resp.put("voice_text", "运单 " + shipmentNo + " 全部收齐");
        }
        return resp;
    }

    // ───── 私有工具 ─────

    private Map<String, Object> lookupCartonMeta(String cartonNo, String shipmentNo) {
        try {
            String sql =
                "SELECT c.id::text       AS carton_id,"
                + "       s.id::text     AS shipment_id,"
                + "       s.shipment_no,"
                + "       s.destination_country,"
                + "       s.destination_postal_code,"
                + "       cn.name        AS channel_name,"
                + "       cn.code        AS channel_code,"
                + "       (SELECT count(*) FROM cartons WHERE shipment_id = s.id) AS total_boxes"
                + " FROM cartons c"
                + " JOIN shipments s   ON s.id = c.shipment_id"
                + " LEFT JOIN channels cn ON cn.id = s.channel_id"
                + " WHERE c.carton_no = ?"
                + (shipmentNo != null && !shipmentNo.isBlank() ? " AND s.shipment_no = ?" : "")
                + " LIMIT 1";
            return shipmentNo != null && !shipmentNo.isBlank()
                ? jdbc.queryForMap(sql, cartonNo, shipmentNo)
                : jdbc.queryForMap(sql, cartonNo);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private void insertScan(String itemNo, String shipNo, String shipId, String cartonId,
                            String action, BigDecimal weight, BigDecimal length,
                            BigDecimal width, BigDecimal height,
                            String status, String info, Map<String, Object> raw) {
        insertScanWithMeasure(itemNo, shipNo, shipId, cartonId, action,
            weight, length, width, height, null, null, null, status, info, raw);
    }

    private void insertScanWithMeasure(String itemNo, String shipNo, String shipId, String cartonId,
                                        String action, BigDecimal weight, BigDecimal length,
                                        BigDecimal width, BigDecimal height,
                                        BigDecimal volumeWeight, BigDecimal chargeable,
                                        String picUrl, String status, String info,
                                        Map<String, Object> raw) {
        try {
            jdbc.update(
                "INSERT INTO acc_dws_scans ("
                + " tenant_id, item_number, shipment_number, shipment_id, carton_id,"
                + " action, weight_kg, length_cm, width_cm, height_cm,"
                + " volume_weight, chargeable_kg, pic_url, raw_payload, status, info"
                + ") VALUES ("
                + " current_setting('app.current_tenant_id')::uuid, ?, ?, ?::uuid, ?::uuid,"
                + " ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?"
                + ")",
                itemNo, shipNo, shipId, cartonId,
                action, weight, length, width, height,
                volumeWeight, chargeable, picUrl, json.toJson(raw == null ? Map.of() : raw),
                status, info);
        } catch (DataAccessException ex) {
            // 流水落库失败不影响主响应
        }
    }

    private boolean validToken(String time, String token) {
        if (time == null || token == null || time.isBlank() || token.isBlank()) return false;
        String expected = md5(DWS_SECRET + time);
        return expected.equalsIgnoreCase(token);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static BigDecimal num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }

    private static List<Map<String, Object>> buildOptions(Map<String, Object> meta,
                                                            String pieces, BigDecimal chargeable) {
        List<Map<String, Object>> opts = new ArrayList<>();
        if (pieces != null) opts.add(opt("件数", pieces));
        Object shipNo = meta.get("shipment_no");
        Integer totalBoxes = (Integer) meta.get("total_boxes");
        opts.add(opt("箱号", meta.getOrDefault("carton_id", "")));
        if (totalBoxes != null) opts.add(opt("总箱数", totalBoxes));
        if (shipNo != null) opts.add(opt("运单号", shipNo));
        Object channelName = meta.get("channel_name");
        Object channelCode = meta.get("channel_code");
        if (channelName != null) opts.add(opt("服务", channelName));
        if (channelCode != null) opts.add(opt("服务代码", channelCode));
        Object country = meta.get("destination_country");
        if (country != null) opts.add(opt("国家", country));
        Object zip = meta.get("destination_postal_code");
        if (zip != null) opts.add(opt("邮编", zip));
        if (chargeable != null) opts.add(opt("计费重(kg)", chargeable));
        return opts;
    }

    private static Map<String, Object> opt(String label, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> ok(String info, List<Map<String, Object>> options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 1);
        body.put("info", info == null ? "" : info);
        if (options != null && !options.isEmpty()) body.put("options", options);
        return body;
    }

    private static Map<String, Object> fail(String info) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 0);
        body.put("info", info);
        return body;
    }
}
