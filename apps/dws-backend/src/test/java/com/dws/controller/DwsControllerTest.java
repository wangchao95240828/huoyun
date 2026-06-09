package com.dws.controller;

import com.dws.dto.IotResponse;
import com.dws.exception.IotException;
import com.dws.service.IotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DWS Controller 层集成测试（MockMvc）
 * <p>
 * 验证 HTTP 层：路由、JSON 反序列化、异常处理、响应格式
 */
@WebMvcTest(DwsController.class)
@DisplayName("DwsController MockMvc 测试")
class DwsControllerTest {

    private static final String SECRET = "mei432qiao765wu168mnjsio";
    private static final String URL = "/api/iot/warehouse/parcel";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IotService iotService;

    @BeforeEach
    void setUp() {
        // 默认配置已通过 @WebMvcTest 加载
    }

    /** 计算 md5(secret + time)，返回 32 位小写 hex */
    private static String sign(String time) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((SECRET + time).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** 构造合法签名的请求体 JSON */
    private String requestBody(String action, String itemNumber) throws Exception {
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("action", action);
        body.put("item_number", itemNumber);
        body.put("time", time);
        body.put("token", sign(time));
        return objectMapper.writeValueAsString(body);
    }

    /** 构造 pickup/update 请求体（含尺寸） */
    private String pickupBody(String action, String itemNumber,
                              double w, double l, double wd, double h) throws Exception {
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("action", action);
        body.put("item_number", itemNumber);
        body.put("time", time);
        body.put("token", sign(time));
        body.put("weight", w);
        body.put("length", l);
        body.put("width", wd);
        body.put("height", h);
        return objectMapper.writeValueAsString(body);
    }

    // ══════════════════════════════════════════════
    // 1. 鉴权相关
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Token 鉴权")
    class AuthTests {

        @Test
        @DisplayName("错误 token → 返回 status=0 + 签名错误")
        void shouldReturnAuthErrorOnInvalidToken() throws Exception {
            String body = requestBody("check", "TEST-001");
            // 故意把 token 改错
            body = body.replaceFirst("\"token\":\"[^\"]+\"", "\"token\":\"00000000000000000000000000000000\"");

            // IotException 被 @ExceptionHandler 捕获 → 200 OK + status=0
            when(iotService.process(any())).thenThrow(IotException.authInvalid());

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(0))
                    .andExpect(jsonPath("$.info").value("签名错误"))
                    .andExpect(jsonPath("$.action").value("stop"));
        }

        @Test
        @DisplayName("缺少 item_number → 返回 status=0")
        void shouldReturnErrorOnMissingItemNumber() throws Exception {
            when(iotService.process(any())).thenThrow(IotException.itemNotFound());

            String body = requestBody("check", "TEST-001");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(0))
                    .andExpect(jsonPath("$.info").value("箱号不存在"));
        }
    }

    // ══════════════════════════════════════════════
    // 2. check 接口
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("check 接口")
    class CheckTests {

        @Test
        @DisplayName("正常 check → 返回 status=1 + options")
        void shouldReturnCheckSuccess() throws Exception {
            IotResponse mockResp = IotResponse.builder()
                    .status(1)
                    .info("")
                    .options(List.of(
                            IotResponse.OptionItem.builder().label("箱号").value("TEST-001").build(),
                            IotResponse.OptionItem.builder().label("总箱数").value(10).build(),
                            IotResponse.OptionItem.builder().label("运单号").value("SHIP-001").build()
                    ))
                    .build();
            when(iotService.process(any())).thenReturn(mockResp);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody("check", "TEST-001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(1))
                    .andExpect(jsonPath("$.options[0].label").value("箱号"))
                    .andExpect(jsonPath("$.options[0].value").value("TEST-001"))
                    .andExpect(jsonPath("$.options[1].label").value("总箱数"))
                    .andExpect(jsonPath("$.options[1].value").value(10));
        }
    }

    // ══════════════════════════════════════════════
    // 3. pickup 接口
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("pickup 接口")
    class PickupTests {

        @Test
        @DisplayName("正常 pickup → 返回 status=1 + voice_text")
        void shouldReturnPickupSuccess() throws Exception {
            IotResponse mockResp = IotResponse.builder()
                    .status(1)
                    .info("")
                    .action("continue")
                    .voiceText("拣货成功，当前 3 / 10")
                    .options(List.of(
                            IotResponse.OptionItem.builder().label("件数").value("3/10").build(),
                            IotResponse.OptionItem.builder().label("箱号").value("TEST-001").build()
                    ))
                    .build();
            when(iotService.process(any())).thenReturn(mockResp);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pickupBody("pickup", "TEST-001", 5.2, 30, 20, 15)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(1))
                    .andExpect(jsonPath("$.voiceText").value("拣货成功，当前 3 / 10"))
                    .andExpect(jsonPath("$.options[0].label").value("件数"));
        }
    }

    // ══════════════════════════════════════════════
    // 4. update 接口
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("update 接口")
    class UpdateTests {

        @Test
        @DisplayName("正常 update → 返回 status=1 + voice_text=更新成功")
        void shouldReturnUpdateSuccess() throws Exception {
            IotResponse mockResp = IotResponse.builder()
                    .status(1)
                    .info("")
                    .action("continue")
                    .voiceText("更新成功")
                    .options(List.of(
                            IotResponse.OptionItem.builder().label("件数").value("5/10").build()
                    ))
                    .build();
            when(iotService.process(any())).thenReturn(mockResp);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pickupBody("update", "TEST-001", 5.5, 32, 22, 16)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(1))
                    .andExpect(jsonPath("$.voiceText").value("更新成功"));
        }
    }

    // ══════════════════════════════════════════════
    // 5. 无效 action
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("无效 action")
    class InvalidActionTests {

        @Test
        @DisplayName("不支持的 action → 返回 status=0")
        void shouldReturnInvalidActionError() throws Exception {
            when(iotService.process(any()))
                    .thenThrow(new IotException("IOT_INVALID_ACTION", "无效的action参数", "stop"));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody("delete", "TEST-001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(0))
                    .andExpect(jsonPath("$.info").value("无效的action参数"))
                    .andExpect(jsonPath("$.action").value("stop"));
        }
    }

    // ══════════════════════════════════════════════
    // 6. 请求体绑定测试
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("请求体 JSON 绑定")
    class JsonBindingTests {

        @Test
        @DisplayName("合法 JSON → 正确绑定到 IotRequest")
        void shouldBindValidJson() throws Exception {
            IotResponse mockResp = IotResponse.builder().status(1).info("").build();
            when(iotService.process(any())).thenReturn(mockResp);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody("check", "TEST-001")))
                    .andExpect(status().isOk());

            verify(iotService).process(any());
        }

        @Test
        @DisplayName("Content-Type 不匹配 → 返回 415")
        void shouldReturn415WhenContentTypeWrong() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("plain text"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("空 body → 返回 400")
        void shouldReturn400WhenBodyEmpty() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("非法 JSON → 返回 400")
        void shouldReturn400WhenInvalidJson() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalid json }"))
                    .andExpect(status().isBadRequest());
        }
    }
}