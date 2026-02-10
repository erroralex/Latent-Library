package com.nilsson.backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * Repository for persistent storage and retrieval of application-wide settings and user preferences.
 * <p>
 * This class provides a simple key-value abstraction over the {@code settings} database table. It is
 * used to persist application state that must survive restarts, such as the last visited directory,
 * UI preferences, and tool-specific configurations (e.g., Speed Sorter target paths).
 * <p>
 * Key functionalities:
 * - Persistent Key-Value Store: Implements a generic mechanism for storing and retrieving string-based settings.
 * - Default Value Support: Provides a safe retrieval method that returns a fallback value if a key is missing.
 * - Atomic Updates: Uses {@code INSERT OR REPLACE} to ensure settings are updated or created efficiently.
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
