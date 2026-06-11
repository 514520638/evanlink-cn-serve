package com.evanlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeShareTokenService {

    private static final long TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SCOPE = "resume-share";

    @Value("${app.admin.password:0.cptbtptp}")
    private String adminPassword;

    public String createToken() {
        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = TOKEN_SCOPE + ":" + UUID.randomUUID() + ":" + expiresAt;
        return encode(payload) + "." + sign(payload);
    }

    public Optional<Long> getExpiresAt(String token) {
        String payload = parseValidPayload(token);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(payload.split(":", 3)[2]));
    }

    public boolean isValidToken(String token) {
        return parseValidPayload(token) != null;
    }

    private String parseValidPayload(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }

        String payload = decode(parts[0]);
        if (payload == null || !sign(payload).equals(parts[1])) {
            return null;
        }

        String[] payloadParts = payload.split(":", 3);
        if (payloadParts.length != 3 || !TOKEN_SCOPE.equals(payloadParts[0])) {
            return null;
        }

        try {
            long expiresAt = Long.parseLong(payloadParts[2]);
            return expiresAt >= Instant.now().getEpochSecond() ? payload : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            String secret = "resume-share:" + adminPassword;
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign resume share token", ex);
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
