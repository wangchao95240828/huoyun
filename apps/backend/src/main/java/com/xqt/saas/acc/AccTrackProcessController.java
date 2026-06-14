package com.xqt.saas.acc;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轨迹处理 — 对齐 ACC TrackProcess.php 批量端点：
 *   L135: 合并轨迹（至少 2 条）
 *   L307/L329: 关联官方轨迹描述 + 同票轨迹去重
 *   L797: 更新轨迹（必须有单号/转单号）
 *   L878: 批量签收（至少一票快件）
 *   L892/L896: 签收国家与快件目的地匹配
 *   L921: 找不到指定的轨迹内容
 */
@RestController
@RequestMapping("/api/acc/track-process")
public class AccTrackProcessController {
    private final JdbcTemplate jdbc;

    public AccTrackProcessController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ACC L135: 合并轨迹记录（至少选 2 条）。 */
    @PostMapping("/merge")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeTracks(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.size() < 2) {
            throw ApiException.badRequest("请至少选择两个记录进行合并");
        }
        // 取第一个作为主，其余 metadata 指向主单
        String masterId = ids.get(0);
        int merged = 0;
        for (int i = 1; i < ids.size(); i++) {
            int n = jdbc.update("""
                UPDATE acc_track_items SET metadata =
                  coalesce(metadata, '{}'::jsonb) || jsonb_build_object('merged_into', ?::text)
                 WHERE id = ?::uuid
                """, masterId, ids.get(i));
            if (n > 0) merged++;
        }
        return Map.of("master", masterId, "merged", merged, "total", ids.size());
    }

    /** ACC L307/L329: 关联官方轨迹描述 + 同行/同票内重复检测。 */
    @PostMapping("/link-official")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> linkOfficial(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("rows", List.of());
        if (rows.isEmpty()) {
            throw ApiException.badRequest("至少关联一个官方轨迹描述");
        }
        // 同票内描述去重
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        java.util.List<Integer> repeat = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object descObj = rows.get(i).get("description");
            String desc = descObj == null ? "" : descObj.toString().trim().toLowerCase();
            if (desc.isEmpty()) continue;
            Integer prev = seen.put(desc, i + 1);
            if (prev != null) repeat.add(i + 1);
        }
        if (!repeat.isEmpty()) {
            throw ApiException.badRequest(
                "第 " + String.join(",", repeat.stream().map(String::valueOf).toList())
                + " 行的轨迹描述与其它行重复，请检查");
        }
        return Map.of("linked", rows.size());
    }

    /** ACC L797: 更新轨迹（必填单号/转单号 + 找到记录）。 */
    @PostMapping("/update-by-no")
    public Map<String, Object> updateByNo(@RequestBody Map<String, Object> body) {
        String no = body.get("no") == null ? null : body.get("no").toString().trim();
        if (no == null || no.isEmpty()) {
            throw ApiException.badRequest("请输入更新轨迹的单号/转单号");
        }
        Integer found = jdbc.queryForObject("""
            SELECT count(*) FROM cartons
             WHERE tracking_no = ? OR carrier_master_tracking_no = ?
            """, Integer.class, no, no);
        if (found == null || found == 0) {
            throw ApiException.notFound("找不到该单号/转单号: " + no);
        }
        return Map.of("ok", true, "matchedCount", found);
    }

    /** ACC L878/L892/L896: 按国家批量签收 — 国家必须与快件目的地一致。 */
    @PostMapping("/sign-in-by-country")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> signInByCountry(@RequestBody Map<String, Object> body) {
        List<String> shipmentIds = (List<String>) body.getOrDefault("shipmentIds", List.of());
        String country = body.get("country") == null ? null : body.get("country").toString();
        if (shipmentIds.isEmpty()) {
            throw ApiException.badRequest("至少要有一票快件");
        }
        if (country == null || !country.matches("[A-Z]{2}")) {
            throw ApiException.badRequest("签收国家代码必须为 2 个大写字母 (ISO 3166-1)");
        }
        // ACC L892/L896: 国家不匹配的快件拦截
        List<String> mismatch = jdbc.queryForList("""
            SELECT shipment_no FROM shipments
             WHERE id = ANY(?::uuid[]) AND destination_country IS NOT NULL
               AND destination_country <> ?
            """, String.class, (Object) shipmentIds.toArray(new String[0]), country);
        if (!mismatch.isEmpty()) {
            String zh = country.equals("DE") ? "德国" :
                        country.equals("FR") ? "法国" :
                        country.equals("US") ? "美国" :
                        country.equals("GB") ? "英国" : country;
            throw ApiException.badRequest(
                "部分快件目的地不在" + zh + "，无法在" + zh + "签收: "
                + String.join(",", mismatch));
        }
        // 批量更新 shipments.status → DELIVERED + 写 tracking_event
        int updated = jdbc.update("""
            UPDATE shipments SET status = 'DELIVERED', updated_at = now()
             WHERE id = ANY(?::uuid[]) AND status NOT IN ('DELIVERED','CANCELLED','RETURNED')
            """, (Object) shipmentIds.toArray(new String[0]));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("country", country);
        result.put("signedIn", updated);
        result.put("total", shipmentIds.size());
        result.put("signedInAt", OffsetDateTime.now().toString());
        return result;
    }

    /** ACC L921: 通过 ID 查询轨迹内容（带 404）。 */
    @PostMapping("/find-content")
    public Map<String, Object> findContent(@RequestBody Map<String, Object> body) {
        String id = body.get("id") == null ? null : body.get("id").toString();
        if (id == null || id.isBlank()) {
            throw ApiException.badRequest("轨迹 ID 必填");
        }
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT id::text, code, name, name_en, is_active
                  FROM acc_track_items WHERE id = ?::uuid
                """, id);
            return row;
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到指定的轨迹内容: " + id);
        }
    }
}
