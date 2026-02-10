package com.nilsson.backend.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageRepositoryTest {

    private DataSource dataSource;
    private ImageRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource(connectionString);
        this.dataSource = driverManagerDataSource;

        // Manually create schema
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String schema = new String(getClass().getClassLoader().getResourceAsStream("schema.sql").readAllBytes());
            stmt.executeUpdate(schema);
        }

        repository = new ImageRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        // In-memory or temp file DB will be cleaned up automatically.
    }

    @Test
    void testGetOrCreateId_createsAndRetrieves() {
        String path = "/tmp/image1.png";
        String hash = "abc123hash";

        int id1 = repository.getOrCreateId(path, hash);
        assertTrue(id1 > 0, "A valid ID should be returned on creation");

        int id2 = repository.getOrCreateId(path, hash);
        assertEquals(id1, id2, "ID should remain consistent for the same path");
    }

    @Test
    void testUpdatePath() {
        String oldPath = "/tmp/old.png";
        String newPath = "/tmp/new.png";
        String hash = "hash123";

        int id = repository.getOrCreateId(oldPath, hash);
        repository.updatePath(oldPath, newPath);

        assertEquals(id, repository.getIdByPath(newPath), "ID should be associated with the new path");
        assertEquals(-1, repository.getIdByPath(oldPath), "Old path should no longer be valid");
    }
}
