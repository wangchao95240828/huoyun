package com.xqt.saas.customerapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class SignatureValidatorTests {
    private final SignatureValidator validator = new SignatureValidator();

    @Test
    void signatureIgnoresInsertionOrder() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("user", "60000DEMO");
        a.put("time", "1715500000");
        a.put("version", "1");
        a.put("body", "abc");

        Map<String, String> b = new LinkedHashMap<>();
        b.put("body", "abc");
        b.put("version", "1");
        b.put("user", "60000DEMO");
        b.put("time", "1715500000");

        assertThat(validator.expected(a, "key1")).isEqualTo(validator.expected(b, "key1"));
    }

    @Test
    void signatureExcludesSignField() {
        Map<String, String> withSign = new TreeMap<>();
        withSign.put("user", "60000DEMO");
        withSign.put("time", "1715500000");
        withSign.put("version", "1");
        withSign.put("sign", "ignored");

        Map<String, String> withoutSign = new TreeMap<>();
        withoutSign.put("user", "60000DEMO");
        withoutSign.put("time", "1715500000");
        withoutSign.put("version", "1");

        assertThat(validator.expected(withSign, "key1")).isEqualTo(validator.expected(withoutSign, "key1"));
    }

    @Test
    void matchesIsCaseInsensitiveOnSign() {
        Map<String, String> params = new TreeMap<>();
        params.put("user", "60000DEMO");
        params.put("time", "1715500000");
        params.put("version", "1");
        params.put("body", "abc");

        String expected = validator.expected(params, "key1");
        assertThat(validator.matches(params, "key1", expected.toUpperCase())).isTrue();
        assertThat(validator.matches(params, "key1", expected)).isTrue();
        assertThat(validator.matches(params, "key1", "0".repeat(32))).isFalse();
    }

    @Test
    void matchesRejectsWrongLength() {
        Map<String, String> params = new TreeMap<>();
        params.put("user", "60000DEMO");
        assertThat(validator.matches(params, "k", null)).isFalse();
        assertThat(validator.matches(params, "k", "short")).isFalse();
    }

    @Test
    void timeWindowAcceptsBoundary() {
        assertThat(validator.withinTimeWindow(1000L, 1000L + SignatureValidator.TIME_DRIFT_SECONDS)).isTrue();
        assertThat(validator.withinTimeWindow(1000L, 1000L + SignatureValidator.TIME_DRIFT_SECONDS + 1)).isFalse();
    }

    @Test
    void algorithmIsMd5SortedJoinedPlusSecret() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user", "U");
        params.put("time", "T");
        params.put("version", "V");
        params.put("body", "B");

        String expected = validator.expected(params, "K");
        // Sorted by key: body, time, user, version -> values B,T,U,V -> join "B,T,U,V" + "K" = "B,T,U,VK"
        // md5("B,T,U,VK") =
        String md5OfReference = md5Hex("B,T,U,VK");
        assertThat(expected).isEqualTo(md5OfReference);
    }

    private static String md5Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
