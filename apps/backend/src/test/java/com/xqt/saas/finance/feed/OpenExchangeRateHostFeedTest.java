package com.xqt.saas.finance.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 用本地 HttpServer 模拟 exchangerate.host，验证：
 *  1. 正常返回 → 倒数后落 BigDecimal
 *  2. HTTP 5xx → 空 Map（不抛）
 *  3. 网络超时 → 空 Map（不抛）
 *  4. 空 currencies → 不发请求
 *  5. JSON 结构异常 → 空 Map
 */
class OpenExchangeRateHostFeedTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private void respondJson(String json) {
        server.createContext("/", exchange -> {
            byte[] body = json.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
    }

    private void respondStatus(int code) {
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
    }

    private OpenExchangeRateHostFeed feed() {
        return new OpenExchangeRateHostFeed(new ObjectMapper(),
            "http://127.0.0.1:" + port + "/", 3000);
    }

    @Test
    void fetchReturnsInverseRates() {
        // base=CNY → 1 CNY = 0.1378 USD ⇒ 1 USD = 7.25690856 CNY (倒数)
        respondJson("{\"success\":true,\"base\":\"CNY\",\"rates\":{\"USD\":0.1378,\"EUR\":0.1266},\"date\":\"2026-05-29\"}");

        Map<String, BigDecimal> result = feed().fetch("CNY", List.of("USD", "EUR"), LocalDate.parse("2026-05-29"));

        assertThat(result).hasSize(2);
        // 1 / 0.1378 = 7.25689405...
        assertThat(result.get("USD")).isEqualByComparingTo("7.25689405");
        assertThat(result.get("EUR")).isEqualByComparingTo("7.89889415");
    }

    @Test
    void serverErrorReturnsEmptyMap() {
        respondStatus(503);
        Map<String, BigDecimal> result = feed().fetch("CNY", List.of("USD"), LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void timeoutReturnsEmptyMap() {
        // 不注册任何 handler → 默认 404，但更测试超时：让 handler 睡眠超过 timeout
        server.createContext("/", exchange -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        OpenExchangeRateHostFeed shortTimeoutFeed = new OpenExchangeRateHostFeed(
            new ObjectMapper(), "http://127.0.0.1:" + port + "/", 500);
        assertDoesNotThrow(() -> {
            Map<String, BigDecimal> result = shortTimeoutFeed.fetch("CNY", List.of("USD"), LocalDate.now());
            assertThat(result).isEmpty();
        });
    }

    @Test
    void emptyCurrenciesSkipRequest() {
        // 未注册 handler，请求会 404，但 fetch 会因 empty 直接返回 Map.of()
        Map<String, BigDecimal> result = feed().fetch("CNY", List.of(), LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void malformedJsonReturnsEmptyMap() {
        respondJson("{not valid json");
        Map<String, BigDecimal> result = feed().fetch("CNY", List.of("USD"), LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void missingRatesFieldReturnsEmptyMap() {
        respondJson("{\"success\":true,\"base\":\"CNY\",\"date\":\"2026-05-29\"}");
        Map<String, BigDecimal> result = feed().fetch("CNY", List.of("USD"), LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void zeroRateIsSkipped() {
        respondJson("{\"success\":true,\"base\":\"CNY\",\"rates\":{\"USD\":0,\"EUR\":0.1266},\"date\":\"2026-05-29\"}");
        Map<String, BigDecimal> result = feed().fetch("CNY", List.of("USD", "EUR"), LocalDate.now());
        assertThat(result).containsOnlyKeys("EUR");
    }

    @Test
    void codeIdentifiesFeed() {
        assertThat(feed().code()).isEqualTo("exchangerate.host");
    }
}
