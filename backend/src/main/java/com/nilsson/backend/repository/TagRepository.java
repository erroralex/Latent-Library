package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

/**
 * Repository for managing user-defined tags associated with images.
 * <p>
 * This class provides persistent storage operations for the many-to-many relationship between images and tags.
 * It utilizes the {@code image_tags} table to store unique tag strings per image ID. Tags are used to
 * augment the searchability of images beyond their embedded metadata and are indexed in the FTS5 system.
 * <p>
 * Key functionalities:
 * - Tag Association: Adds new tags to an image while preventing duplicates via {@code INSERT OR IGNORE}.
 * - Tag Removal: Deletes specific tag associations for a given image.
 * - Retrieval: Fetches the complete set of tags for a specific image ID.
 */
@Repository
public class TagRepository {

    private final JdbcClient jdbcClient;

    public TagRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public void addTag(int imageId, String tag) {
        jdbcClient.sql("INSERT OR IGNORE INTO image_tags(image_id, tag) VALUES(?, ?)")
                .param(imageId)
                .param(tag)
                .update();
    }

    public void removeTag(int imageId, String tag) {
        jdbcClient.sql("DELETE FROM image_tags WHERE image_id = ? AND tag = ?")
                .param(imageId)
                .param(tag)
                .update();
    }

    public Set<String> getTags(int imageId) {
        return new HashSet<>(jdbcClient.sql("SELECT tag FROM image_tags WHERE image_id = ?")
                .param(imageId)
                .query(String.class)
                .list());
    }
}
