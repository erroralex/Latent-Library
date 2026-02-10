package com.nilsson.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * The entry point and configuration hub for the AIToolbox-Web backend application.
 * <p>
 * This class initializes the Spring Boot framework and bootstraps the application context.
 * It is configured to exclude {@code DataSourceAutoConfiguration} to allow for a custom,
 * portable SQLite database setup managed by {@code DatabaseService}. The application
 * leverages modern Java 21 features, including Virtual Threads for high-concurrency
 * I/O operations related to image indexing and metadata extraction.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
