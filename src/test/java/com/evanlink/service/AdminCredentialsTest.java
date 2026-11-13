package com.evanlink.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AdminCredentialsTest {
    private AdminAuthService configured() {
        var service=new AdminAuthService();
        ReflectionTestUtils.setField(service,"adminUsername","test-admin");
        ReflectionTestUtils.setField(service,"adminPassword","test-only-password-A");
        return service;
    }
    @Test void missingCredentialsCannotLoginOrAcceptTokens() {
        var service=new AdminAuthService();
        assertFalse(service.isConfigured());
        assertThrows(ResponseStatusException.class,()->service.login("", ""));
        assertFalse(service.isValidToken("payload.signature"));
        ReflectionTestUtils.setField(service,"adminUsername","test-admin");
        ReflectionTestUtils.setField(service,"adminPassword"," ");
        assertFalse(service.isConfigured());
    }
    @Test void configuredCredentialsAllowLoginAndRejectWrongPassword() {
        var service=configured();
        assertTrue(service.isConfigured());
        assertTrue(service.login("test-admin","wrong").isEmpty());
        String token=service.login("test-admin","test-only-password-A").orElseThrow();
        assertTrue(service.isValidToken(token));
        assertFalse(service.isValidToken(token+"tampered"));
    }
    @Test void rotatingPasswordInvalidatesOldLoginTokens() {
        var service=configured();String token=service.login("test-admin","test-only-password-A").orElseThrow();
        ReflectionTestUtils.setField(service,"adminPassword","test-only-password-B");
        assertFalse(service.isValidToken(token));
        assertTrue(service.login("test-admin","test-only-password-A").isEmpty());
    }
    @Test void shareTokenServiceHasNoFallbackSecret() {
        var service=new ResumeShareTokenService();
        assertThrows(ResponseStatusException.class,service::createToken);
        assertFalse(service.isValidToken("payload.signature"));
        ReflectionTestUtils.setField(service,"adminPassword","test-only-password-A");
        String token=service.createToken();assertTrue(service.isValidToken(token));
        ReflectionTestUtils.setField(service,"adminPassword","test-only-password-B");
        assertFalse(service.isValidToken(token));
    }
    @Test void logoutRevokesCookieWithoutStoringRawToken() {
        var service=configured();var hashes=new java.util.HashSet<String>();
        var repository=(com.evanlink.auth.RevokedAdminSessionRepository)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{com.evanlink.auth.RevokedAdminSessionRepository.class},(p,m,a)->switch(m.getName()) {
            case "existsById" -> hashes.contains(a[0]);
            case "save" -> {var row=(com.evanlink.auth.RevokedAdminSession)a[0];hashes.add(row.tokenHash);yield row;}
            case "deleteByExpiresAtLessThan" -> null;
            default -> throw new AssertionError(m.getName());
        });
        ReflectionTestUtils.setField(service,"revokedSessions",repository);
        String token=service.login("test-admin","test-only-password-A").orElseThrow();assertTrue(service.expiresAt(token)>0);
        service.revoke(token);assertFalse(service.isValidToken(token));assertTrue(hashes.stream().allMatch(h->h.length()==64));assertFalse(hashes.contains(token));
    }

}
