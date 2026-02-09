package com.nilsson.backend.repository;

import com.nilsson.backend.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 Repository class responsible for managing image collections and their associations
 within the local SQLite database.
 * <p>This class provides high-level abstractions for:
 <ul>
 <li>CRUD operations for collection entities (Create, Read, Delete).</li>
 <li>Managing many-to-many relationships between images and collections.</li>
 <li>Retrieving filesystem paths for images associated with specific collections.</li>
 </ul>
 * <p>It utilizes {@link DatabaseService} for connection management and follows
 standard JDBC practices with try-with-resources for automatic resource handling.</p>

 @version 1.0 */
@Repository
public class CollectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(CollectionRepository.class);
    private final DatabaseService db;

    public CollectionRepository(DatabaseService db) {
        this.db = db;
    }

    // --- Collection Management ---

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

    // --- Image Association ---

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