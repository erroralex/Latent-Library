package com.nilsson.backend.repository;

import com.nilsson.backend.model.ImageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SearchRepositoryTest provides integration tests for the SearchRepository, specifically
 * focusing on the Full-Text Search (FTS5) capabilities and complex filtering logic.
 * It verifies that images can be retrieved based on prompt text, model names, and
 * combinations of multiple metadata filters. The tests simulate a realistic environment
 * by creating temporary SQLite databases with FTS virtual tables and seeding them
 * with sample image data and metadata.
 */
class SearchRepositoryTest {

    private SearchRepository searchRepository;
    private ImageRepository imageRepository;
    private ImageMetadataRepository metadataRepository;

    @BeforeEach
    void setUp() throws Exception {
        Path tempDb = Files.createTempFile("test-search", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDb.toAbsolutePath());

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, rating INTEGER DEFAULT 0, last_scanned INTEGER, is_missing BOOLEAN DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS image_metadata (image_id INTEGER, key TEXT, value TEXT, FOREIGN KEY(image_id) REFERENCES images(id))");
            stmt.executeUpdate("CREATE VIRTUAL TABLE IF NOT EXISTS metadata_fts USING fts5(image_id UNINDEXED, global_text)");
        }

        imageRepository = new ImageRepository(dataSource);
        metadataRepository = new ImageMetadataRepository(dataSource);
        searchRepository = new SearchRepository(dataSource);

        seedData();
    }

    private void seedData() {
        int id1 = imageRepository.getOrCreateId("/img/cat_space.png", "hash1");
        metadataRepository.saveMetadata(id1, Map.of(
                "Prompt", "A cat floating in space, galaxy background",
                "Model", "Flux V1",
                "Seed", "12345"
        ));
        addToFts(id1, "Prompt: A cat floating in space, galaxy background Model: Flux V1 Seed: 12345");

        int id2 = imageRepository.getOrCreateId("/img/dog_forest.png", "hash2");
        metadataRepository.saveMetadata(id2, Map.of(
                "Prompt", "A dog running in a dark forest",
                "Model", "SDXL",
                "Seed", "67890"
        ));
        addToFts(id2, "Prompt: A dog running in a dark forest Model: SDXL Seed: 67890");
    }

    private void addToFts(int imageId, String content) {
        try {
            java.lang.reflect.Field clientField = SearchRepository.class.getDeclaredField("jdbcClient");
            clientField.setAccessible(true);
            var client = (org.springframework.jdbc.core.simple.JdbcClient) clientField.get(searchRepository);

            client.sql("INSERT INTO metadata_fts(image_id, global_text) VALUES(?, ?)")
                    .param(imageId)
                    .param(content)
                    .update();
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed FTS data", e);
        }
    }

    @Test
    void search_ShouldFilterByPromptText() {
        List<String> results = searchRepository.findPaths("space cat", null, 0, 10);

        assertEquals(1, results.size());
        assertEquals("/img/cat_space.png", results.getFirst());
    }

    @Test
    void search_ShouldFilterByModel() {
        Map<String, List<String>> filters = Map.of("Model", List.of("SDXL"));

        List<String> results = searchRepository.findPaths(null, filters, 0, 10);

        assertEquals(1, results.size());
        assertEquals("/img/dog_forest.png", results.getFirst());
    }

    @Test
    void search_ShouldCombineFilters() {
        Map<String, List<String>> filters = Map.of("Model", List.of("Flux V1"));

        List<String> results = searchRepository.findPaths("forest", filters, 0, 10);

        assertTrue(results.isEmpty(), "Should return empty if Model and Prompt do not match same image");
    }
}
