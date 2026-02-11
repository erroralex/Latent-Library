package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

/**
 * Repository for managing user-defined tags associated with images.
 * <p>
 * This class provides persistent storage operations for the many-to-many relationship between images
 * and tags. It utilizes the {@code image_tags} table to store unique tag strings per image ID.
 * Tags are used to augment the searchability of images beyond their embedded technical metadata
 * and are integrated into the SQLite FTS5 search index.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Tag Association:</b> Persists new tags for an image, utilizing {@code INSERT OR IGNORE}
 *   to prevent duplicate entries for the same image-tag pair.</li>
 *   <li><b>Tag Removal:</b> Deletes specific tag associations, allowing users to refine their
 *   image organization.</li>
 *   <li><b>Retrieval:</b> Fetches the complete set of tags for a specific image ID, returning
 *   them as a {@link Set} for efficient membership checks.</li>
 * </ul>
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
