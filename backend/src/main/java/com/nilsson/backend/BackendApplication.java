package com.nilsson.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * The entry point for the AIToolbox-Web backend application.
 * Configures and launches the Spring Boot application.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
