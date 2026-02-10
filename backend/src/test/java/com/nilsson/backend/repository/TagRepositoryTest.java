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

class TagRepositoryTest {

    private ImageRepository imageRepository;
    private TagRepository tagRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test-library.db").toFile();
        String connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        DataSource dataSource = new DriverManagerDataSource(connectionString);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String schema = new String(getClass().getClassLoader().getResourceAsStream("schema.sql").readAllBytes());
            stmt.executeUpdate(schema);
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
