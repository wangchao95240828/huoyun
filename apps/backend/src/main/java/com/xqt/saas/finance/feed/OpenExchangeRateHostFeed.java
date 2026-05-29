package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 免费公共 exchangerate.host 实现。
 *
 * 端点：{@code https://api.exchangerate.host/historical?date=YYYY-MM-DD&base=CNY&symbols=USD,EUR}
 *
 * 该 API 不需要 token，无 QPS 限制（普通用法）；超时/异常时返回空 Map，
 * 调用方走 fallback（保留原表配置或 FxSnapshotCapture 的 MISSING_RATE 路径）。
 *
 * 切换到其他 feed（如 PBoC、中行）只需另写一个 {@link FxRateFeed} 实现，
 * 通过配置 {@code app.fx.feed} 选择激活的 bean。
 */
@Component
public class OpenExchangeRateHostFeed implements FxRateFeed {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenExchangeRateHostFeed.class);
    private static final String CODE = "exchangerate.host";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final Duration timeout;

    public OpenExchangeRateHostFeed(ObjectMapper mapper,
                                     @Value("${app.fx.exchangerate-host.endpoint:https://api.exchangerate.host/historical}")
                                     String endpoint,
                                     @Value("${app.fx.exchangerate-host.timeout-ms:5000}")
                                     int timeoutMs) {
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .build();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, BigDecimal> fetch(String baseCurrency,
                                          Collection<String> currencies,
                                          LocalDate rateDate) {
        if (currencies == null || currencies.isEmpty()) return Map.of();
        String symbols = String.join(",", currencies);
        String date = (rateDate == null ? LocalDate.now() : rateDate).toString();
        String url = String.format("%s?date=%s&base=%s&symbols=%s",
            endpoint, date, baseCurrency, symbols);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOGGER.warn("fx feed {} HTTP {}", url, resp.statusCode());
                return Map.of();
            }
            return parse(resp.body(), currencies);
        } catch (Exception ex) {
            LOGGER.warn("fx feed {} failed: {}", url, ex.getMessage());
            return Map.of();
        }
    }

    /**
     * exchangerate.host 历史端点响应：
     *   {"success":true,"base":"CNY","rates":{"USD":0.1378,"EUR":0.1266,...},"date":"2026-05-29"}
     *
     * rate 是 1 base = rate currency，本系统需要的是 1 currency = ? base，所以做倒数：
     *   from_currency 实际 rate = 1 / response.rates.from_currency
     */
    private Map<String, BigDecimal> parse(String body, Collection<String> currencies) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode rates = root.path("rates");
        if (rates.isMissingNode()) return Map.of();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String c : currencies) {
            JsonNode v = rates.get(c);
            if (v == null || v.isNull()) continue;
            BigDecimal forwardRate = v.decimalValue();
            if (forwardRate.signum() <= 0) continue;
            // 倒数：1 USD 对应多少 CNY
            BigDecimal inverse = BigDecimal.ONE.divide(forwardRate, 8, java.math.RoundingMode.HALF_UP);
            out.put(c, inverse);
        }
        return out;
    }
}
