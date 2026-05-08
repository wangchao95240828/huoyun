package com.xqt.saas.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private static final int SIGNATURE_INDEX = 2;
    private static final int TOKEN_PART_COUNT = 3;
    private static final String JWT_ALGORITHM = "HS256";
    private static final String JWT_TYPE = "JWT";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long ttlSeconds;

    public TokenService(
        ObjectMapper objectMapper,
        @Value("${app.auth.jwt-secret}") String secret,
        @Value("${app.auth.token-ttl-seconds}") long ttlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedToken issue(AuthPrincipal base) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        AuthPrincipal payload = new AuthPrincipal(
            base.userId(),
            base.tenantId(),
            base.tenantCode(),
            base.username(),
            base.displayName(),
            base.roles(),
            base.permissions(),
            exp,
            UUID.randomUUID().toString()
        );
        String header = encodeJson(Map.of("alg", JWT_ALGORITHM, "typ", JWT_TYPE));
        String body = encodeJson(payload);
        return new IssuedToken(header + "." + body + "." + sign(header + "." + body), payload, ttlSeconds);
    }

    public AuthPrincipal verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != TOKEN_PART_COUNT) {
            throw new IllegalArgumentException("Invalid token");
        }

        String expected = sign(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expected, parts[SIGNATURE_INDEX])) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, PAYLOAD_TYPE);
            long exp = ((Number) payload.get("exp")).longValue();
            if (exp < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Token expired");
            }
            return new AuthPrincipal(
                (String) payload.get("userId"),
                (String) payload.get("tenantId"),
                (String) payload.get("tenantCode"),
                (String) payload.get("username"),
                (String) payload.get("displayName"),
                objectMapper.convertValue(payload.get("roles"), STRING_LIST_TYPE),
                objectMapper.convertValue(payload.get("permissions"), STRING_LIST_TYPE),
                exp,
                (String) payload.get("jti")
            );
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid token", ex);
        }
    }

    private String encodeJson(Object value) {
        try {
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to encode token", ex);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to sign token", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record IssuedToken(String token, AuthPrincipal payload, long expiresIn) {
    }
}
