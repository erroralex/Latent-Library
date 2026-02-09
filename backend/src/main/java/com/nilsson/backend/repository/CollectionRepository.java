package com.nilsson.backend.repository;

import com.nilsson.backend.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing image collections and their associations.
 * Handles CRUD operations for collections and manages the many-to-many relationship with images.
 * Provides methods to retrieve image paths associated with specific collections.
 */
@Repository
public class CollectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(CollectionRepository.class);
    private final DatabaseService db;

    public CollectionRepository(DatabaseService db) {
        this.db = db;
    }

    public List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM collections ORDER BY name ASC")) {
            while (rs.next()) names.add(rs.getString("name"));
        } catch (SQLException e) {
            logger.error("Error fetching collections", e);
        }
        return names;
    }

    public void create(String name) {
        if (name == null || name.isBlank()) return;
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO collections(name, created_at) VALUES(?, ?)")) {
            pstmt.setString(1, name.trim());
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error creating collection: {}", name, e);
        }
    }

    public void delete(String name) {
        if (name == null) return;
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM collections WHERE name = ?")) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting collection: {}", name, e);
        }
    }

    public void addImage(String collectionName, int imageId) {
        String sql = "INSERT OR IGNORE INTO collection_images (collection_id, image_id, added_at) SELECT id, ?, ? FROM collections WHERE name = ?";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, imageId);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, collectionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to add to collection", e);
        }
    }

    public List<String> getFilePaths(String collectionName) {
        List<String> paths = new ArrayList<>();
        String sql = "SELECT i.file_path FROM images i JOIN collection_images ci ON i.id = ci.image_id JOIN collections c ON ci.collection_id = c.id WHERE c.name = ? ORDER BY ci.added_at DESC";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, collectionName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) paths.add(rs.getString("file_path"));
        } catch (SQLException e) {
            logger.error("Failed to fetch collection paths", e);
        }
        return paths;
    }
}
