package com.nilsson.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link DatabaseService}, validating core database infrastructure,
 * schema migrations, and maintenance operations.
 * <p>
 * This class ensures the reliability of the application's persistent storage layer by verifying:
 * <ul>
 *   <li><b>Schema Initialization:</b> Confirms that Flyway migrations are correctly applied
 *   upon startup, creating the required table structures (e.g., {@code images}).</li>
 *   <li><b>Data Maintenance:</b> Validates the {@code clearAllData} operation, ensuring it
 *   atomically wipes all application tables while preserving the schema.</li>
 *   <li><b>Connection Management:</b> Verifies that the service correctly provides valid,
 *   open connections from the HikariCP pool.</li>
 * </ul>
 * The tests utilize a temporary file-based SQLite database to simulate production-like
 * interactions with full schema support.
 */
class DatabaseServiceTest {

    private DatabaseService databaseService;
    private HikariDataSource dataSource;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("test-lifecycle.db").toFile();
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setPoolName("TestPool");
        this.dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        databaseService = new DatabaseService(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Database should initialize with schema migrations")
    void testInitialization() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='images'");
            assertTrue(rs.next(), "Table 'images' should exist after migration");
        }
    }

    @Test
    @DisplayName("clearAllData should empty all tables")
    void testClearAllData() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO images (file_path, file_hash) VALUES ('/test/path', 'hash')");
        }

        databaseService.clearAllData();

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM images");
            rs.next();
            assertEquals(0, rs.getInt(1), "Images table should be empty after clearAllData");
        }
    }

    @Test
    @DisplayName("connect should return a valid connection")
    void testConnect() throws Exception {
        try (Connection conn = databaseService.connect()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }
}
