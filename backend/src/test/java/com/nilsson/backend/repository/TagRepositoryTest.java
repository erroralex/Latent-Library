package com.nilsson.backend.repository;

import org.junit.jupiter.api.BeforeEach;
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
 * TagRepositoryTest provides integration tests for the TagRepository, verifying the
 * persistence and retrieval of image tags in the SQLite database. It covers scenarios
 * such as adding multiple tags to an image, retrieving the set of tags associated
 * with a specific image ID, and removing individual tags, ensuring that the
 * many-to-many relationship between images and tags is correctly managed.
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