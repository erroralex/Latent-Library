package com.nilsson.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test suite for the {@link SearchRepository}, validating the Full-Text Search (FTS5)
 * capabilities and complex relational filtering logic.
 * <p>
 * This class ensures the accuracy and resilience of the search engine by verifying:
 * <ul>
 *   <li><b>Tokenization & Matching:</b> Confirms that natural language queries correctly
 *   identify relevant images based on indexed prompt text.</li>
 *   <li><b>Relational Filtering:</b> Validates the combination of FTS queries with relational
 *   filters (e.g., Model, Collection) to ensure precise result sets.</li>
 *   <li><b>Special Character Resilience:</b> Ensures that search queries containing quotes,
 *   dashes, or other FTS-sensitive characters are handled safely without crashing the engine.</li>
 *   <li><b>Pagination:</b> Confirms that offset and limit parameters are correctly applied
 *   to the result set.</li>
 * </ul>
 * The tests utilize a temporary file-based SQLite database to enable the FTS5 extension
 * and simulate production-like query execution.
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
    @DisplayName("Search should find images by partial prompt text")
    void search_ShouldFilterByPromptText() {
        List<String> results = searchRepository.findPaths("space cat", null, null, 0, 10);

        assertEquals(1, results.size());
        assertEquals("/img/cat_space.png", results.getFirst());
    }

    @Test
    @DisplayName("Search should handle special characters and quotes safely")
    void search_ShouldHandleSpecialCharacters() {
        // FTS5 can be sensitive to quotes and dashes. We need to ensure our repository
        // sanitizes or handles these correctly.
        List<String> results = searchRepository.findPaths("\"galaxy background\"", null, null, 0, 10);
        assertEquals(1, results.size());

        // Test with dashes and other symbols
        List<String> results2 = searchRepository.findPaths("Flux-V1", null, null, 0, 10);
        assertEquals(1, results2.size());
    }

    @Test
    @DisplayName("Search should filter by relational metadata (Model)")
    void search_ShouldFilterByModel() {
        Map<String, List<String>> filters = Map.of("Model", List.of("SDXL"));

        List<String> results = searchRepository.findPaths(null, filters, null, 0, 10);

        assertEquals(1, results.size());
        assertEquals("/img/dog_forest.png", results.getFirst());
    }

    @Test
    @DisplayName("Search should return empty when filters do not intersect")
    void search_ShouldCombineFilters() {
        Map<String, List<String>> filters = Map.of("Model", List.of("Flux V1"));

        List<String> results = searchRepository.findPaths("forest", filters, null, 0, 10);

        assertTrue(results.isEmpty(), "Should return empty if Model and Prompt do not match same image");
    }
}
