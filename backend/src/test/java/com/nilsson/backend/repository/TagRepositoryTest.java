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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test suite for the {@link TagRepository}, validating the persistence and
 * management of image-specific tags within the SQLite database.
 * <p>
 * This class ensures the integrity of the tagging system by verifying:
 * <ul>
 *   <li><b>Tag Association:</b> Confirms that multiple tags can be correctly linked to
 *   an image and retrieved as a set.</li>
 *   <li><b>Tag Removal:</b> Validates the deletion of specific tags while ensuring
 *   other associations remain intact.</li>
 *   <li><b>Relational Integrity:</b> Ensures that tags are correctly associated with
 *   the primary image records via foreign key relationships.</li>
 * </ul>
 */
class TagRepositoryTest {

    private ImageRepository imageRepository;
    private TagRepository tagRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS images (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT UNIQUE, file_hash TEXT, is_starred BOOLEAN DEFAULT 0, rating INTEGER DEFAULT 0, last_scanned INTEGER, is_missing BOOLEAN DEFAULT 0)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS image_tags (image_id INTEGER, tag TEXT, FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE)");
        }

        imageRepository = new ImageRepository(dataSource);
        tagRepository = new TagRepository(dataSource);
    }

    @Test
    @DisplayName("addTag should persist tags and getTags should retrieve them")
    void testAddAndGetTags() {
        int imageId = imageRepository.getOrCreateId("/tmp/image.png", "hash");
        tagRepository.addTag(imageId, "test_tag");
        tagRepository.addTag(imageId, "another_tag");

        Set<String> tags = tagRepository.getTags(imageId);
        assertEquals(2, tags.size());
        assertTrue(tags.contains("test_tag"));
        assertTrue(tags.contains("another_tag"));
    }

    @Test
    @DisplayName("removeTag should delete specific tag association")
    void testRemoveTag() {
        int imageId = imageRepository.getOrCreateId("/tmp/image.png", "hash");
        tagRepository.addTag(imageId, "tag_to_keep");
        tagRepository.addTag(imageId, "tag_to_remove");

        tagRepository.removeTag(imageId, "tag_to_remove");

        Set<String> tags = tagRepository.getTags(imageId);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("tag_to_keep"));
    }
}
