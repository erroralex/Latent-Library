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
 * Integration test suite for the {@link CollectionRepository}, validating the persistence
 * and relational logic for image collections.
 * <p>
 * This class ensures the integrity of the collection system by verifying:
 * <ul>
 *   <li><b>Static Collections:</b> Confirms the creation and retrieval of manual image
 *   groupings.</li>
 *   <li><b>Membership Management:</b> Validates the association of images with collections
 *   and the correct retrieval of file paths.</li>
 *   <li><b>Exclusion Logic:</b> Ensures that blacklisted images are correctly hidden
 *   from collection results.</li>
 *   <li><b>Smart Collections:</b> Validates the serialization and persistence of complex
 *   filtering criteria (JSON) for rule-based collections.</li>
 * </ul>
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
