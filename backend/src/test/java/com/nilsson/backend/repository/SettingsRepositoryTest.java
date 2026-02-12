package com.nilsson.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SettingsRepositoryTest is an integration test suite for the SettingsRepository, focusing on the
 * persistence and retrieval of application-wide configuration settings. It verifies that
 * key-value pairs are correctly stored in the SQLite database, that existing settings
 * are updated atomically using 'INSERT OR REPLACE' logic, and that the repository
 * correctly handles requests for non-existent keys by returning specified default
 * values. These tests ensure the reliability of the application's persistent
 * state management across restarts.
 */
class SettingsRepositoryTest {

    private SettingsRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-settings.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String schema = new String(getClass().getClassLoader().getResourceAsStream("schema.sql").readAllBytes());
            stmt.executeUpdate(schema);
        }

        repository = new SettingsRepository(dataSource);
    }

    @Test
    @DisplayName("set and get should persist and retrieve values")
    void testSetAndGet() {
        repository.set("theme", "dark");
        assertEquals("dark", repository.get("theme", "light"));
    }

    @Test
    @DisplayName("get should return default value if key is missing")
    void testGetDefault() {
        assertEquals("default_val", repository.get("non_existent", "default_val"));
    }

    @Test
    @DisplayName("set should update existing keys (INSERT OR REPLACE)")
    void testUpdate() {
        repository.set("last_folder", "/old/path");
        repository.set("last_folder", "/new/path");
        assertEquals("/new/path", repository.get("last_folder", ""));
    }
}