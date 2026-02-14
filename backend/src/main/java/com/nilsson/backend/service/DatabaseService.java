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
 * High-level service for database maintenance, administrative operations, and lifecycle management.
 * <p>
 * This service provides a centralized interface for performing destructive or maintenance-heavy
 * database tasks. It handles operations such as clearing specific data subsets (AI tags, metadata),
 * purging unorganized records, and performing full database resets. It also manages low-level
 * connection retrieval and SQLite-specific maintenance commands like {@code VACUUM}.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Data Purging:</b> Provides granular methods to clear AI-generated tags or the entire
 *   metadata cache while preserving user-created data like ratings and collections.</li>
 *   <li><b>Library Cleanup:</b> Implements logic to remove "unorganized" images—those without
 *   ratings, stars, or collection memberships—to keep the index lean.</li>
 *   <li><b>Full Reset:</b> Facilitates a complete wipe of all application data across all tables,
 *   typically used for system re-initialization.</li>
 *   <li><b>Storage Optimization:</b> Executes {@code VACUUM} commands to reclaim disk space
 *   and defragment the SQLite database file after large deletions.</li>
 *   <li><b>Connection Management:</b> Acts as a wrapper for the {@link DataSource} to provide
 *   raw JDBC connections when high-level abstractions are insufficient.</li>
 * </ul>
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

    public void clearAiTags() {
        executeSql("UPDATE images SET ai_tags = NULL", "AI tags cleared.");
        executeSql("UPDATE metadata_fts SET ai_tags = ''", "FTS AI tags cleared.");
    }

    public void clearMetadataCache() {
        executeSql("DELETE FROM image_metadata", "Metadata cache cleared.");
        executeSql("DELETE FROM metadata_fts", "Search index cleared.");
    }

    public void clearUnorganizedImages() {
        String sql = """
                    DELETE FROM images 
                    WHERE rating = 0 
                    AND is_starred = 0 
                    AND id NOT IN (SELECT image_id FROM collection_images)
                """;
        executeSql(sql, "Unorganized images cleared from index.");
        executeSql("VACUUM", "Database vacuumed.");
    }

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

    private void executeSql(String sql, String logMsg) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info(logMsg);
        } catch (SQLException e) {
            throw new ApplicationException("Database maintenance failed: " + e.getMessage(), e);
        }
    }

    public void shutdown() {
        logger.info("Database shutdown requested (handled by Spring context).");
    }
}
