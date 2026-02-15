package com.nilsson.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * General application configuration, including versioning and metadata.
 */
@Configuration
public class AppConfig {

    @Value("${project.version:0.0.1-SNAPSHOT}")
    private String version;

    @Bean
    public Map<String, String> appMetadata() {
        return Map.of("version", version);
    }
}
