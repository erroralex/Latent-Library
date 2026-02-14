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
 * This class provides persistent storage for key-value pairs extracted from image files,
 * such as generation parameters (Prompt, Sampler, Model, etc.). It manages the
 * {@code image_metadata} table, which allows for flexible, schema-less storage of
 * tool-specific metadata while maintaining a relational link to the core image record.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Metadata Persistence:</b> Saves a map of metadata keys and values for a specific
 *   image, ensuring old metadata is purged before new data is inserted.</li>
 *   <li><b>Efficient Retrieval:</b> Fetches all metadata for an image and reconstructs it
 *   into a {@link Map} for easy consumption by the service layer.</li>
 *   <li><b>Discovery:</b> Provides a mechanism to fetch distinct values for a specific
 *   metadata key (e.g., all unique Model names) to populate UI filters.</li>
 *   <li><b>Perceptual Hash Storage:</b> Manages the persistence of the 64-bit dHash
 *   within the core {@code images} table for similarity-based lookups.</li>
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

    public void saveDHash(int imageId, long dHash) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for dHash persistence.");
        }
        jdbcClient.sql("UPDATE images SET dhash = ? WHERE id = ?")
                .param(dHash)
                .param(imageId)
                .update();
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
