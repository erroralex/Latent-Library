package com.nilsson.backend.config;

import com.nilsson.backend.exception.ApplicationException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Infrastructure configuration for the SQLite database and connection pooling.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DATA_DIR_NAME = "data";
    private static final String DB_FILE_NAME = "library.db";

    @Value("${app.data.dir:.}")
    private String appDataDir;

    @Bean(destroyMethod = "close")
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = resolvePortableDbUrl();
        logger.info("Initializing HikariDataSource with URL: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("ImageToolboxPool");

        // SQLite specific optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("foreign_keys", "ON");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");

        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
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
            return "jdbc:sqlite:" + dbPath.toString();
        } catch (IOException e) {
            throw new ApplicationException("Fatal Error: Could not create data directory.", e);
        }
    }
}
