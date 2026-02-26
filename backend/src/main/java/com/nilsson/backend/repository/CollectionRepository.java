package com.nilsson.backend.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.CreateCollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for managing image collections and their relational associations.
 * <p>
 * This class handles the persistence of both "Static Collections" (manual associations) and
 * "Smart Collections" (dynamic, filter-based groupings). It manages the {@code collections}
 * table, which stores collection definitions and serialized JSON filters, and the
 * {@code collection_images} join table, which maintains the many-to-many relationship
 * between images and collections.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Collection CRUD:</b> Implements full lifecycle management for collection entities,
 *   including creation, updates, and deletion.</li>
 *   <li><b>Smart Filter Persistence:</b> Serializes and deserializes complex search criteria
 *   using Jackson to store dynamic collection rules in the database.</li>
 *   <li><b>Membership Management:</b> Handles adding images to collections, distinguishing
 *   between manual additions and smart population, and managing blacklisted exclusions.</li>
 *   <li><b>Path Resolution:</b> Retrieves absolute file system paths for all images within
 *   a specific collection, respecting user-defined exclusions.</li>
 *   <li><b>Atomic Operations:</b> Utilizes {@code INSERT OR IGNORE} and transactional updates
 *   to safely manage image associations and collection state.</li>
 * </ul>
 */
@Repository
public class CollectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(CollectionRepository.class);
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public CollectionRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.objectMapper = objectMapper;
    }

    public List<String> getAllNames() {
        return jdbcClient.sql("SELECT name FROM collections ORDER BY name ASC")
                .query(String.class)
                .list();
    }

    public Optional<CreateCollectionRequest> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT name, is_smart, filters_json FROM collections WHERE name = ?")
                .param(name.trim())
                .query((rs, rowNum) -> {
                    String colName = rs.getString("name");
                    boolean isSmart = rs.getBoolean("is_smart");
                    String filtersJson = rs.getString("filters_json");
                    CreateCollectionRequest.CollectionFilters filters = null;
                    if (filtersJson != null && !filtersJson.isBlank()) {
                        try {
                            filters = objectMapper.readValue(filtersJson, CreateCollectionRequest.CollectionFilters.class);
                        } catch (JsonProcessingException e) {
                            logger.error("Failed to parse filters for collection: {}", colName, e);
                            throw new ApplicationException("Error reading collection filter data from database", e);
                        }
                    }
                    return new CreateCollectionRequest(colName, isSmart, filters);
                })
                .optional();
    }

    public void create(String name, boolean isSmart, CreateCollectionRequest.CollectionFilters filters) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Collection name cannot be empty.");
        }

        String filtersJson = null;
        if (isSmart && filters != null) {
            try {
                filtersJson = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing collection filters for collection: {}", name, e);
                throw new ApplicationException("Failed to process collection filter parameters", e);
            }
        }

        jdbcClient.sql("INSERT OR IGNORE INTO collections(name, created_at, is_smart, filters_json) VALUES(?, ?, ?, ?)")
                .param(name.trim())
                .param(System.currentTimeMillis())
                .param(isSmart)
                .param(filtersJson)
                .update();
    }

    public void update(String oldName, String newName, boolean isSmart, CreateCollectionRequest.CollectionFilters filters) {
        if (oldName == null || oldName.isBlank()) {
            throw new ValidationException("Original collection name is required for update.");
        }
        if (newName == null || newName.isBlank()) {
            newName = oldName;
        }

        String filtersJson = null;
        if (isSmart && filters != null) {
            try {
                filtersJson = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing collection filters for collection: {}", newName, e);
                throw new ApplicationException("Failed to process collection filter parameters", e);
            }
        }

        jdbcClient.sql("UPDATE collections SET name = ?, is_smart = ?, filters_json = ? WHERE name = ?")
                .param(newName.trim())
                .param(isSmart)
                .param(filtersJson)
                .param(oldName.trim())
                .update();
    }

    public void delete(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Collection name is required for deletion.");
        }
        jdbcClient.sql("DELETE FROM collections WHERE name = ?")
                .param(name.trim())
                .update();
    }

    public void addImage(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required to add an image.");
        }
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at, is_manual) SELECT id, ?, ?, 1 FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(System.currentTimeMillis())
                .param(collectionName.trim())
                .update();
    }

    @Transactional
    public void addImages(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required to add images.");
        }
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        Integer collectionId = jdbcClient.sql("SELECT id FROM collections WHERE name = ?")
                .param(collectionName.trim())
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ValidationException("Collection not found: " + collectionName));

        long now = System.currentTimeMillis();
        int batchSize = 500;
        for (int i = 0; i < imageIds.size(); i += batchSize) {
            List<Integer> batch = imageIds.subList(i, Math.min(i + batchSize, imageIds.size()));

            StringBuilder sql = new StringBuilder("INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at, is_manual) VALUES ");
            List<Object> params = new java.util.ArrayList<>();

            for (int j = 0; j < batch.size(); j++) {
                sql.append("(?, ?, ?, 1)");
                if (j < batch.size() - 1) sql.append(", ");
                params.add(collectionId);
                params.add(batch.get(j));
                params.add(now);
            }

            jdbcClient.sql(sql.toString())
                    .params(params)
                    .update();
        }
    }

    @Transactional
    public void removeImages(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank() || imageIds == null || imageIds.isEmpty()) {
            return;
        }

        Integer collectionId = jdbcClient.sql("SELECT id FROM collections WHERE name = ?")
                .param(collectionName.trim())
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ValidationException("Collection not found: " + collectionName));

        int batchSize = 500;
        for (int i = 0; i < imageIds.size(); i += batchSize) {
            List<Integer> batch = imageIds.subList(i, Math.min(i + batchSize, imageIds.size()));
            String placeholders = batch.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "DELETE FROM collection_images WHERE collection_id = ? AND is_manual = 1 AND image_id IN (" + placeholders + ")";

            List<Object> params = new java.util.ArrayList<>();
            params.add(collectionId);
            params.addAll(batch);

            jdbcClient.sql(sql)
                    .params(params)
                    .update();
        }
    }

    public void removeAllImages(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required to clear images.");
        }
        String sql = "DELETE FROM collection_images WHERE collection_id = (SELECT id FROM collections WHERE name = ?) AND is_manual = 0";
        jdbcClient.sql(sql)
                .param(collectionName.trim())
                .update();
    }

    public List<String> getFilePaths(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            return List.of();
        }
        String sql = """
                    SELECT i.file_path
                    FROM images i 
                    JOIN collection_images ci ON i.id = ci.image_id 
                    JOIN collections c ON ci.collection_id = c.id 
                    WHERE c.name = ? 
                    AND i.id NOT IN (
                        SELECT ce.image_id 
                        FROM collection_exclusions ce 
                        JOIN collections c2 ON ce.collection_id = c2.id 
                        WHERE c2.name = ?
                    )
                    ORDER BY ci.added_at DESC
                """;
        return jdbcClient.sql(sql)
                .param(collectionName.trim())
                .param(collectionName.trim())
                .query(String.class)
                .list();
    }

    public void addExclusion(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required for exclusion.");
        }
        String sql = "INSERT OR IGNORE INTO collection_exclusions (collection_id, image_id) SELECT id, ? FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(collectionName.trim())
                .update();
    }

    @Transactional
    public void addExclusions(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank() || imageIds == null || imageIds.isEmpty()) {
            return;
        }

        Integer collectionId = jdbcClient.sql("SELECT id FROM collections WHERE name = ?")
                .param(collectionName.trim())
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ValidationException("Collection not found: " + collectionName));

        int batchSize = 500;
        for (int i = 0; i < imageIds.size(); i += batchSize) {
            List<Integer> batch = imageIds.subList(i, Math.min(i + batchSize, imageIds.size()));
            StringBuilder sql = new StringBuilder("INSERT OR IGNORE INTO collection_exclusions (collection_id, image_id) VALUES ");
            
            List<Object> params = new java.util.ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                sql.append("(?, ?)");
                if (j < batch.size() - 1) sql.append(", ");
                params.add(collectionId);
                params.add(batch.get(j));
            }

            jdbcClient.sql(sql.toString())
                    .params(params)
                    .update();
        }
    }

    @Transactional
    public void removeExclusions(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        Integer collectionId = jdbcClient.sql("SELECT id FROM collections WHERE name = ?")
                .param(collectionName.trim())
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ValidationException("Collection not found: " + collectionName));

        int batchSize = 500;
        for (int i = 0; i < imageIds.size(); i += batchSize) {
            List<Integer> batch = imageIds.subList(i, Math.min(i + batchSize, imageIds.size()));
            String placeholders = batch.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "DELETE FROM collection_exclusions WHERE collection_id = ? AND image_id IN (" + placeholders + ")";

            List<Object> params = new java.util.ArrayList<>();
            params.add(collectionId);
            params.addAll(batch);

            jdbcClient.sql(sql)
                    .params(params)
                    .update();
        }
    }

    public void removeExclusion(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required to remove exclusion.");
        }
        String sql = "DELETE FROM collection_exclusions WHERE collection_id = (SELECT id FROM collections WHERE name = ?) AND image_id = ?";
        jdbcClient.sql(sql)
                .param(collectionName.trim())
                .param(imageId)
                .update();
    }

    public void addSmartImage(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            return;
        }
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at, is_manual) SELECT id, ?, ?, 0 FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(System.currentTimeMillis())
                .param(collectionName.trim())
                .update();
    }
}
