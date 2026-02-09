package com.nilsson.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Core infrastructure service handling database persistence, connection pooling, and schema migration.
 * Manages the SQLite database connection and ensures schema integrity.
 */
@Service
public class DatabaseService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final String DATA_DIR_NAME = ".aitoolbox";
    private static final String DB_FILE_NAME = "library.db";
    private static final int CURRENT_DB_VERSION = 1;

    private final HikariDataSource dataSource;

    public DatabaseService() {
        this(resolvePortableDbUrl());
    }

    public DatabaseService(String jdbcUrl) {
        logger.info("Initializing DatabaseService with URL: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("ImageToolboxPool");

        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("foreign_keys", "ON");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");

        this.dataSource = new HikariDataSource(config);
        performMigrations();
    }

    private static String resolvePortableDbUrl() {
        try {
            Path appDir = Paths.get(System.getProperty("user.home")).resolve(DATA_DIR_NAME);

            if (!Files.exists(appDir)) {
                logger.info("Data directory missing. Creating: {}", appDir.toAbsolutePath());
                Files.createDirectories(appDir);
            }

            Path dbPath = appDir.resolve(DB_FILE_NAME);
            return "jdbc:sqlite:" + dbPath.toAbsolutePath();

        } catch (IOException e) {
            logger.error("Failed to initialize data directory.", e);
            throw new RuntimeException("Fatal Error: Could not create data directory.", e);
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down connection pool...");
            dataSource.close();
        }
    }

    @Override
    public void destroy() throws Exception {
        shutdown();
    }

    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    private void performMigrations() {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            int currentVersion = getDatabaseVersion(conn);
            logger.debug("Detected Database Version: {}", currentVersion);

            if (currentVersion < CURRENT_DB_VERSION) {
                logger.info("Migrating database from v{} to v{}...", currentVersion, CURRENT_DB_VERSION);
                try {
                    if (currentVersion == 0) {
                        applyInitialSchema(conn);
                    }

                    setDatabaseVersion(conn, CURRENT_DB_VERSION);
                    conn.commit();
                    logger.info("Database migration completed successfully.");
                } catch (SQLException e) {
                    conn.rollback();
                    logger.error("Migration failed. Transaction rolled back.", e);
                    throw e;
                }
            }
        } catch (SQLException e) {
            logger.error("Critical error during database migration.", e);
            throw new RuntimeException("Database migration failed during startup.", e);
        }
    }

    private void applyInitialSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS images (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            file_path TEXT UNIQUE NOT NULL,
                            file_hash TEXT,
                            is_starred BOOLEAN DEFAULT 0,
                            rating INTEGER DEFAULT 0,
                            last_scanned INTEGER
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS collections (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT UNIQUE NOT NULL,
                            created_at INTEGER
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS image_metadata (
                            image_id INTEGER,
                            key TEXT,
                            value TEXT,
                            FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS image_tags (
                            image_id INTEGER,
                            tag TEXT,
                            FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS collection_images (
                            collection_id INTEGER,
                            image_id INTEGER,
                            added_at INTEGER,
                            PRIMARY KEY (collection_id, image_id),
                            FOREIGN KEY(collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                            FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE
                        )
                    """);

            stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS metadata_fts USING fts5(image_id UNINDEXED, global_text)");
            stmt.execute("CREATE TABLE IF NOT EXISTS pinned_folders (path TEXT UNIQUE NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON images(file_path)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_hash ON images(file_hash)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tags_text ON image_tags(tag)");
        }
    }

    private int getDatabaseVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA user_version;")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setDatabaseVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA user_version = " + version);
        }
    }
}
