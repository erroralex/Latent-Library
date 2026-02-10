package com.nilsson.backend.service;

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

/**
 * Core infrastructure service for database lifecycle management and persistence.
 * <p>
 * This service initializes and maintains the SQLite database connection pool using HikariCP.
 * It ensures that the database is stored in a portable, user-specific directory and handles
 * automated schema migrations via Flyway. It also configures critical SQLite performance
 * and concurrency settings, such as Write-Ahead Logging (WAL) mode.
 * <p>
 * Key functionalities:
 * - Portable Storage: Resolves and creates the {@code .aitoolbox} directory in the user's home folder.
 * - Connection Pooling: Manages a high-performance HikariCP pool optimized for SQLite.
 * - Schema Migration: Executes Flyway migrations on startup to ensure database integrity.
 * - Resource Management: Implements {@code DisposableBean} for graceful pool shutdown.
 */
@Service
public class DatabaseService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final String DATA_DIR_NAME = ".aitoolbox";
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
        logger.info("Starting Flyway migration...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
        logger.info("Flyway migration completed successfully.");
    }
}
