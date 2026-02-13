package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * High-level service for database maintenance and administrative operations.
 */
@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private final DataSource dataSource;

    public DatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Clears all application data from the database and reclaims space.
     */
    public void clearAllData() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM images");
            stmt.execute("DELETE FROM collections");
            stmt.execute("DELETE FROM pinned_folders");
            stmt.execute("DELETE FROM settings");
            stmt.execute("DELETE FROM image_metadata");
            stmt.execute("DELETE FROM image_tags");
            stmt.execute("DELETE FROM collection_images");
            stmt.execute("DELETE FROM collection_exclusions");
            stmt.execute("DELETE FROM metadata_fts");

            stmt.execute("VACUUM");

            logger.info("Database cleared and vacuumed.");
        } catch (SQLException e) {
            logger.error("Failed to clear database", e);
            throw new ApplicationException("System failed to clear application data.", e);
        }
    }

    /**
     * Shutdown is now handled by the DataSource bean's destroyMethod in DatabaseConfig.
     */
    public void shutdown() {
        logger.info("Database shutdown requested (handled by Spring context).");
    }
}
