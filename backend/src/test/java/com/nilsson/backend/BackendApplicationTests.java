package com.nilsson.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * BackendApplicationTests serves as a fundamental smoke test for the Spring Boot application.
 * Its primary purpose is to verify that the application context can be initialized successfully
 * with the 'test' profile active. This ensures that all bean definitions, configurations,
 * and dependencies are correctly wired, providing a baseline guarantee that the application
 * is capable of starting up without critical configuration errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    @DisplayName("Application Context should load without exceptions")
    void contextLoads() {
    }
}
