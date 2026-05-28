package com.xqt.saas.scale;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复刻 ACC acc/api/Scale.php + inc/scale/Goodscan.php 电子秤设备接口。
 *
 * 设备 POST 称重报文（JSON）：
 *   { "hid": 设备编码, "code": 装箱单号, "L": 长(mm), "W": 宽, "H": 高,
 *     "weight": 重量, "picture": base64(可空), "time": 时间 }
 *
 * 流程（对齐 Goodscan.doReceive）：
 *   1. 按 pluginCode 找设备配置（设备无 JWT，用 service role 绕 RLS 查询）
 *   2. 校验 hid 匹配 + L/W/H/weight 为正数 + code 非空
 *   3. md5(原始报文) 作幂等 hash，重复报文直接返回成功（幂等）
 *   4. 落 scale_records，按 tenant 归属
 *
 * 返回保持 Goodscan 格式：{ "code": 0, "error": "upload success" }，
 * 错误时 code 非 0（设备端按 code 判定），HTTP 始终 200。
 */
@Service
public class ScaleService {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public ScaleService(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receive(String pluginCode, String rawBody) {
        enableServiceRole();
        if (rawBody == null || rawBody.isBlank()) {
            return result(1, "数据为空！");
        }
        Map<String, Object> data;
        try {
            data = json.fromJson(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException ex) {
            return result(2, "JSON数据解析失败！");
        }
        if (data == null) {
            return result(2, "JSON数据解析失败！");
        }

        Map<String, Object> device = findDevice(pluginCode);
        if (device == null) {
            return result(99, "找不到该电子称接口！");
        }

        // ─── 校验（对齐 Goodscan 错误码）───
        String hid = str(data.get("hid"));
        if (hid.isBlank()) return result(3, "找不到hid值！");
        if (!hid.equals(device.get("hid"))) {
            return result(4, "系统编码匹配失败，请确认插件填写的设备编码正确。");
        }
        String code = str(data.get("code"));
        if (code.isBlank()) return result(5, "找不到code值！");
        BigDecimal l = num(data.get("L"));
        if (l == null) return result(6, "找不到L值！");
        if (l.signum() <= 0) return result(7, "L值的数值不正确！");
        BigDecimal w = num(data.get("W"));
        if (w == null) return result(8, "找不到W值！");
        if (w.signum() <= 0) return result(9, "W值的数值不正确！");
        BigDecimal h = num(data.get("H"));
        if (h == null) return result(10, "找不到H值！");
        if (h.signum() <= 0) return result(11, "H值的数值不正确！");
        BigDecimal weight = num(data.get("weight"));
        if (weight == null) return result(12, "找不到weight值！");
        if (weight.signum() <= 0) return result(13, "weight值的数值不正确！");

        String tenantId = (String) device.get("tenant_id");
        String deviceId = (String) device.get("id");
        boolean hasPicture = !str(data.get("picture")).isBlank();
        String hash = md5(rawBody);

        // 幂等：相同报文 hash 已存在则直接成功（ON CONFLICT DO NOTHING）
        jdbc.update("""
            INSERT INTO scale_records (
              tenant_id, device_id, no, weight, length_mm, width_mm, height_mm,
              has_picture, hash
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, hash) DO NOTHING
            """, tenantId, deviceId, code, weight, l, w, h, hasPicture, hash);

        return result(0, "upload success");
    }

    /** 对应 Goodscan.readData：列未签入（未关联运单）称重记录。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> readUnbound(String pluginCode, String hid) {
        enableServiceRole();
        Map<String, Object> device = findDevice(pluginCode);
        if (device == null || !hid.equals(device.get("hid"))) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, no, weight, length_mm, width_mm, height_mm
            FROM scale_records
            WHERE device_id = ?::uuid AND shipment_id IS NULL
            ORDER BY received_at
            """, device.get("id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.get("id"));
            o.put("No", r.get("no"));
            o.put("weight", r.get("weight"));
            // 长宽高 mm → cm 向上取整（对齐 Goodscan readData ceil(/10)）
            o.put("length", ceilCm(r.get("length_mm")));
            o.put("width", ceilCm(r.get("width_mm")));
            o.put("height", ceilCm(r.get("height_mm")));
            out.add(o);
        }
        return out;
    }

    private Map<String, Object> findDevice(String pluginCode) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, tenant_id::text AS tenant_id, hid, active
                FROM scale_devices WHERE plugin_code = ? AND active = true
                """, pluginCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void enableServiceRole() {
        jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
    }

    private Map<String, Object> result(int code, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("error", error);
        return m;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static BigDecimal num(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long ceilCm(Object mm) {
        BigDecimal v = num(mm);
        if (v == null) return 0;
        return (long) Math.ceil(v.doubleValue() / 10.0);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
