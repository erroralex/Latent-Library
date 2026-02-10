package com.nilsson.backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * Repository for persistent storage and retrieval of application-wide settings.
 * Provides a key-value abstraction over the settings database table.
 * Handles user preferences, application state, and configuration parameters.
 */
@Repository
public class SettingsRepository {

    private static final Logger logger = LoggerFactory.getLogger(SettingsRepository.class);
    private final JdbcClient jdbcClient;

    public SettingsRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public String get(String key, String defaultValue) {
        return jdbcClient.sql("SELECT value FROM settings WHERE key = ?")
                .param(key)
                .query(String.class)
                .optional()
                .orElse(defaultValue);
    }

    public void set(String key, String value) {
        jdbcClient.sql("INSERT OR REPLACE INTO settings(key, value) VALUES(?, ?)")
                .param(key)
                .param(value)
                .update();
    }
}
