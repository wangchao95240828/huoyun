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

    // ───── check：根据箱号在 acc_inbound_parcels（收货主表）里查信息 ─────
    private Map<String, Object> doCheck(Map<String, Object> body) {
        String itemNo = str(body.get("item_number"));
        String waybillNo = str(body.get("shipment_number"));    // DWS 协议字段名，实际是 waybill_no
        if (itemNo == null || itemNo.isBlank()) return fail("item_number 必填");

        Map<String, Object> meta = lookupInboundParcel(itemNo, waybillNo);
        if (meta == null) {
            insertScan(itemNo, waybillNo, null, null, "check", null, null, null, null,
                "NOT_FOUND", "箱号未在收货系统登记", body);
            return fail("箱号未在系统中登记: " + itemNo);
        }
        insertScan(itemNo, waybillNo, null, (String) meta.get("parcel_id"),
            "check", null, null, null, null, "OK", null, body);
        return ok("检查成功", buildOptions(meta, null, null));
    }

    // ───── pickup / update：落库 acc_dws_scans 并回写 acc_inbound_parcels ─────
    private Map<String, Object> doPickup(Map<String, Object> body, String action) {
        String itemNo = str(body.get("item_number"));
        String waybillNo = str(body.get("shipment_number"));
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

        Map<String, Object> meta = lookupInboundParcel(itemNo, waybillNo);
        if (meta == null) {
            insertScan(itemNo, waybillNo, null, null, action,
                weight, length, width, height, "NOT_FOUND", "箱号未在收货系统登记", body);
            return fail("箱号未在系统中登记: " + itemNo);
        }

        String parcelId = (String) meta.get("parcel_id");

        // 防重复：pickup 已落库的不允许重复，update 不限制
        Long existing = jdbc.queryForObject(
            "SELECT count(*) FROM acc_dws_scans"
            + " WHERE inbound_parcel_id = ?::uuid AND action IN ('pickup','update') AND status='OK'",
            Long.class, parcelId);
        if ("pickup".equals(action) && existing != null && existing > 0) {
            return fail("箱号已扫过，请使用 action=update 修正");
        }

        // 落流水
        insertScanWithMeasure(itemNo, waybillNo, null, parcelId,
            action, weight, length, width, height, volumeWeight, chargeable,
            picUrl, "OK", null, body);

        // 回写收货主表：实测值 + 体积重 + 计费重 + cbm + status='SCANNED'
        syncMeasurementToInboundParcel(parcelId, weight, length, width, height,
            volumeWeight, chargeable, picUrl);

        // 当前同一运单已扫件数（用 waybill_no 统计）
        String pieces;
        if (waybillNo != null && !waybillNo.isBlank()) {
            Long currentCount = jdbc.queryForObject(
                "SELECT count(*) FROM acc_inbound_parcels"
                + " WHERE waybill_no = ? AND status IN ('SCANNED','CHARGED','SHIPPED')",
                Long.class, waybillNo);
            Long totalForWaybill = jdbc.queryForObject(
                "SELECT count(*) FROM acc_inbound_parcels WHERE waybill_no = ?",
                Long.class, waybillNo);
            pieces = currentCount + "/" + totalForWaybill;
        } else {
            pieces = "1/1";
        }

        Map<String, Object> resp = ok("收货成功 " + pieces, buildOptions(meta, pieces, chargeable));
        resp.put("voice_text", "收货成功 " + pieces);

        // 全部箱收齐 → 停流水线
        if (waybillNo != null && !waybillNo.isBlank()) {
            Long remaining = jdbc.queryForObject(
                "SELECT count(*) FROM acc_inbound_parcels"
                + " WHERE waybill_no = ? AND status = 'PENDING'",
                Long.class, waybillNo);
            if (remaining != null && remaining == 0) {
                resp.put("action", "stop");
                resp.put("voice_text", "运单 " + waybillNo + " 全部收齐");
            }
        }
        return resp;
    }

    // ───── 私有工具 ─────

    private Map<String, Object> lookupInboundParcel(String parcelNo, String waybillNo) {
        try {
            String sql =
                "SELECT p.id::text       AS parcel_id,"
                + "       p.parcel_no,"
                + "       p.waybill_no,"
                + "       p.destination_country,"
                + "       p.destination_postal_code,"
                + "       p.zone,"
                + "       cu.name        AS customer_name,"
                + "       cn.name        AS channel_name,"
                + "       cn.code        AS channel_code,"
                + "       (SELECT count(*) FROM acc_inbound_parcels"
                + "          WHERE waybill_no = p.waybill_no AND p.waybill_no IS NOT NULL"
                + "       )              AS total_boxes"
                + " FROM acc_inbound_parcels p"
                + " LEFT JOIN customers cu ON cu.id = p.customer_id"
                + " LEFT JOIN channels  cn ON cn.id = p.channel_id"
                + " WHERE p.parcel_no = ?"
                + (waybillNo != null && !waybillNo.isBlank() ? " AND p.waybill_no = ?" : "")
                + " LIMIT 1";
            return waybillNo != null && !waybillNo.isBlank()
                ? jdbc.queryForMap(sql, parcelNo, waybillNo)
                : jdbc.queryForMap(sql, parcelNo);
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

    /**
     * 回写收货主表 acc_inbound_parcels：实测值覆盖预报值，status → SCANNED。
     *   - actual_weight / length_cm / width_cm / height_cm 用 DWS 值
     *   - chargeable_kg = max(实测重, 体积重)
     *   - cbm = LxWxH/1_000_000
     *   - received_at 第一次扫描时设
     *   - 注：表价计算 (rate_amount) 留给 RateEngine 异步处理，本接口只落实测
     */
    private void syncMeasurementToInboundParcel(String parcelId, BigDecimal weight,
                                                  BigDecimal length, BigDecimal width,
                                                  BigDecimal height, BigDecimal volumeWeight,
                                                  BigDecimal chargeable, String picUrl) {
        if (parcelId == null) return;
        try {
            BigDecimal cbm = null;
            if (length != null && width != null && height != null) {
                cbm = length.multiply(width).multiply(height)
                    .divide(new BigDecimal("1000000"), 4, RoundingMode.HALF_UP);
            }
            jdbc.update(
                "UPDATE acc_inbound_parcels SET"
                + "  actual_weight = coalesce(?, actual_weight),"
                + "  length_cm     = coalesce(?, length_cm),"
                + "  width_cm      = coalesce(?, width_cm),"
                + "  height_cm     = coalesce(?, height_cm),"
                + "  volume_weight = coalesce(?, volume_weight),"
                + "  chargeable_kg = coalesce(?, chargeable_kg),"
                + "  cbm           = coalesce(?, cbm),"
                + "  pic_url       = coalesce(?, pic_url),"
                + "  status        = CASE WHEN status = 'PENDING' THEN 'SCANNED' ELSE status END,"
                + "  received_at   = coalesce(received_at, now()),"
                + "  updated_at    = now()"
                + " WHERE id = ?::uuid",
                weight, length, width, height, volumeWeight, chargeable, cbm, picUrl, parcelId);
        } catch (DataAccessException ex) {
            System.err.println("[AccDwsController] syncMeasurementToInboundParcel failed: " + ex.getMessage());
        }
    }

    private void insertScanWithMeasure(String itemNo, String shipNo, String shipId, String parcelId,
                                        String action, BigDecimal weight, BigDecimal length,
                                        BigDecimal width, BigDecimal height,
                                        BigDecimal volumeWeight, BigDecimal chargeable,
                                        String picUrl, String status, String info,
                                        Map<String, Object> raw) {
        try {
            jdbc.update(
                "INSERT INTO acc_dws_scans ("
                + " tenant_id, item_number, shipment_number, shipment_id, inbound_parcel_id,"
                + " action, weight_kg, length_cm, width_cm, height_cm,"
                + " volume_weight, chargeable_kg, pic_url, raw_payload, status, info"
                + ") VALUES ("
                + " (SELECT id FROM tenants WHERE code='xqt' LIMIT 1),"
                + " ?, ?, ?::uuid, ?::uuid,"
                + " ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?"
                + ")",
                itemNo, shipNo, shipId, parcelId,
                action, weight, length, width, height,
                volumeWeight, chargeable, picUrl, json.toJson(raw == null ? Map.of() : raw),
                status, info);
        } catch (DataAccessException ex) {
            System.err.println("[AccDwsController] insertScan failed: " + ex.getMessage());
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
        Object parcelNo = meta.get("parcel_no");
        Object waybillNo = meta.get("waybill_no");
        Long totalBoxes = meta.get("total_boxes") instanceof Number n ? n.longValue() : null;
        if (parcelNo != null) opts.add(opt("箱号", parcelNo));
        if (totalBoxes != null && totalBoxes > 0) opts.add(opt("总箱数", totalBoxes));
        if (waybillNo != null) opts.add(opt("运单号", waybillNo));
        Object customerName = meta.get("customer_name");
        if (customerName != null) opts.add(opt("客户", customerName));
        Object channelName = meta.get("channel_name");
        Object channelCode = meta.get("channel_code");
        if (channelName != null) opts.add(opt("服务", channelName));
        if (channelCode != null) opts.add(opt("服务代码", channelCode));
        Object country = meta.get("destination_country");
        if (country != null) opts.add(opt("国家", country));
        Object zip = meta.get("destination_postal_code");
        if (zip != null) opts.add(opt("邮编", zip));
        Object zone = meta.get("zone");
        if (zone != null) opts.add(opt("分区", zone));
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
