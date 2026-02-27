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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test suite for the {@link ImageRepository}, validating the persistence and
 * management of image records within the SQLite database.
 * <p>
 * This class ensures the integrity of the data access layer by verifying:
 * <ul>
 *   <li><b>Idempotent Registration:</b> Confirms that {@code getOrCreateId} correctly handles
 *   both new file registration and retrieval of existing records without duplication.</li>
 *   <li><b>Path Management:</b> Validates the atomic update of file paths during rename or
 *   move operations, ensuring metadata consistency.</li>
 *   <li><b>Special Character Handling:</b> Ensures that the repository safely handles complex
 *   file paths containing single quotes, emojis, and potential SQL injection patterns.</li>
 * </ul>
 * The tests utilize a temporary in-memory or file-based SQLite database to simulate
 * production-like interactions while maintaining test isolation.
 */
class ImageRepositoryTest {

    private DataSource dataSource;
    private ImageRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, rating INTEGER DEFAULT 0, last_scanned INTEGER, is_missing BOOLEAN DEFAULT 0)");
        }

        repository = new ImageRepository(dataSource);
    }

    @Test
    @DisplayName("getOrCreateId should register new paths and retrieve existing ones")
    void testGetOrCreateId_createsAndRetrieves() {
        String path = "/tmp/image1.png";
        String hash = "abc123hash";

        int id1 = repository.getOrCreateId(path, hash);
        assertTrue(id1 > 0, "A valid ID should be returned on creation");

        int id2 = repository.getOrCreateId(path, hash);
        assertEquals(id1, id2, "ID should remain consistent for the same path");
    }

    @Test
    @DisplayName("updatePath should correctly migrate record to new location")
    void testUpdatePath() {
        String oldPath = "/tmp/old.png";
        String newPath = "/tmp/new.png";
        String hash = "hash123";

        int id = repository.getOrCreateId(oldPath, hash);
        repository.updatePath(oldPath, newPath);

        assertEquals(id, repository.getIdByPath(newPath), "ID should be associated with the new path");
        assertEquals(-1, repository.getIdByPath(oldPath), "Old path should no longer be valid");
    }

    /**
     * Verifies that the repository can handle file paths containing single quotes,
     * which are common in media libraries and can cause SQL syntax errors if not
     * properly escaped via parameterized queries.
     */
    @Test
    @DisplayName("Repository should handle paths with single quotes")
    void testHandleSingleQuotes() {
        String path = "/images/O'Reilly_Book_Cover.png";
        String hash = "quote-hash";

        int id = repository.getOrCreateId(path, hash);
        assertTrue(id > 0);
        assertEquals(id, repository.getIdByPath(path));
    }

    /**
     * Verifies that the repository correctly persists and retrieves paths containing
     * multi-byte Unicode characters (emojis). This ensures full support for modern
     * file naming conventions.
     */
    @Test
    @DisplayName("Repository should handle paths with emojis")
    void testHandleEmojis() {
        String path = "/photos/vacation_🏖️_2024.jpg";
        String hash = "emoji-hash";

        int id = repository.getOrCreateId(path, hash);
        assertTrue(id > 0);
        assertEquals(id, repository.getIdByPath(path));
    }

    /**
     * Verifies that the repository is resilient to SQL injection attempts embedded
     * within file paths. Since the application uses parameterized queries via JdbcClient,
     * these should be treated as literal strings.
     */
    @Test
    @DisplayName("Repository should be resilient to SQL injection in paths")
    void testSqlInjectionResilience() {
        String maliciousPath = "/images/'; DROP TABLE images; --.png";
        String hash = "safe-hash";

        int id = repository.getOrCreateId(maliciousPath, hash);
        assertTrue(id > 0);
        assertEquals(id, repository.getIdByPath(maliciousPath));
        
        // Verify the table still exists by performing another operation
        assertTrue(repository.getOrCreateId("/safe/path.png", "hash") > 0);
    }
}
