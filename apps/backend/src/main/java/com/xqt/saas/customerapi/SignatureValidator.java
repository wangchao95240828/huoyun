package com.xqt.saas.customerapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * 复刻 ACC api/APIClass.php 的签名算法：
 *   1. 把请求参数（user/act/time/version/...）按 key 升序排列；
 *   2. 取 value 用英文逗号拼接：join(',', sorted_values)；
 *   3. 末尾追加 APIKey；
 *   4. md5 取小写 hex，长度 32；
 *   5. 与 sign 参数比较。
 * 注意：旧实现把 sign 自己也放进 $_GET/$_POST，所以拼接时必须先剔除 sign。
 */
@Component
public class SignatureValidator {
    private static final String SIGN_KEY = "sign";
    public static final long TIME_DRIFT_SECONDS = 3600L;
    public static final int MD5_HEX_LENGTH = 32;

    public String expected(Map<String, String> params, String apiKey) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k == null || SIGN_KEY.equalsIgnoreCase(k) || v == null) {
                return;
            }
            sorted.put(k, v);
        });
        StringBuilder joined = new StringBuilder();
        boolean first = true;
        for (String value : sorted.values()) {
            if (!first) {
                joined.append(',');
            }
            joined.append(value);
            first = false;
        }
        joined.append(apiKey == null ? "" : apiKey);
        return md5Hex(joined.toString());
    }

    public boolean matches(Map<String, String> params, String apiKey, String givenSign) {
        if (givenSign == null || givenSign.length() != MD5_HEX_LENGTH) {
            return false;
        }
        String expected = expected(params, apiKey);
        return constantTimeEquals(expected, givenSign.toLowerCase(Locale.ROOT));
    }

    public boolean withinTimeWindow(long requestEpochSeconds, long nowEpochSeconds) {
        return Math.abs(nowEpochSeconds - requestEpochSeconds) <= TIME_DRIFT_SECONDS;
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(MD5_HEX_LENGTH);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
