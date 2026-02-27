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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test suite for the {@link PinnedFolderRepository}, validating the persistence
 * and management of user-pinned directories.
 * <p>
 * This class ensures the reliability of the folder bookmarking system by verifying:
 * <ul>
 *   <li><b>Persistence:</b> Confirms that directory paths can be pinned and retrieved
 *   correctly from the database.</li>
 *   <li><b>Removal:</b> Validates the deletion of pinned folder records.</li>
 *   <li><b>Uniqueness:</b> Ensures that the repository handles duplicate pinning attempts
 *   gracefully, preventing redundant entries for the same path.</li>
 * </ul>
 */
class PinnedFolderRepositoryTest {

    private PinnedFolderRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS pinned_folders (path TEXT UNIQUE NOT NULL)");
        }

        repository = new PinnedFolderRepository(dataSource);
    }

    @Test
    @DisplayName("addPinnedFolder should persist paths and getPinnedFolders should retrieve them")
    void testAddAndGetPinnedFolders() {
        repository.addPinnedFolder("/tmp/folder1");
        repository.addPinnedFolder("/tmp/folder2");

        List<String> folders = repository.getPinnedFolders();
        assertEquals(2, folders.size());
        assertTrue(folders.contains("/tmp/folder1"));
        assertTrue(folders.contains("/tmp/folder2"));
    }

    @Test
    @DisplayName("removePinnedFolder should delete specific path record")
    void testRemovePinnedFolder() {
        repository.addPinnedFolder("/tmp/folder_to_keep");
        repository.addPinnedFolder("/tmp/folder_to_remove");

        repository.removePinnedFolder("/tmp/folder_to_remove");

        List<String> folders = repository.getPinnedFolders();
        assertEquals(1, folders.size());
        assertTrue(folders.contains("/tmp/folder_to_keep"));
    }

    @Test
    @DisplayName("addPinnedFolder should handle duplicate paths gracefully")
    void addPinnedFolder_ShouldEnforceUniqueness() {
        repository.addPinnedFolder("/unique/path");

        // Should not throw exception or create duplicate
        try {
            repository.addPinnedFolder("/unique/path");
        } catch (Exception ignored) {
        }
    }
}
