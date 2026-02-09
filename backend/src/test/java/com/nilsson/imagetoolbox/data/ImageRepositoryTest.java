package com.nilsson.imagetoolbox.data;

import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.service.DatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 Integration tests for {@link ImageRepository} using an ephemeral SQLite instance.
 */
class ImageRepositoryTest {

    private DatabaseService dbService;
    private ImageRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        dbService = new DatabaseService(connectionString);
        repository = new ImageRepository(dbService);
    }

    @AfterEach
    void tearDown() {
        if (dbService != null) {
            dbService.shutdown();
        }
    }

    @Test
    void testGetOrCreateId_createsAndRetrieves() throws SQLException {
        String path = "/tmp/image1.png";
        String hash = "abc123hash";

        // 1. Create
        int id1 = repository.getOrCreateId(path, hash);
        assertTrue(id1 > 0);

        // 2. Retrieve existing
        int id2 = repository.getOrCreateId(path, hash);
        assertEquals(id1, id2, "ID should remain consistent for same path");
    }

    @Test
    void testMetadataSavingAndSearch() throws SQLException {
        int id = repository.getOrCreateId("/tmp/robot.png", "hash1");

        Map<String, String> meta = new HashMap<>();
        meta.put("Model", "Stable Diffusion XL");
        meta.put("Prompt", "A futuristic robot");

        repository.saveMetadata(id, meta);

        // Test Filter Map search
        Map<String, String> filters = new HashMap<>();
        filters.put("Model", "Stable Diffusion XL");

        List<String> results = repository.findPaths("", filters, 10);
        assertEquals(1, results.size());
        assertEquals("/tmp/robot.png", results.get(0));
    }
}
