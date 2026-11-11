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

    public static final String ADMIN_COOKIE_NAME = "evanlink_admin_token";
    private static final long TOKEN_TTL_SECONDS = 12 * 60 * 60;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public boolean isConfigured() {
        return adminUsername != null && !adminUsername.isBlank()
            && adminPassword != null && !adminPassword.isBlank();
    }

    @jakarta.annotation.PostConstruct
    public void checkConfiguration() {
        if (!isConfigured()) org.slf4j.LoggerFactory.getLogger(getClass()).warn(
            "Administrator login disabled: configure APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD in the server environment");
    }

    public Optional<String> login(String username, String password) {
        if (!isConfigured()) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "管理员登录未配置，请在后端设置 APP_ADMIN_USERNAME 和 APP_ADMIN_PASSWORD");
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            return Optional.empty();
        }

        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = adminUsername + ":" + expiresAt;
        return Optional.of(encode(payload) + "." + sign(payload));
    }

    public boolean isValidToken(String token) {
        if (!isConfigured() || token == null || token.isBlank()) {
            return false;
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }

        String payload = decode(parts[0]);
        if (payload == null || !java.security.MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
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

    public long getTokenTtlSeconds() {
        return TOKEN_TTL_SECONDS;
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
