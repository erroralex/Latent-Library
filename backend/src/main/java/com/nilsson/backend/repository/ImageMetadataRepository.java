package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for managing granular image metadata.
 * <p>
 * This class provides persistent storage for the key-value pairs extracted from image files. It manages
 * the {@code image_metadata} table, which stores technical generation parameters (e.g., "Steps",
 * "Sampler", "CFG Scale"). This data is used for both detailed display in the UI and as the
 * source for the FTS5 search index.
 * <p>
 * Key functionalities:
 * - Metadata Persistence: Saves a map of metadata keys and values for a specific image ID.
 * - Atomic Updates: Implements a delete-then-insert strategy within a transaction to ensure metadata consistency.
 * - Retrieval: Fetches all metadata associated with an image as a {@code Map<String, String>}.
 * - Discovery: Provides distinct values for specific metadata keys to support frontend filter menus.
 */
@Repository
public class ImageMetadataRepository {

    private final JdbcClient jdbcClient;

    public ImageMetadataRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public boolean hasMetadata(int imageId) {
        return jdbcClient.sql("SELECT 1 FROM image_metadata WHERE image_id = ? LIMIT 1")
                .param(imageId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public Map<String, String> getMetadata(int imageId) {
        return jdbcClient.sql("SELECT key, value FROM image_metadata WHERE image_id = ?")
                .param(imageId)
                .query(rs -> {
                    Map<String, String> meta = new HashMap<>();
                    while (rs.next()) {
                        meta.put(rs.getString("key"), rs.getString("value"));
                    }
                    return meta;
                });
    }

    @Transactional
    public void saveMetadata(int imageId, Map<String, String> meta) {
        jdbcClient.sql("DELETE FROM image_metadata WHERE image_id = ?")
                .param(imageId)
                .update();

        for (Map.Entry<String, String> entry : meta.entrySet()) {
            jdbcClient.sql("INSERT INTO image_metadata(image_id, key, value) VALUES(?, ?, ?)")
                    .param(imageId)
                    .param(entry.getKey())
                    .param(entry.getValue())
                    .update();
        }
    }

    public List<String> getDistinctValues(String key) {
        return jdbcClient.sql("SELECT DISTINCT value FROM image_metadata WHERE key = ? ORDER BY value ASC")
                .param(key)
                .query(String.class)
                .list()
                .stream()
                .filter(val -> val != null && !val.isBlank())
                .toList();
    }
}
