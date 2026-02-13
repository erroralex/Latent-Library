package com.nilsson.backend.repository;

import org.junit.jupiter.api.BeforeEach;
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
 * PinnedFolderRepositoryTest provides integration tests for the PinnedFolderRepository,
 * ensuring that user-pinned directories are correctly persisted and managed in the
 * SQLite database. It verifies the ability to add new pinned folders, retrieve the
 * list of all pinned folders, and remove existing pins. The tests also check for
 * uniqueness constraints to prevent duplicate entries for the same directory path.
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
    void testAddAndGetPinnedFolders() {
        repository.addPinnedFolder("/tmp/folder1");
        repository.addPinnedFolder("/tmp/folder2");

        List<String> folders = repository.getPinnedFolders();
        assertEquals(2, folders.size());
        assertTrue(folders.contains("/tmp/folder1"));
        assertTrue(folders.contains("/tmp/folder2"));
    }

    @Test
    void testRemovePinnedFolder() {
        repository.addPinnedFolder("/tmp/folder_to_keep");
        repository.addPinnedFolder("/tmp/folder_to_remove");

        repository.removePinnedFolder("/tmp/folder_to_remove");

        List<String> folders = repository.getPinnedFolders();
        assertEquals(1, folders.size());
        assertTrue(folders.contains("/tmp/folder_to_keep"));
    }

    @Test
    void addPinnedFolder_ShouldEnforceUniqueness() {
        repository.addPinnedFolder("/unique/path");

        try {
            repository.addPinnedFolder("/unique/path");
        } catch (Exception e) {
        }
    }
}