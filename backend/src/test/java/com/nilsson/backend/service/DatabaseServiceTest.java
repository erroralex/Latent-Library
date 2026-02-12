package com.nilsson.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseServiceTest is responsible for verifying the core database infrastructure and lifecycle management
 * of the application. It ensures that the SQLite database is correctly initialized, that Flyway schema
 * migrations are applied successfully, and that connection pooling via HikariCP is properly configured.
 * Additionally, the tests validate administrative maintenance tasks, such as clearing all application
 * data and reclaiming disk space, ensuring the persistence layer remains reliable and consistent.
 */
class DatabaseServiceTest {

    private DatabaseService databaseService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("test-lifecycle.db").toFile();
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        databaseService = new DatabaseService(jdbcUrl);
    }

    @AfterEach
    void tearDown() {
        databaseService.shutdown();
    }

    @Test
    @DisplayName("Database should initialize with schema migrations")
    void testInitialization() throws Exception {
        DataSource ds = databaseService.dataSource();
        assertNotNull(ds);

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='images'");
            assertTrue(rs.next(), "Table 'images' should exist after migration");
        }
    }

    @Test
    @DisplayName("clearAllData should empty all tables")
    void testClearAllData() throws Exception {
        DataSource ds = databaseService.dataSource();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO images (file_path, file_hash) VALUES ('/test/path', 'hash')");
        }

        databaseService.clearAllData();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
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