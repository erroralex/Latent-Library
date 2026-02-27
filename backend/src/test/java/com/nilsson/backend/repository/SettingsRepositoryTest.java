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
 * Integration test suite for the {@link SettingsRepository}, validating the persistence and
 * retrieval of application-wide configuration settings.
 * <p>
 * This class ensures the reliability of the application's persistent state by verifying:
 * <ul>
 *   <li><b>Key-Value Persistence:</b> Confirms that settings are correctly stored and
 *   retrieved from the database.</li>
 *   <li><b>Atomic Updates:</b> Validates the {@code INSERT OR REPLACE} logic, ensuring
 *   that existing settings are updated without duplication.</li>
 *   <li><b>Default Fallbacks:</b> Ensures that the repository correctly returns provided
 *   default values when a requested key is missing.</li>
 * </ul>
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
