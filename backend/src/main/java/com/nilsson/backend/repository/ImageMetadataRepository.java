package com.nilsson.backend.repository;

import com.nilsson.backend.exception.ValidationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for managing granular technical metadata associated with images.
 * <p>
 * This class provides persistent storage for the key-value pairs extracted from image files,
 * such as generation parameters (e.g., "Steps", "Sampler", "CFG Scale"). It manages the
 * {@code image_metadata} table, which serves as the primary source for both detailed
 * metadata display in the UI and the generation of tokens for the SQLite FTS5 search index.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Metadata Persistence:</b> Saves a comprehensive map of metadata keys and values
 *   for a specific image ID, ensuring technical data is cached for rapid access.</li>
 *   <li><b>Atomic Updates:</b> Implements a transactional delete-then-insert strategy to
 *   ensure metadata consistency and prevent stale data during re-indexing.</li>
 *   <li><b>Retrieval:</b> Fetches all metadata associated with an image as a {@link Map},
 *   allowing for efficient rendering of technical details in the frontend.</li>
 *   <li><b>Discovery:</b> Provides distinct values for specific metadata keys (e.g., a list
 *   of all unique Models used) to dynamically populate UI filter menus.</li>
 * </ul>
 */
@Repository
public class ImageMetadataRepository {

    private final JdbcClient jdbcClient;

    public ImageMetadataRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public boolean hasMetadata(int imageId) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for metadata check.");
        }
        return jdbcClient.sql("SELECT 1 FROM image_metadata WHERE image_id = ? LIMIT 1")
                .param(imageId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public Map<String, String> getMetadata(int imageId) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for metadata retrieval.");
        }
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
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for metadata persistence.");
        }
        if (meta == null) {
            throw new ValidationException("Metadata map cannot be null.");
        }

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
        if (key == null || key.isBlank()) {
            throw new ValidationException("Metadata key is required to fetch distinct values.");
        }
        return jdbcClient.sql("SELECT DISTINCT value FROM image_metadata WHERE key = ? ORDER BY value ASC")
                .param(key)
                .query(String.class)
                .list()
                .stream()
                .filter(val -> val != null && !val.isBlank())
                .toList();
    }
}