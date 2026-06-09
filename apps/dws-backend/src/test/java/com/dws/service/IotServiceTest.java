package com.dws.service;

import com.dws.dto.IotRequest;
import com.dws.dto.IotResponse;
import com.dws.exception.IotException;
import com.dws.repository.IotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DWS 实物分拣推送接口 - IotService 单元测试
 * <p>
 * 覆盖文档 v2 中 check / pickup / update 三个 action 及鉴权逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DWS IotService 单元测试")
class IotServiceTest {

    /** 与 app.iot.secret-key 默认值一致的密钥 */
    private static final String SECRET = "mei432qiao765wu168mnjsio";

    /** 鉴权时间窗口（秒），与 app.iot.auth-window-seconds 默认值一致 */
    private static final int AUTH_WINDOW_SECONDS = 900;

    /** 默认租户 ID */
    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000000";

    @Mock
    private IotRepository repository;

    @InjectMocks
    private IotService iotService;

    @BeforeEach
    void setUp() {
        // 注入 @Value 字段（纯 Mockito 测试不走 Spring 容器）
        ReflectionTestUtils.setField(iotService, "secretKey", SECRET);
        ReflectionTestUtils.setField(iotService, "authWindowSeconds", AUTH_WINDOW_SECONDS);
        ReflectionTestUtils.setField(iotService, "defaultTenantId", "");
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    /** 计算 md5(secret + time)，返回 32 位小写 hex */
    private static String sign(String time) {
        return md5(SECRET + time);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** 构造一个带合法签名的 IotRequest */
    private IotRequest validRequest(String action, String itemNumber) {
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        IotRequest req = new IotRequest();
        req.setAction(action);
        req.setItemNumber(itemNumber);
        req.setTime(time);
        req.setToken(sign(time));
        return req;
    }

    /** 构造一个带合法签名和尺寸的 pickup/update 请求 */
    private IotRequest pickupRequest(String itemNumber, double w, double l, double wd, double h) {
        IotRequest req = validRequest("pickup", itemNumber);
        req.setWeight(BigDecimal.valueOf(w));
        req.setLength(BigDecimal.valueOf(l));
        req.setWidth(BigDecimal.valueOf(wd));
        req.setHeight(BigDecimal.valueOf(h));
        return req;
    }

    /** 构造一个带合法签名和尺寸的 update 请求 */
    private IotRequest updateRequest(String itemNumber, double w, double l, double wd, double h) {
        IotRequest req = validRequest("update", itemNumber);
        req.setWeight(BigDecimal.valueOf(w));
        req.setLength(BigDecimal.valueOf(l));
        req.setWidth(BigDecimal.valueOf(wd));
        req.setHeight(BigDecimal.valueOf(h));
        return req;
    }

    /** 构造一条模拟的 carton 数据库记录 */
    private Map<String, Object> mockCartonRow(String cartonId, String cartonNo,
                                               String shipmentId, String shipmentNo) {
        Map<String, Object> row = new HashMap<>();
        row.put("carton_id", cartonId);
        row.put("carton_no", cartonNo);
        row.put("actual_weight_kg", null);
        row.put("length_cm", null);
        row.put("width_cm", null);
        row.put("height_cm", null);
        row.put("shipment_id", shipmentId);
        row.put("shipment_no", shipmentNo);
        row.put("shipment_status", "DRAFT");
        row.put("destination_country", "US");
        row.put("destination_postal_code", "07001");
        row.put("channel_code", "EU-AIR-UPS");
        row.put("channel_name", "欧洲空派 UPS");
        return row;
    }

    // ══════════════════════════════════════════════
    // 1. Token 鉴权测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Token 鉴权")
    class AuthTests {

        @Test
        @DisplayName("缺少 token → 签名错误")
        void shouldRejectWhenTokenMissing() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setToken(null);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_AUTH_INVALID");
        }

        @Test
        @DisplayName("缺少 time → 请求已过期")
        void shouldRejectWhenTimeMissing() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setTime(null);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_TIMESTAMP_EXPIRED");
        }

        @Test
        @DisplayName("错误 token（故意传错） → 签名错误")
        void shouldRejectWhenTokenInvalid() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setToken("00000000000000000000000000000000"); // 32 位假 token

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_AUTH_INVALID");
        }

        @Test
        @DisplayName("时间戳过期（超出 900 秒窗口） → 请求已过期")
        void shouldRejectWhenTimestampExpired() {
            String pastTime = String.valueOf(System.currentTimeMillis() / 1000 - 901);
            IotRequest req = new IotRequest();
            req.setAction("check");
            req.setItemNumber("TEST-001");
            req.setTime(pastTime);
            req.setToken(sign(pastTime));

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_TIMESTAMP_EXPIRED");
        }

        @Test
        @DisplayName("未来时间戳（超过窗口） → 请求已过期")
        void shouldRejectWhenTimestampInFuture() {
            String futureTime = String.valueOf(System.currentTimeMillis() / 1000 + 901);
            IotRequest req = new IotRequest();
            req.setAction("check");
            req.setItemNumber("TEST-001");
            req.setTime(futureTime);
            req.setToken(sign(futureTime));

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_TIMESTAMP_EXPIRED");
        }

        @Test
        @DisplayName("重复请求（相同 token 在窗口内再次使用） → 重复请求")
        void shouldRejectDuplicateRequest() {
            IotRequest req = validRequest("check", "TEST-001");
            // 第一次请求通过认证，但 item_number 为空时会在业务层抛异常
            // 需要 item_number 有值但 carton 存在，让流程走完
            req.setItemNumber("TEST-001");
            when(repository.findCartonByItemNumber(anyString(), eq("TEST-001")))
                    .thenReturn(mockCartonRow("c1", "TEST-001", "s1", "SHIP-001"));
            when(repository.countCartonsByShipment(anyString(), eq("s1"))).thenReturn(3);

            // 第一次请求成功
            IotResponse first = iotService.process(req);
            assertThat(first.getStatus()).isEqualTo(1);

            // 第二次请求相同 token → 重复请求
            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DUPLICATE_REQUEST");
        }

        @Test
        @DisplayName("边界：刚好 900 秒（窗口内） → 通过认证")
        void shouldPassAtWindowBoundary() {
            String timeAtBoundary = String.valueOf(System.currentTimeMillis() / 1000 - 899);
            IotRequest req = new IotRequest();
            req.setAction("check");
            req.setItemNumber("TEST-001");
            req.setTime(timeAtBoundary);
            req.setToken(sign(timeAtBoundary));

            when(repository.findCartonByItemNumber(anyString(), eq("TEST-001")))
                    .thenReturn(mockCartonRow("c1", "TEST-001", "s1", "SHIP-001"));
            when(repository.countCartonsByShipment(anyString(), eq("s1"))).thenReturn(5);

            IotResponse resp = iotService.process(req);
            assertThat(resp.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("time 非数字格式 → 请求已过期")
        void shouldRejectWhenTimeIsNotNumeric() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setTime("not-a-number");

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_TIMESTAMP_EXPIRED");
        }
    }

    // ══════════════════════════════════════════════
    // 2. check 接口测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("check 接口")
    class CheckTests {

        @Test
        @DisplayName("item_number 为空 → 箱号不存在")
        void shouldRejectWhenItemNumberMissing() {
            IotRequest req = validRequest("check", null);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_ITEM_NOT_FOUND");
        }

        @Test
        @DisplayName("箱号已预报 → 返回 status=1 + options 含箱号/运单/客户等信息")
        void shouldCheckExistingCarton() {
            IotRequest req = validRequest("check", "TEST-001");
            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");

            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(10);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getInfo()).isEmpty();
            assertThat(resp.getOptions())
                    .isNotNull()
                    .anyMatch(o -> "箱号".equals(o.getLabel()) && "TEST-001".equals(o.getValue()))
                    .anyMatch(o -> "总箱数".equals(o.getLabel()) && Integer.valueOf(10).equals(o.getValue()))
                    .anyMatch(o -> "运单号".equals(o.getLabel()) && "SHIP-001".equals(o.getValue()))
                    .anyMatch(o -> "服务".equals(o.getLabel()) && "欧洲空派 UPS".equals(o.getValue()))
                    .anyMatch(o -> "服务代码".equals(o.getLabel()) && "EU-AIR-UPS".equals(o.getValue()))
                    .anyMatch(o -> "国家".equals(o.getLabel()) && "US".equals(o.getValue()))
                    .anyMatch(o -> "邮编".equals(o.getLabel()) && "07001".equals(o.getValue()));
        }

        @Test
        @DisplayName("箱号未预报 → 自动创建运单+箱号，返回 status=1")
        void shouldCheckNewCarton_autoCreate() {
            IotRequest req = validRequest("check", "NEW-001");

            when(repository.findCartonByItemNumber(TENANT_ID, "NEW-001")).thenReturn(null);
            when(repository.findOrCreateDefaultCustomer(TENANT_ID)).thenReturn("cust-1");
            when(repository.findOrCreateShipment(eq(TENANT_ID), eq("cust-1"), anyString()))
                    .thenReturn("new-ship-1");
            when(repository.findOrCreateCarton(eq(TENANT_ID), eq("new-ship-1"), eq("NEW-001"),
                    eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO)))
                    .thenReturn("new-carton-1");
            when(repository.countCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getOptions()).isNotNull();
        }

        @Test
        @DisplayName("运单号不匹配 → 箱号和运单不匹配")
        void shouldRejectWhenShipmentMismatch() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setShipmentNumber("WRONG-SHIP"); // 传了一个不匹配的运单号

            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");
            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_SHIPMENT_MISMATCH");
        }

        @Test
        @DisplayName("运单号传了但匹配 → 正常返回")
        void shouldCheckWhenShipmentMatches() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setShipmentNumber("SHIP-001"); // 传了匹配的运单号

            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");
            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(3);

            IotResponse resp = iotService.process(req);
            assertThat(resp.getStatus()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════
    // 3. pickup 接口测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("pickup 接口")
    class PickupTests {

        @Test
        @DisplayName("重量为 null → 重量或尺寸不合法")
        void shouldRejectWhenWeightNull() {
            IotRequest req = validRequest("pickup", "TEST-001");
            req.setWeight(null);
            req.setLength(BigDecimal.valueOf(30));
            req.setWidth(BigDecimal.valueOf(20));
            req.setHeight(BigDecimal.valueOf(15));

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("重量 ≤ 0 → 重量或尺寸不合法")
        void shouldRejectWhenWeightZero() {
            IotRequest req = pickupRequest("TEST-001", 0, 30, 20, 15);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("长度为 null → 重量或尺寸不合法")
        void shouldRejectWhenLengthNull() {
            IotRequest req = pickupRequest("TEST-001", 5, 0, 20, 15);
            req.setLength(null);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("宽度 ≤ 0 → 重量或尺寸不合法")
        void shouldRejectWhenWidthInvalid() {
            IotRequest req = pickupRequest("TEST-001", 5, 30, -1, 15);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("高度为 null → 重量或尺寸不合法")
        void shouldRejectWhenHeightNull() {
            IotRequest req = pickupRequest("TEST-001", 5, 30, 20, 0);
            req.setHeight(null);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("正常 pickup（已预报 carton） → status=1 + voice_text 含当前/总箱数")
        void shouldPickupExistingCarton() {
            IotRequest req = pickupRequest("TEST-001", 5.2, 30.0, 20.0, 15.0);
            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");

            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(10);
            when(repository.countScannedCartonsByShipment(TENANT_ID, "s1")).thenReturn(3);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getAction()).isEqualTo("continue");
            assertThat(resp.getVoiceText()).isEqualTo("拣货成功，当前 3 / 10");
            assertThat(resp.getOptions())
                    .anyMatch(o -> "件数".equals(o.getLabel()) && "3/10".equals(o.getValue()));

            // 验证更新了 carton 尺寸
            verify(repository).updateCartonDimensions(TENANT_ID, "c1",
                    BigDecimal.valueOf(5.2), BigDecimal.valueOf(30.0),
                    BigDecimal.valueOf(20.0), BigDecimal.valueOf(15.0));
            // 验证插入了扫描事件
            verify(repository).insertScanEvent(TENANT_ID, "s1", "c1",
                    "PICKUP", "OK", null, null, null);
            // 验证更新了 shipment 测量时间
            verify(repository).updateShipmentMeasuredAt(TENANT_ID, "s1");
        }

        @Test
        @DisplayName("pickup 新 carton（未预报） → 自动创建并返回成功")
        void shouldPickupNewCarton_autoCreate() {
            IotRequest req = pickupRequest("NEW-001", 3.0, 25.0, 18.0, 12.0);

            when(repository.findCartonByItemNumber(TENANT_ID, "NEW-001")).thenReturn(null);
            when(repository.findOrCreateDefaultCustomer(TENANT_ID)).thenReturn("cust-1");
            when(repository.findOrCreateShipment(eq(TENANT_ID), eq("cust-1"), anyString()))
                    .thenReturn("new-ship-1");
            when(repository.findOrCreateCarton(eq(TENANT_ID), eq("new-ship-1"), eq("NEW-001"),
                    eq(BigDecimal.valueOf(3.0)), eq(BigDecimal.valueOf(25.0)),
                    eq(BigDecimal.valueOf(18.0)), eq(BigDecimal.valueOf(12.0))))
                    .thenReturn("new-carton-1");
            when(repository.countCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);
            when(repository.countScannedCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getVoiceText()).isEqualTo("拣货成功，当前 1 / 1");
        }

        @Test
        @DisplayName("pickup 运单号不匹配 → 箱号和运单不匹配")
        void shouldRejectPickupWhenShipmentMismatch() {
            IotRequest req = pickupRequest("TEST-001", 5, 30, 20, 15);
            req.setShipmentNumber("WRONG-SHIP");

            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");
            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_SHIPMENT_MISMATCH");
        }
    }

    // ══════════════════════════════════════════════
    // 4. update 接口测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("update 接口")
    class UpdateTests {

        @Test
        @DisplayName("尺寸不合法 → 重量或尺寸不合法")
        void shouldRejectWhenDimensionsInvalid() {
            IotRequest req = updateRequest("TEST-001", 0, 0, 0, 0);

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_DIMENSION_INVALID");
        }

        @Test
        @DisplayName("正常 update（已存在 carton） → status=1 + voice_text=更新成功")
        void shouldUpdateExistingCarton() {
            IotRequest req = updateRequest("TEST-001", 5.5, 32.0, 22.0, 16.0);
            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");

            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(10);
            when(repository.countScannedCartonsByShipment(TENANT_ID, "s1")).thenReturn(5);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getAction()).isEqualTo("continue");
            assertThat(resp.getVoiceText()).isEqualTo("更新成功");

            // 验证更新了 carton 尺寸
            verify(repository).updateCartonDimensions(TENANT_ID, "c1",
                    BigDecimal.valueOf(5.5), BigDecimal.valueOf(32.0),
                    BigDecimal.valueOf(22.0), BigDecimal.valueOf(16.0));
            // 验证插入了 UPDATE 类型的扫描事件
            verify(repository).insertScanEvent(TENANT_ID, "s1", "c1",
                    "UPDATE", "OK", null, null, null);
        }

        @Test
        @DisplayName("update 新 carton（未预报） → 自动创建并返回成功")
        void shouldUpdateNewCarton_autoCreate() {
            IotRequest req = updateRequest("NEW-001", 4.0, 28.0, 19.0, 13.0);

            when(repository.findCartonByItemNumber(TENANT_ID, "NEW-001")).thenReturn(null);
            when(repository.findOrCreateDefaultCustomer(TENANT_ID)).thenReturn("cust-1");
            when(repository.findOrCreateShipment(eq(TENANT_ID), eq("cust-1"), anyString()))
                    .thenReturn("new-ship-1");
            when(repository.findOrCreateCarton(eq(TENANT_ID), eq("new-ship-1"), eq("NEW-001"),
                    eq(BigDecimal.valueOf(4.0)), eq(BigDecimal.valueOf(28.0)),
                    eq(BigDecimal.valueOf(19.0)), eq(BigDecimal.valueOf(13.0))))
                    .thenReturn("new-carton-1");
            when(repository.countCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);
            when(repository.countScannedCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getStatus()).isEqualTo(1);
            assertThat(resp.getVoiceText()).isEqualTo("更新成功");
        }
    }

    // ══════════════════════════════════════════════
    // 5. 无效 action 测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("无效 action")
    class InvalidActionTests {

        @Test
        @DisplayName("不支持的 action → 无效的action参数")
        void shouldRejectUnknownAction() {
            IotRequest req = validRequest("delete", "TEST-001");

            assertThatThrownBy(() -> iotService.process(req))
                    .isInstanceOf(IotException.class)
                    .extracting("errorCode")
                    .isEqualTo("IOT_INVALID_ACTION");
        }
    }

    // ══════════════════════════════════════════════
    // 6. printLabels 扩展字段测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("printLabels 扩展字段")
    class PrintLabelsTests {

        @Test
        @DisplayName("ext 包含 printlabels → 返回 printLabels 字段")
        void shouldParsePrintLabels() {
            IotRequest req = validRequest("check", "TEST-001");
            Map<String, Object> ext = new HashMap<>();
            Map<String, Object> labels = new HashMap<>();
            Map<String, Object> labelA = new HashMap<>();
            labelA.put("printer_name", "PRINTER-A");
            labelA.put("qty", 2);
            labelA.put("type", "CARGO_LABEL");
            labelA.put("label_url", "http://example.com/label.pdf");
            labels.put("labelA", labelA);
            ext.put("printlabels", labels);
            req.setExt(ext);

            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");
            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(3);

            IotResponse resp = iotService.process(req);

            assertThat(resp.getPrintLabels()).isNotNull().hasSize(1);
            IotResponse.PrintLabel pl = resp.getPrintLabels().get(0);
            assertThat(pl.getPrinterName()).isEqualTo("PRINTER-A");
            assertThat(pl.getPrintQty()).isEqualTo(2);
            assertThat(pl.getType()).isEqualTo("CARGO_LABEL");
            assertThat(pl.getLabelUrl()).isEqualTo("http://example.com/label.pdf");
        }

        @Test
        @DisplayName("ext 为空 → printLabels 为 null")
        void shouldReturnNullPrintLabelsWhenExtEmpty() {
            IotRequest req = validRequest("check", "TEST-001");
            req.setExt(new HashMap<>());

            Map<String, Object> cartonRow = mockCartonRow("c1", "TEST-001", "s1", "SHIP-001");
            when(repository.findCartonByItemNumber(TENANT_ID, "TEST-001")).thenReturn(cartonRow);
            when(repository.countCartonsByShipment(TENANT_ID, "s1")).thenReturn(3);

            IotResponse resp = iotService.process(req);
            assertThat(resp.getPrintLabels()).isNull();
        }
    }

    // ══════════════════════════════════════════════
    // 7. shipmentNumber 自动生成测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("shipmentNumber 自动生成")
    class ShipmentNumberAutoGenTests {

        @Test
        @DisplayName("check 时 shipment_number 为空 → 自动生成 IOT_xxx")
        void shouldAutoGenerateShipmentNoForCheck() {
            IotRequest req = validRequest("check", "NEW-001");
            req.setShipmentNumber(null);

            when(repository.findCartonByItemNumber(TENANT_ID, "NEW-001")).thenReturn(null);
            when(repository.findOrCreateDefaultCustomer(TENANT_ID)).thenReturn("cust-1");
            when(repository.findOrCreateShipment(eq(TENANT_ID), eq("cust-1"), anyString()))
                    .thenReturn("new-ship-1");
            when(repository.findOrCreateCarton(eq(TENANT_ID), eq("new-ship-1"), eq("NEW-001"),
                    any(), any(), any(), any())).thenReturn("new-carton-1");
            when(repository.countCartonsByShipment(TENANT_ID, "new-ship-1")).thenReturn(1);

            IotResponse resp = iotService.process(req);
            assertThat(resp.getStatus()).isEqualTo(1);

            // 捕获传给 findOrCreateShipment 的 shipmentNo
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(repository).findOrCreateShipment(eq(TENANT_ID), eq("cust-1"), captor.capture());
            assertThat(captor.getValue()).startsWith("IOT_");
        }
    }
}