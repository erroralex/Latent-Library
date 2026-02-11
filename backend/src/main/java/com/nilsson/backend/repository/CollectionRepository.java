package com.nilsson.backend.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.model.CreateCollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing image collections and their relational associations.
 * <p>
 * This class handles the persistence of both static and smart collections. It manages the
 * {@code collections} table, which stores collection definitions (including serialized JSON
 * filters for smart collections), and the {@code collection_images} join table, which
 * maintains the many-to-many relationship between images and collections.
 * <p>
 * Key functionalities:
 * - Collection CRUD: Implements full lifecycle management for collection entities.
 * - Smart Filter Persistence: Serializes and deserializes complex search criteria using Jackson.
 * - Membership Management: Handles adding images to collections and clearing memberships.
 * - Path Resolution: Retrieves absolute file system paths for all images within a specific collection.
 * - Atomic Operations: Utilizes {@code INSERT OR IGNORE} to safely manage image associations.
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
        if (name == null) return Optional.empty();
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
                        }
                    }
                    return new CreateCollectionRequest(colName, isSmart, filters);
                })
                .optional();
    }

    public void create(String name, boolean isSmart, CreateCollectionRequest.CollectionFilters filters) {
        if (name == null || name.isBlank()) return;
        String filtersJson = null;
        if (isSmart && filters != null) {
            try {
                filtersJson = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing collection filters for collection: {}", name, e);
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
        if (oldName == null || oldName.isBlank()) return;
        if (newName == null || newName.isBlank()) newName = oldName;
        
        String filtersJson = null;
        if (isSmart && filters != null) {
            try {
                filtersJson = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing collection filters for collection: {}", newName, e);
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
        if (name == null) return;
        jdbcClient.sql("DELETE FROM collections WHERE name = ?")
                .param(name.trim())
                .update();
    }

    public void addImage(String collectionName, int imageId) {
        if (collectionName == null) return;
        // Set is_manual = 1 for explicit additions
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at, is_manual) SELECT id, ?, ?, 1 FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(System.currentTimeMillis())
                .param(collectionName.trim())
                .update();
    }

    public void removeAllImages(String collectionName) {
        if (collectionName == null) return;
        // Only remove non-manual images (smart population)
        String sql = "DELETE FROM collection_images WHERE collection_id = (SELECT id FROM collections WHERE name = ?) AND is_manual = 0";
        jdbcClient.sql(sql)
                .param(collectionName.trim())
                .update();
    }

    public List<String> getFilePaths(String collectionName) {
        if (collectionName == null) return List.of();
        // Exclude blacklisted images
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
        if (collectionName == null) return;
        String sql = "INSERT OR IGNORE INTO collection_exclusions (collection_id, image_id) SELECT id, ? FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(collectionName.trim())
                .update();
    }
    
    public void removeExclusion(String collectionName, int imageId) {
        if (collectionName == null) return;
        String sql = "DELETE FROM collection_exclusions WHERE collection_id = (SELECT id FROM collections WHERE name = ?) AND image_id = ?";
        jdbcClient.sql(sql)
                .param(collectionName.trim())
                .param(imageId)
                .update();
    }
    
    public void addSmartImage(String collectionName, int imageId) {
        if (collectionName == null) return;
        // is_manual = 0 for smart population
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at, is_manual) SELECT id, ?, ?, 0 FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(System.currentTimeMillis())
                .param(collectionName.trim())
                .update();
    }
}
