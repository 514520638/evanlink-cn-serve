package com.evanlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AdminAuthService {

    private static final long TOKEN_TTL_SECONDS = 12 * 60 * 60;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.admin.username:14776866846}")
    private String adminUsername;

    @Value("${app.admin.password:0.cptbtptp}")
    private String adminPassword;

    public Optional<String> login(String username, String password) {
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            return Optional.empty();
        }

        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = adminUsername + ":" + expiresAt;
        return Optional.of(encode(payload) + "." + sign(payload));
    }

    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }

        String payload = decode(parts[0]);
        if (payload == null || !sign(payload).equals(parts[1])) {
            return false;
        }

        String[] payloadParts = payload.split(":", 2);
        if (payloadParts.length != 2 || !adminUsername.equals(payloadParts[0])) {
            return false;
        }

        try {
            long expiresAt = Long.parseLong(payloadParts[1]);
            return expiresAt >= Instant.now().getEpochSecond();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public boolean isValidAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return isValidToken(authorization.substring("Bearer ".length()));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(adminPassword.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign admin token", ex);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
