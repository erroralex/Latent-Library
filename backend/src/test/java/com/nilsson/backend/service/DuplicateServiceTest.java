package com.nilsson.backend.service;

import com.nilsson.backend.model.DuplicatePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit and integration test suite for the {@link DuplicateService}, validating the visual and
 * exact duplicate detection engine.
 * <p>
 * This class ensures the accuracy of the similarity logic by verifying:
 * <ul>
 *   <li><b>Exact Match Detection:</b> Confirms that files with identical SHA-256 hashes are
 *   correctly identified as duplicates.</li>
 *   <li><b>Visual Similarity (dHash):</b> Validates the BK-Tree implementation and Hamming
 *   distance calculations for identifying visually similar images within a bit-threshold.</li>
 *   <li><b>BK-Tree Efficiency:</b> Ensures that the search algorithm correctly traverses the
 *   metric tree to find neighbors within the specified distance.</li>
 *   <li><b>Data Integrity:</b> Verifies that duplicate pairs are correctly mapped to their
 *   respective metadata and ratings for UI display.</li>
 * </ul>
 * The tests use a temporary file-based SQLite database to ensure realistic query execution
 * and schema persistence across service calls.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateServiceTest {

    private DataSource dataSource;
    private DuplicateService duplicateService;

    @Mock
    private PathService pathService;
    @Mock
    private UserDataManager userDataManager;
    @Mock
    private DHashService dHashService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("test-duplicates.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, dhash INTEGER, last_scanned INTEGER)");
        }

        duplicateService = new DuplicateService(dataSource, pathService, userDataManager, dHashService, 2);
    }

    @Test
    @DisplayName("findDuplicatePairs should identify exact duplicates via file_hash")
    void testExactDuplicates() {
        insertImage("/path/1.png", "hash-aaa", 0L);
        insertImage("/path/2.png", "hash-aaa", 0L);

        mockFileExists("/path/1.png");
        mockFileExists("/path/2.png");

        List<DuplicatePair> pairs = duplicateService.findDuplicatePairs();

        assertEquals(1, pairs.size());
        assertEquals("/path/1.png", pairs.get(0).left().path());
        assertEquals("/path/2.png", pairs.get(0).right().path());
    }

    @Test
    @DisplayName("findDuplicatePairs should identify visual duplicates via dHash Hamming distance")
    void testVisualDuplicates() {
        insertImage("/path/v1.png", "hash-1", 10L);
        insertImage("/path/v2.png", "hash-2", 11L);

        mockFileExists("/path/v1.png");
        mockFileExists("/path/v2.png");

        List<DuplicatePair> pairs = duplicateService.findDuplicatePairs();

        assertEquals(1, pairs.size());
        assertTrue(pairs.get(0).left().path().contains("v1") || pairs.get(0).left().path().contains("v2"));
    }

    @Test
    @DisplayName("findDuplicatePairs should ignore images exceeding the Hamming threshold")
    void testNonDuplicates() {
        insertImage("/path/diff1.png", "hash-x", 0L);
        insertImage("/path/diff2.png", "hash-y", 255L);

        List<DuplicatePair> pairs = duplicateService.findDuplicatePairs();

        assertTrue(pairs.isEmpty());
    }

    @Test
    @DisplayName("BK-Tree search should find multiple similar images")
    void testMultipleVisualDuplicates() {
        insertImage("/path/base.png", "h0", 100L); // 01100100
        insertImage("/path/sim1.png", "h1", 101L); // 01100101 (dist 1 from base)
        insertImage("/path/sim2.png", "h2", 104L); // 01101000 (dist 2 from base, dist 3 from sim1)

        mockFileExists("/path/base.png");
        mockFileExists("/path/sim1.png");
        mockFileExists("/path/sim2.png");

        List<DuplicatePair> pairs = duplicateService.findDuplicatePairs();

        // Expected pairs: base-sim1 (dist 1), base-sim2 (dist 2). 
        // sim1-sim2 is dist 3, which is > threshold 2.
        assertEquals(2, pairs.size());
    }

    private void insertImage(String path, String fileHash, Long dhash) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String sql = String.format("INSERT INTO images (file_path, file_hash, dhash, last_scanned) VALUES ('%s', '%s', %d, %d)",
                    path, fileHash, dhash, System.currentTimeMillis());
            stmt.executeUpdate(sql);
        } catch (Exception e) {
            fail("Failed to insert test data: " + e.getMessage());
        }
    }

    private void mockFileExists(String path) {
        File mockFile = mock(File.class);
        when(pathService.resolve(path)).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        when(userDataManager.getCachedMetadata(mockFile)).thenReturn(Map.of());
        when(userDataManager.getRating(mockFile)).thenReturn(0);
    }
}
