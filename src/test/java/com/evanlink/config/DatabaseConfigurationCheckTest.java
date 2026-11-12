package com.evanlink.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseConfigurationCheckTest {
    @Test void missingUsernameIsReportedWithoutLeakingValues() {
        var missing=DatabaseConfigurationCheck.missing(new MockEnvironment().withProperty("spring.datasource.password","private-test-value"));
        assertTrue(missing.contains("SPRING_DATASOURCE_USERNAME"));assertFalse(missing.toString().contains("private-test-value"));
    }
    @Test void explicitlyConfiguredLocalEmptyPasswordIsAllowed() {
        assertTrue(DatabaseConfigurationCheck.missing(new MockEnvironment().withProperty("spring.datasource.username","local-test-user").withProperty("spring.datasource.password","")).isEmpty());
    }
}
