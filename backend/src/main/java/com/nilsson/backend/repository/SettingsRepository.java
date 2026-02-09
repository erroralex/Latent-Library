package com.nilsson.backend.repository;

import com.nilsson.backend.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repository for persistent storage and retrieval of application-wide settings.
 * Provides a key-value abstraction over the settings database table.
 * Handles user preferences, application state, and configuration parameters.
 */
@Repository
public class SettingsRepository {

    private static final Logger logger = LoggerFactory.getLogger(SettingsRepository.class);
    private final DatabaseService db;

    public SettingsRepository(DatabaseService db) {
        this.db = db;
    }

    public String get(String key, String defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException e) {
            logger.warn("Failed to load setting: {}", key);
        }
        return defaultValue;
    }

    public void set(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings(key, value) VALUES(?, ?)";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save setting: {}={}", key, value, e);
        }
    }
}
