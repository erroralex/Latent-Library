package com.nilsson.backend.config;

import com.nilsson.backend.exception.ApplicationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Infrastructure configuration for the persistence layer, specifically managing the SQLite database
 * lifecycle and connection pooling via HikariCP.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li><b>Portable Path Resolution:</b> Dynamically determining the database location based on
 *   application configuration, ensuring the data directory exists.</li>
 *   <li><b>Connection Pooling:</b> Configuring a {@link HikariDataSource} with optimized settings
 *   for SQLite, including Write-Ahead Logging (WAL) and foreign key support.</li>
 *   <li><b>Schema Migration:</b> Initializing and executing Flyway migrations to ensure the
 *   database schema is consistent with the application version.</li>
 *   <li><b>Performance Tuning:</b> Applying specific SQLite PRAGMAs (synchronous, busy_timeout)
 *   to balance data integrity with high-concurrency performance.</li>
 * </ul>
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DATA_DIR_NAME = "data";
    private static final String DB_FILE_NAME = "library.db";

    @Value("${app.data.dir:.}")
    private String appDataDir;

    @Value("${spring.datasource.url:#{null}}")
    private String overrideUrl;

    @Bean(destroyMethod = "close")
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = (overrideUrl != null && !overrideUrl.isBlank()) ? overrideUrl : resolvePortableDbUrl();
        logger.info("Initializing HikariDataSource with URL: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        config.setMaximumPoolSize(25);
        config.setMinimumIdle(5);
        config.setPoolName("ImageToolboxPool");

        // SQLite Optimization PRAGMAs
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "60000");
        config.addDataSourceProperty("foreign_keys", "ON");

        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    public Flyway flyway(DataSource dataSource) {
        logger.info("Configuring Flyway migrations...");
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    private String resolvePortableDbUrl() {
        try {
            Path appDir = Paths.get(appDataDir).resolve(DATA_DIR_NAME).toAbsolutePath().normalize();
            if (!Files.exists(appDir)) {
                logger.info("Creating data directory: {}", appDir);
                Files.createDirectories(appDir);
            }
            Path dbPath = appDir.resolve(DB_FILE_NAME);
            // Ensure cross-platform compatibility for JDBC URL by forcing forward slashes
            return "jdbc:sqlite:" + dbPath.toString().replace("\\", "/");
        } catch (IOException e) {
            throw new ApplicationException("Fatal Error: Could not create data directory.", e);
        }
    }
}
