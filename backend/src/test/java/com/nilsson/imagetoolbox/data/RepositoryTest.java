package com.nilsson.imagetoolbox.data;

import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.service.DatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 Integration test suite for the {@link ImageRepository} layer.
 */
class RepositoryTest {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryTest.class);

    private DatabaseService dbService;
    private ImageRepository repository;
    private Connection realConnection;
    private Connection proxyConnection;

    @BeforeEach
    void setUp() throws SQLException {
        String jdbcUrl = "jdbc:sqlite::memory:";

        realConnection = DriverManager.getConnection(jdbcUrl);

        proxyConnection = (Connection) Proxy.newProxyInstance(
                RepositoryTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return method.invoke(realConnection, args);
                });

        dbService = new DatabaseService(jdbcUrl) {
            @Override
            public Connection connect() throws SQLException {
                return proxyConnection;
            }

            @Override
            public void shutdown() {
                // No-op
            }
        };

        try (Connection conn = dbService.connect()) {
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE NOT NULL, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, last_scanned INTEGER, rating INTEGER DEFAULT 0)");
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS image_metadata (image_id INTEGER, key TEXT, value TEXT, FOREIGN KEY(image_id) REFERENCES images(id))");
            conn.createStatement().execute("CREATE VIRTUAL TABLE IF NOT EXISTS metadata_fts USING fts5(image_id UNINDEXED, global_text)");
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS image_tags (image_id INTEGER, tag TEXT, FOREIGN KEY(image_id) REFERENCES images(id))");
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS pinned_folders (path TEXT UNIQUE NOT NULL)");
        }

        repository = new ImageRepository(dbService);
    }

    @AfterEach
    void tearDown() {
        if (realConnection != null) {
            try {
                realConnection.close();
            } catch (SQLException e) {
                logger.error("Error closing test connection", e);
            }
        }
    }

    @Test
    void testCreateAndRetrieveImage() throws SQLException {
        String path = "/test/image.png";
        String hash = "abc123hash";

        int id = repository.getOrCreateId(path, hash);
        assertTrue(id > 0, "ID should be positive");

        int retrievedId = repository.getIdByPath(path);
        assertEquals(id, retrievedId, "Retrieved ID should match created ID");
    }

    @Test
    void testSaveAndRetrieveMetadata() throws SQLException {
        String path = "/test/meta.png";
        int id = repository.getOrCreateId(path, "hash_meta");

        Map<String, String> meta = new HashMap<>();
        meta.put("Model", "Flux");
        meta.put("Steps", "30");

        repository.saveMetadata(id, meta);

        Map<String, String> retrieved = repository.getMetadata(path);
        assertEquals("Flux", retrieved.get("Model"));
        assertEquals("30", retrieved.get("Steps"));
    }

    @Test
    void testSearchByMetadata() throws SQLException {
        int id1 = repository.getOrCreateId("/img1.png", "h1");
        repository.saveMetadata(id1, Map.of("Prompt", "A futuristic city"));

        int id2 = repository.getOrCreateId("/img2.png", "h2");
        repository.saveMetadata(id2, Map.of("Prompt", "A green forest"));

        List<String> results = repository.findPaths("futuristic", null, 10);
        assertEquals(1, results.size());
        assertEquals("/img1.png", results.get(0));

        results = repository.findPaths("forest", null, 10);
        assertEquals(1, results.size());
        assertEquals("/img2.png", results.get(0));
    }

    @Test
    void testUpdatePath() throws SQLException {
        String oldPath = "/old/path.png";
        String newPath = "/new/path.png";
        String hash = "move_hash";

        int id = repository.getOrCreateId(oldPath, hash);
        repository.saveMetadata(id, Map.of("Key", "Value"));

        repository.updatePath(oldPath, newPath);

        assertEquals(-1, repository.getIdByPath(oldPath), "Old path should no longer exist");
        assertEquals(id, repository.getIdByPath(newPath), "New path should map to the same ID");

        Map<String, String> meta = repository.getMetadata(newPath);
        assertEquals("Value", meta.get("Key"), "Metadata should persist after move");
    }

    @Test
    void testRatings() throws SQLException {
        String path = "/rated.png";
        int id = repository.getOrCreateId(path, "hash_rate");

        repository.setRating(id, 5);
        int rating = repository.getRating(path);

        assertEquals(5, rating);
    }
}
