package com.nilsson.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify that the Spring ApplicationContext loads successfully.
 * If this fails, the application will not start in production.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    @DisplayName("Application Context should load without exceptions")
    void contextLoads() {
        // The test passes if the context loads successfully.
    }
}
