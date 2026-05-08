package com.xqt.saas.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
    private static final int HASH_PART_COUNT = 5;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int SALT_BYTES = 18;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PBKDF2_MARKER = "pbkdf2";
    private static final String SHA256_MARKER = "sha256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] saltBytes = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(saltBytes);
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);

        try {
            KeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                ITERATIONS,
                KEY_LENGTH_BITS
            );
            byte[] raw = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            return PBKDF2_MARKER + "$" + SHA256_MARKER + "$" + ITERATIONS + "$" + salt + "$" + digest;
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash password", ex);
        }
    }

    public boolean verify(String password, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return false;
        }

        String[] parts = encoded.split("\\$");
        if (parts.length != HASH_PART_COUNT || !PBKDF2_MARKER.equals(parts[0]) || !SHA256_MARKER.equals(parts[1])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[2]);
            String salt = parts[3];
            String expected = parts[4];
            if (iterations < MIN_ITERATIONS) {
                return false;
            }

            KeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                iterations,
                KEY_LENGTH_BITS
            );
            byte[] raw = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            String actual = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
            );
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NumberFormatException ex) {
            return false;
        }
    }
}
