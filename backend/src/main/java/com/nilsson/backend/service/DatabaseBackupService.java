package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service responsible for creating safety snapshots of the application database.
 * <p>
 * This service automatically creates a backup of the SQLite database upon application startup.
 * It utilizes the SQLite {@code VACUUM INTO} command to generate a consistent, defragmented
 * copy of the database, even while the application is running (hot backup).
 * <p>
 * This service is disabled when the 'test' profile is active.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Startup Snapshot:</b> Triggers a backup immediately after the application context is ready,
 *   ensuring a recovery point exists before any user modifications occur in the current session.</li>
 *   <li><b>Hot Backup:</b> Uses {@code VACUUM INTO} to safely copy the database state without
 *   locking the file or requiring a shutdown.</li>
 *   <li><b>Path Management:</b> Resolves the backup location relative to the configured application
 *   data directory, using a configurable filename.</li>
 * </ul>
 */
@Service
@Profile("!test")
public class DatabaseBackupService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final String DATA_DIR = "data";

    private final JdbcClient jdbcClient;
    private final String appDataDir;
    private final String backupFilename;

    public DatabaseBackupService(DataSource dataSource,
                                 @Value("${app.data.dir:.}") String appDataDir,
                                 @Value("${app.db.backup-filename:library.db.bak}") String backupFilename) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.appDataDir = appDataDir;
        this.backupFilename = backupFilename;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread.ofVirtual().start(this::performBackup);
    }

    public void performBackup() {
        logger.info("Starting database backup...");
        try {
            Path dataPath = Paths.get(appDataDir).resolve(DATA_DIR).toAbsolutePath().normalize();
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
            }

            Path backupPath = dataPath.resolve(backupFilename);

            // VACUUM INTO requires the target file to NOT exist.
            Files.deleteIfExists(backupPath);

            // Execute VACUUM INTO. Note: SQLite expects a string literal for the path.
            // We use string concatenation here because the path is internally resolved and safe.
            // Parameter binding for VACUUM INTO is not consistently supported across drivers.
            String sql = "VACUUM INTO '" + backupPath.toString().replace("\\", "/") + "'";
            
            jdbcClient.sql(sql).update();

            logger.info("Database backup completed successfully: {}", backupPath);
        } catch (IOException e) {
            logger.error("Failed to prepare backup file", e);
            throw new ApplicationException("Could not prepare database backup file.", e);
        } catch (Exception e) {
            logger.error("Database backup failed", e);
            // We log but do not throw, as backup failure should not crash the application startup
        }
    }
}
