package com.evanlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {

    private static final long TOKEN_TTL_SECONDS = 12 * 60 * 60;

    @Value("${app.admin.username:14776866846}")
    private String adminUsername;

    @Value("${app.admin.password:0.cptbtptp}")
    private String adminPassword;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    public Optional<String> login(String username, String password) {
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            return Optional.empty();
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokens.put(token, Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
        return Optional.of(token);
    }

    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        Instant expiresAt = tokens.get(token);
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt.isBefore(Instant.now())) {
            tokens.remove(token);
            return false;
        }

        return true;
    }

    public boolean isValidAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return isValidToken(authorization.substring("Bearer ".length()));
    }
}
