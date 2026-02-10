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

class PinnedFolderRepositoryTest {

    private PinnedFolderRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String schema = new String(getClass().getClassLoader().getResourceAsStream("schema.sql").readAllBytes());
            stmt.executeUpdate(schema);
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
}
