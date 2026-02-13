package com.nilsson.backend.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.model.CreateCollectionRequest;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollectionRepositoryTest is an integration test suite for the CollectionRepository, focusing on the
 * persistence and complex relational logic of image collections. It verifies the management of
 * both static and smart collections, ensuring that membership associations, manual additions,
 * and blacklisted exclusions are correctly handled in the SQLite database. The tests also
 * validate the serialization of smart filtering criteria into JSON format, confirming
 * that dynamic collection rules are accurately preserved and retrieved.
 */
class CollectionRepositoryTest {

    private CollectionRepository repository;
    private ImageRepository imageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-collections.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Manually create tables with the latest schema including is_missing
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, rating INTEGER DEFAULT 0, last_scanned INTEGER, is_missing BOOLEAN DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS collections (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, created_at INTEGER, is_smart BOOLEAN DEFAULT FALSE, filters_json TEXT)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS collection_images (collection_id INTEGER, image_id INTEGER, added_at INTEGER, is_manual BOOLEAN DEFAULT 0, PRIMARY KEY (collection_id, image_id), FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE, FOREIGN KEY (image_id) REFERENCES images (id) ON DELETE CASCADE)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS collection_exclusions (collection_id INTEGER, image_id INTEGER, PRIMARY KEY (collection_id, image_id), FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE, FOREIGN KEY (image_id) REFERENCES images (id) ON DELETE CASCADE)");
        }

        repository = new CollectionRepository(dataSource, objectMapper);
        imageRepository = new ImageRepository(dataSource);
    }

    @Test
    @DisplayName("create and get should handle static collections")
    void testStaticCollection() {
        repository.create("Favorites", false, null);
        Optional<CreateCollectionRequest> details = repository.get("Favorites");

        assertTrue(details.isPresent());
        assertEquals("Favorites", details.get().name());
        assertFalse(details.get().isSmart());
    }

    @Test
    @DisplayName("addImage and getFilePaths should manage membership")
    void testMembership() {
        repository.create("MyColl", false, null);
        int id1 = imageRepository.getOrCreateId("/img1.png", "h1");
        int id2 = imageRepository.getOrCreateId("/img2.png", "h2");

        repository.addImage("MyColl", id1);
        repository.addImage("MyColl", id2);

        List<String> paths = repository.getFilePaths("MyColl");
        assertEquals(2, paths.size());
        assertTrue(paths.contains("/img1.png"));
    }

    @Test
    @DisplayName("addExclusion should hide images from collection")
    void testExclusion() {
        repository.create("MyColl", false, null);
        int id1 = imageRepository.getOrCreateId("/img1.png", "h1");
        repository.addImage("MyColl", id1);

        repository.addExclusion("MyColl", id1);

        List<String> paths = repository.getFilePaths("MyColl");
        assertTrue(paths.isEmpty(), "Excluded image should not be returned in file paths");
    }

    @Test
    @DisplayName("Smart collection should persist filters as JSON")
    void testSmartCollectionFilters() {
        CreateCollectionRequest.CollectionFilters filters = new CreateCollectionRequest.CollectionFilters(
                List.of("Flux"),
                List.of(),
                List.of("Euler"),
                "5",
                List.of("cat")
        );
        repository.create("SmartCats", true, filters);

        Optional<CreateCollectionRequest> details = repository.get("SmartCats");
        assertTrue(details.isPresent());
        assertTrue(details.get().isSmart());
        assertNotNull(details.get().filters());
        assertEquals(List.of("cat"), details.get().filters().prompt());
    }
}