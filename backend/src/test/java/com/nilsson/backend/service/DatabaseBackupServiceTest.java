package com.nilsson.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link DatabaseBackupService}, validating the disaster recovery
 * and database snapshot engine.
 * <p>
 * This class ensures the reliability of the backup mechanism by verifying:
 * <ul>
 *   <li><b>Successful Snapshot:</b> Confirms that the service correctly executes the
 *   SQLite {@code VACUUM INTO} command to create a physical backup file.</li>
 *   <li><b>Path Resolution:</b> Validates that the backup is created in the correct
 *   directory relative to the application data root.</li>
 *   <li><b>Hot Backup Integrity:</b> Ensures that the backup file is a valid SQLite
 *   database that can be opened and queried.</li>
 * </ul>
 * The tests utilize a temporary file-based SQLite database to simulate a production
 * environment while maintaining test isolation.
 */
class DatabaseBackupServiceTest {

    private DatabaseBackupService backupService;
    private DataSource dataSource;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create a real source database file
        File dbFile = tempDir.resolve("source.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.dataSource = new DriverManagerDataSource(connectionString);

        // Seed some data so the backup isn't empty
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE test_data (id INTEGER PRIMARY KEY, val TEXT)");
            stmt.executeUpdate("INSERT INTO test_data (val) VALUES ('important user data')");
        }

        // Initialize service with temp directory as app data root
        backupService = new DatabaseBackupService(dataSource, tempDir.toString(), "backup.db.bak");
    }

    /**
     * Verifies that the performBackup method successfully creates a .bak file
     * containing the database state.
     */
    @Test
    @DisplayName("performBackup should create a valid backup file")
    void testPerformBackupSuccess() {
        backupService.performBackup();

        // The service creates a 'data' subfolder inside the appDataDir
        Path expectedBackupPath = tempDir.resolve("data").resolve("backup.db.bak");

        assertTrue(Files.exists(expectedBackupPath), "Backup file should be created");
        assertTrue(expectedBackupPath.toFile().length() > 0, "Backup file should not be empty");
        
        // Verify it's a valid SQLite database by attempting to connect to it
        DriverManagerDataSource backupDs = new DriverManagerDataSource("jdbc:sqlite:" + expectedBackupPath.toAbsolutePath());
        try (Connection conn = backupDs.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT val FROM test_data");
            assertTrue(rs.next());
            assertTrue(rs.getString("val").contains("important user data"));
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("Backup file is not a valid SQLite database: " + e.getMessage());
        }
    }

    /**
     * Verifies that the service handles existing backup files by overwriting them
     * (via deleteIfExists logic).
     */
    @Test
    @DisplayName("performBackup should overwrite existing backup files")
    void testOverwriteExistingBackup() throws Exception {
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path backupPath = dataDir.resolve("backup.db.bak");
        Files.writeString(backupPath, "old stale data");

        backupService.performBackup();

        assertTrue(Files.exists(backupPath));
        // If it was overwritten, it should now be a binary SQLite file, not the text we wrote
        assertTrue(backupPath.toFile().length() > "old stale data".length());
    }
}
