package com.xqt.saas.customerapi;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.xqt.saas.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Customer API 鉴权过滤器，对应 acc/api/APIClass.php::doStart() 的前置校验。
 *
 * 客户端请求约定：
 *   X-API-User:    客户的 access_key（旧系统的 APIID）
 *   X-API-Time:    unix epoch 秒，允许 ±3600 秒漂移
 *   X-API-Version: 协议版本号（整数）
 *   X-API-Sign:    md5(join(',', sorted_values) + secret) 的小写 hex
 *
 * 参与签名的 value 集合（key 升序排列后取值，逗号拼接）：
 *   body  → 请求体 sha256 hex（空请求体取 sha256(""))
 *   time  → X-API-Time
 *   user  → X-API-User
 *   version → X-API-Version
 *
 * 该方案与 ACC PHP 原算法保持一致的"排序值逗号拼接 + 末尾追加 APIKey"结构，
 * 把 body 作为单个稳定 token 加入，避免 PHP 嵌套数组在签名时的歧义。
 */
@Component
public class CustomerApiAuthFilter extends OncePerRequestFilter {
    public static final String PATH_PREFIX = "/api/customer-api/";
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final String HEADER_USER = "X-API-User";
    private static final String HEADER_TIME = "X-API-Time";
    private static final String HEADER_VERSION = "X-API-Version";
    private static final String HEADER_SIGN = "X-API-Sign";

    private final SignatureValidator signatureValidator;
    private final CustomerApiRepository repository;
    private final ObjectMapper objectMapper;

    public CustomerApiAuthFilter(SignatureValidator signatureValidator,
                                 CustomerApiRepository repository,
                                 ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletRequest cached = CachedBodyHttpServletRequest.from(request);

        String accessKey = cached.getHeader(HEADER_USER);
        String time = cached.getHeader(HEADER_TIME);
        String version = cached.getHeader(HEADER_VERSION);
        String sign = cached.getHeader(HEADER_SIGN);

        if (isBlank(accessKey)) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.MISSING_USER, "missing X-API-User header");
            return;
        }
        if (isBlank(time) || !isNumeric(time)) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.TIME_DRIFT, "X-API-Time must be unix epoch seconds");
            return;
        }
        long requestTime;
        try {
            requestTime = Long.parseLong(time);
        } catch (NumberFormatException ex) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.TIME_DRIFT, "X-API-Time is not a valid integer");
            return;
        }
        if (!signatureValidator.withinTimeWindow(requestTime, Instant.now().getEpochSecond())) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.TIME_DRIFT, "X-API-Time drifts more than 1 hour");
            return;
        }
        if (isBlank(version) || !isNumeric(version)) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.MISSING_VERSION, "X-API-Version must be numeric");
            return;
        }
        if (sign == null || sign.length() != SignatureValidator.MD5_HEX_LENGTH) {
            reject(response, HTTP_BAD_REQUEST, AccErrorCode.INVALID_SIGN, "X-API-Sign must be a 32-char md5");
            return;
        }

        CustomerApiCredential credential = repository.findByAccessKey(accessKey);
        if (credential == null) {
            reject(response, HTTP_UNAUTHORIZED, AccErrorCode.UNKNOWN_USER, "access_key not registered");
            return;
        }
        if (!credential.active()) {
            reject(response, HTTP_UNAUTHORIZED, AccErrorCode.UNKNOWN_USER, "access_key is not active");
            return;
        }

        Map<String, String> signedParams = new TreeMap<>();
        signedParams.put("body", sha256Hex(cached.cachedBody()));
        signedParams.put("time", time);
        signedParams.put("user", accessKey);
        signedParams.put("version", version);
        if (!signatureValidator.matches(signedParams, credential.secretKey(), sign)) {
            reject(response, HTTP_UNAUTHORIZED, AccErrorCode.SIGN_MISMATCH, "signature mismatch");
            return;
        }

        repository.markCalled(credential.credentialId());

        CustomerApiPrincipal principal = new CustomerApiPrincipal(
            credential.credentialId(),
            credential.tenantId(),
            credential.customerId(),
            credential.customerCode(),
            credential.accessKey(),
            credential.secretKey()
        );
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_API"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, authorities)
        );

        filterChain.doFilter(cached, response);
    }

    private void reject(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = new ApiResponse<>(false, null, message, errorCode);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data == null ? new byte[0] : data);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 0 && c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 帮客户端构造签名时复用的辅助方法（测试也用）。
     */
    public static String buildSignaturePayload(String accessKey, long epochSeconds, int version, byte[] body) {
        Map<String, String> params = new TreeMap<>();
        params.put("body", sha256Hex(body));
        params.put("time", Long.toString(epochSeconds));
        params.put("user", accessKey);
        params.put("version", Integer.toString(version));
        StringBuilder joined = new StringBuilder();
        boolean first = true;
        for (String value : params.values()) {
            if (!first) {
                joined.append(',');
            }
            joined.append(value);
            first = false;
        }
        return joined.toString();
    }

    public static String bodyHash(byte[] body) {
        return sha256Hex(body);
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
