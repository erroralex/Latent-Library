package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Core infrastructure service for database lifecycle management, persistence, and schema evolution.
 * <p>
 * This service initializes and maintains the SQLite database connection pool using HikariCP.
 * It ensures that the database is stored in a portable, user-specific directory and handles
 * automated schema migrations via Flyway. It also configures critical SQLite performance
 * and concurrency settings, such as Write-Ahead Logging (WAL) mode, to support high-concurrency
 * indexing and search operations.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Portable Storage:</b> Resolves and creates the {@code data} directory in the application's
 *   root folder to ensure data portability.</li>
 *   <li><b>Connection Pooling:</b> Manages a high-performance HikariCP pool optimized for SQLite,
 *   configuring appropriate timeouts and journal modes.</li>
 *   <li><b>Schema Migration:</b> Executes Flyway migrations on startup to ensure the database
 *   schema is always up-to-date and consistent.</li>
 *   <li><b>Resource Management:</b> Implements {@link DisposableBean} to ensure the connection
 *   pool is gracefully shut down when the application context is closed.</li>
 *   <li><b>Data Maintenance:</b> Provides administrative functions to clear all application data
 *   and reclaim disk space via SQLite's {@code VACUUM} command.</li>
 * </ul>
 */
@Service
public class DatabaseService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final String DATA_DIR_NAME = "data";
    private static final String DB_FILE_NAME = "library.db";

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

    @Bean
    public DataSource dataSource() {
        return this.dataSource;
    }

    private static String resolvePortableDbUrl() {
        try {
            Path appDir = Paths.get(".").resolve(DATA_DIR_NAME).toAbsolutePath().normalize();

            if (!Files.exists(appDir)) {
                logger.info("Data directory missing. Creating: {}", appDir);
                Files.createDirectories(appDir);
            }

            Path dbPath = appDir.resolve(DB_FILE_NAME);
            return "jdbc:sqlite:" + dbPath.toString();

        } catch (IOException e) {
            logger.error("Failed to initialize data directory.", e);
            throw new ApplicationException("Fatal Error: Could not create data directory.", e);
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
        logger.info("Starting Flyway migration...");
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();

            flyway.migrate();
            logger.info("Flyway migration completed successfully.");
        } catch (Exception e) {
            throw new ApplicationException("Database migration failed. Please check technical logs.", e);
        }
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
}