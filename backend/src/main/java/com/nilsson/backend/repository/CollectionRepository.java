package com.nilsson.backend.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.model.CreateCollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing image collections and their associations.
 * Handles CRUD operations for collections and manages the many-to-many relationship with images.
 * Provides methods to retrieve image paths associated with specific collections.
 */
@Repository
public class CollectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(CollectionRepository.class);
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    public CollectionRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.jdbcClient = JdbcClient.create(dataSource);
        this.objectMapper = objectMapper;
        initializeTable();
    }

    private void initializeTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Add is_smart and filters_json columns if they don't exist
            try {
                stmt.execute("ALTER TABLE collections ADD COLUMN is_smart BOOLEAN DEFAULT FALSE");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    logger.error("Error adding is_smart column", e);
                }
            }
            try {
                stmt.execute("ALTER TABLE collections ADD COLUMN filters_json TEXT");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    logger.error("Error adding filters_json column", e);
                }
            }
        } catch (SQLException e) {
            logger.error("Error initializing collections table schema", e);
        }
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

    public void update(String name, boolean isSmart, CreateCollectionRequest.CollectionFilters filters) {
        if (name == null || name.isBlank()) return;
        String filtersJson = null;
        if (isSmart && filters != null) {
            try {
                filtersJson = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing collection filters for collection: {}", name, e);
            }
        }

        jdbcClient.sql("UPDATE collections SET is_smart = ?, filters_json = ? WHERE name = ?")
                .param(isSmart)
                .param(filtersJson)
                .param(name.trim())
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
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at) SELECT id, ?, ? FROM collections WHERE name = ?";
        jdbcClient.sql(sql)
                .param(imageId)
                .param(System.currentTimeMillis())
                .param(collectionName.trim())
                .update();
    }

    public void removeAllImages(String collectionName) {
        if (collectionName == null) return;
        String sql = "DELETE FROM collection_images WHERE collection_id = (SELECT id FROM collections WHERE name = ?)";
        jdbcClient.sql(sql)
                .param(collectionName.trim())
                .update();
    }

    public List<String> getFilePaths(String collectionName) {
        if (collectionName == null) return List.of();
        String sql = "SELECT i.file_path FROM images i JOIN collection_images ci ON i.id = ci.image_id JOIN collections c ON ci.collection_id = c.id WHERE c.name = ? ORDER BY ci.added_at DESC";
        return jdbcClient.sql(sql)
                .param(collectionName.trim())
                .query(String.class)
                .list();
    }
}
