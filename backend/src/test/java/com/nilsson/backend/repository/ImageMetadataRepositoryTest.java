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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for the {@link ImageMetadataRepository}, validating the storage
 * and retrieval of technical generation parameters.
 * <p>
 * This class ensures the integrity of the metadata caching layer by verifying:
 * <ul>
 *   <li><b>Key-Value Persistence:</b> Confirms that complex metadata maps (e.g., prompts,
 *   models) are correctly associated with image records and retrieved accurately.</li>
 *   <li><b>Existence Checks:</b> Validates the {@code hasMetadata} logic used to optimize
 *   I/O by preventing redundant extractions.</li>
 *   <li><b>Distinct Value Discovery:</b> Ensures that the repository can correctly identify
 *   unique values for specific keys (e.g., Samplers, Models), which is critical for
 *   populating UI search filters.</li>
 * </ul>
 */
class ImageMetadataRepositoryTest {

    private ImageRepository imageRepository;
    private ImageMetadataRepository metadataRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, rating INTEGER DEFAULT 0, last_scanned INTEGER, is_missing BOOLEAN DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS image_metadata (image_id INTEGER, key TEXT, value TEXT, FOREIGN KEY(image_id) REFERENCES images(id))");
        }

        imageRepository = new ImageRepository(dataSource);
        metadataRepository = new ImageMetadataRepository(dataSource);
    }

    @Test
    @DisplayName("saveMetadata should persist key-value pairs and getMetadata should retrieve them")
    void testSaveAndGetMetadata() {
        int imageId = imageRepository.getOrCreateId("/tmp/image.png", "hash");
        Map<String, String> metadata = Map.of("Prompt", "a test prompt", "Model", "test_model");

        metadataRepository.saveMetadata(imageId, metadata);

        assertTrue(metadataRepository.hasMetadata(imageId));
        Map<String, String> retrieved = metadataRepository.getMetadata(imageId);
        assertEquals(metadata, retrieved);
    }

    @Test
    @DisplayName("getDistinctValues should return unique values for a given metadata key")
    void testGetDistinctValues() {
        int imageId1 = imageRepository.getOrCreateId("/tmp/image1.png", "hash1");
        metadataRepository.saveMetadata(imageId1, Map.of("Sampler", "Euler"));

        int imageId2 = imageRepository.getOrCreateId("/tmp/image2.png", "hash2");
        metadataRepository.saveMetadata(imageId2, Map.of("Sampler", "DPM++"));

        int imageId3 = imageRepository.getOrCreateId("/tmp/image3.png", "hash3");
        metadataRepository.saveMetadata(imageId3, Map.of("Sampler", "Euler"));

        var distinctSamplers = metadataRepository.getDistinctValues("Sampler");
        assertEquals(2, distinctSamplers.size());
        assertTrue(distinctSamplers.contains("Euler"));
        assertTrue(distinctSamplers.contains("DPM++"));
    }
}
