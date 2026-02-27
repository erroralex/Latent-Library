package com.nilsson.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Fundamental smoke test for the Spring Boot application context.
 * <p>
 * This class verifies that the application's dependency injection container, configuration
 * properties, and bean definitions are correctly wired and capable of bootstrapping.
 * It serves as the first line of defense against critical configuration errors that
 * would prevent the application from starting in a production environment.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.data.dir=.")
class BackendApplicationTests {

    @Test
    @DisplayName("Application Context should load without exceptions")
    void contextLoads() {
    }
}
