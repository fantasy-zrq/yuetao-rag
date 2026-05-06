package com.rag.cn.yuetaoragbackend.framework.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

public final class PasswordHashVerifier {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private PasswordHashVerifier() {
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(passwordHash)) {
            return false;
        }
        String normalizedHash = passwordHash.trim();
        if (isBcryptHash(normalizedHash)) {
            return BCRYPT.matches(rawPassword, normalizedHash);
        }
        if (isSha256Hex(normalizedHash)) {
            return MessageDigest.isEqual(
                    sha256Hex(rawPassword).getBytes(StandardCharsets.UTF_8),
                    normalizedHash.toLowerCase().getBytes(StandardCharsets.UTF_8));
        }
        return false;
    }

    private static boolean isBcryptHash(String passwordHash) {
        return passwordHash.startsWith("$2a$") || passwordHash.startsWith("$2b$") || passwordHash.startsWith("$2y$");
    }

    private static boolean isSha256Hex(String passwordHash) {
        return passwordHash.matches("(?i)^[0-9a-f]{64}$");
    }

    private static String sha256Hex(String rawPassword) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte each : digest) {
                builder.append(String.format("%02x", each));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
