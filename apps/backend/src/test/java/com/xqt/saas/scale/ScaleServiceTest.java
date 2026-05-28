package com.xqt.saas.scale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.JsonSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 电子秤设备接口测试：hid 鉴权 / 字段校验 / 幂等落库（对齐 Goodscan 错误码）。
 */
class ScaleServiceTest {
    private JdbcTemplate jdbc;
    private ScaleService scale;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        scale = new ScaleService(jdbc, new JsonSupport(new ObjectMapper()));
        // enableServiceRole 的 set_config 调用
        when(jdbc.queryForObject(contains("set_config"), eq(String.class))).thenReturn("true");
    }

    private void deviceExists(String hid) {
        when(jdbc.queryForMap(contains("FROM scale_devices"), eq("GOODSCAN-DEMO"))).thenReturn(Map.of(
            "id", "dev-1", "tenant_id", "tenant-1", "hid", hid, "active", true));
    }

    private String body(String hid, String code, Object l, Object w, Object h, Object weight) {
        return String.format(
            "{\"hid\":\"%s\",\"code\":\"%s\",\"L\":%s,\"W\":%s,\"H\":%s,\"weight\":%s,\"time\":\"2026-05-28 10:00:00\"}",
            hid, code, l, w, h, weight);
    }

    // 1. 正常称重 → 落库 + 返回 code 0
    @Test
    void receiveSuccessInsertsRecord() {
        deviceExists("HID-DEMO-001");
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO",
            body("HID-DEMO-001", "PKG001", 300, 200, 150, 2.5));

        assertThat(out.get("code")).isEqualTo(0);
        assertThat(out.get("error")).isEqualTo("upload success");
        verify(jdbc, times(1)).update(contains("INSERT INTO scale_records"),
            eq("tenant-1"), eq("dev-1"), eq("PKG001"),
            any(), any(), any(), any(), any(), any());
    }

    // 2. hid 不匹配 → code 4，不落库
    @Test
    void receiveRejectsWrongHid() {
        deviceExists("HID-DEMO-001");
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO",
            body("WRONG-HID", "PKG001", 300, 200, 150, 2.5));

        assertThat(out.get("code")).isEqualTo(4);
        verify(jdbc, never()).update(contains("INSERT INTO scale_records"), (Object[]) any());
    }

    // 3. 设备不存在 → code 99
    @Test
    void receiveRejectsUnknownDevice() {
        when(jdbc.queryForMap(contains("FROM scale_devices"), eq("NOPE")))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        Map<String, Object> out = scale.receive("NOPE",
            body("HID-DEMO-001", "PKG001", 300, 200, 150, 2.5));
        assertThat(out.get("code")).isEqualTo(99);
    }

    // 4. weight <= 0 → code 13
    @Test
    void receiveRejectsNonPositiveWeight() {
        deviceExists("HID-DEMO-001");
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO",
            body("HID-DEMO-001", "PKG001", 300, 200, 150, 0));
        assertThat(out.get("code")).isEqualTo(13);
        verify(jdbc, never()).update(contains("INSERT INTO scale_records"), (Object[]) any());
    }

    // 5. L 缺失 → code 6
    @Test
    void receiveRejectsMissingL() {
        deviceExists("HID-DEMO-001");
        String body = "{\"hid\":\"HID-DEMO-001\",\"code\":\"PKG001\",\"W\":200,\"H\":150,\"weight\":2.5}";
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO", body);
        assertThat(out.get("code")).isEqualTo(6);
    }

    // 6. code（装箱单号）缺失 → code 5
    @Test
    void receiveRejectsMissingCode() {
        deviceExists("HID-DEMO-001");
        String body = "{\"hid\":\"HID-DEMO-001\",\"code\":\"\",\"L\":300,\"W\":200,\"H\":150,\"weight\":2.5}";
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO", body);
        assertThat(out.get("code")).isEqualTo(5);
    }

    // 7. 空报文 → code 1
    @Test
    void receiveRejectsEmptyBody() {
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO", "");
        assertThat(out.get("code")).isEqualTo(1);
    }

    // 8. 非法 JSON → code 2
    @Test
    void receiveRejectsBadJson() {
        Map<String, Object> out = scale.receive("GOODSCAN-DEMO", "not-json{");
        assertThat(out.get("code")).isEqualTo(2);
    }

    // 9. INSERT 用 ON CONFLICT DO NOTHING 实现幂等
    @Test
    void receiveUsesIdempotentInsert() {
        deviceExists("HID-DEMO-001");
        scale.receive("GOODSCAN-DEMO", body("HID-DEMO-001", "PKG001", 300, 200, 150, 2.5));
        verify(jdbc, times(1)).update(
            contains("ON CONFLICT (tenant_id, hash) DO NOTHING"),
            eq("tenant-1"), eq("dev-1"), eq("PKG001"),
            any(), any(), any(), any(), any(), any());
    }
}
