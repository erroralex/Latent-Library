package com.nilsson.backend.repository;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for managing core image entities and their persistent state within the library.
 * <p>
 * This class serves as the primary data access layer for the {@code images} table. It handles
 * the registration of new files, tracking of file movements via cryptographic hashes, and
 * management of user-facing attributes such as ratings and "starred" status. It also
 * maintains the "missing" state for files that are indexed but currently unreachable on disk.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>File Registration & Tracking:</b> Implements {@code getOrCreateId} logic that
 *   uses file hashes to detect moved or renamed files, preserving metadata across path changes.</li>
 *   <li><b>State Management:</b> Persists and retrieves image ratings, starred status, and
 *   perceptual hashes (dHash) used for similarity detection.</li>
 *   <li><b>Batch Operations:</b> Provides efficient batch deletion mechanisms to handle
 *   large-scale library reconciliation or folder removals.</li>
 *   <li><b>Library Traversal:</b> Offers streaming access to all indexed file paths for
 *   background maintenance tasks like reconciliation and thumbnail generation.</li>
 *   <li><b>Integrity Maintenance:</b> Manages the {@code is_missing} flag to distinguish
 *   between permanently deleted files and temporarily disconnected storage media.</li>
 * </ul>
 */
@Repository
public class ImageRepository {

    private static final Logger log = LoggerFactory.getLogger(ImageRepository.class);
    private final JdbcClient jdbcClient;

    public ImageRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public int getIdByPath(String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path cannot be empty for ID retrieval.");
        }
        return jdbcClient.sql("SELECT id FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElse(-1);
    }

    public List<String> findPathsByHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new ValidationException("Hash cannot be empty for path lookup.");
        }
        return jdbcClient.sql("SELECT file_path FROM images WHERE file_hash = ?")
                .param(hash)
                .query(String.class)
                .list();
    }

    @Transactional
    public void updatePath(String oldPath, String newPath) {
        if (oldPath == null || oldPath.isBlank() || newPath == null || newPath.isBlank()) {
            throw new ValidationException("Both old and new paths are required for update.");
        }
        jdbcClient.sql("UPDATE images SET file_path = ?, is_missing = 0 WHERE file_path = ?")
                .param(newPath)
                .param(oldPath)
                .update();
    }

    @Transactional
    public int getOrCreateId(String path, String hash) {
        if (path == null || path.isBlank() || hash == null || hash.isBlank()) {
            throw new ValidationException("Path and hash are required for registration.");
        }

        jdbcClient.sql("INSERT OR IGNORE INTO images(file_path, file_hash, last_scanned, is_missing) VALUES(?, ?, ?, 0)")
                .param(path)
                .param(hash)
                .param(System.currentTimeMillis())
                .update();

        return jdbcClient.sql("SELECT id FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ApplicationException("System failed to retrieve ID for registered path: " + path));
    }

    @Transactional
    public void deleteByPath(String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path is required for deletion.");
        }
        jdbcClient.sql("DELETE FROM images WHERE file_path = ?")
                .param(path)
                .update();
    }

    @Transactional
    public void deleteByPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        int batchSize = 500;
        for (int i = 0; i < paths.size(); i += batchSize) {
            List<String> batch = paths.subList(i, Math.min(i + batchSize, paths.size()));
            String placeholders = batch.stream().map(p -> "?").collect(Collectors.joining(","));
            String sql = "DELETE FROM images WHERE file_path IN (" + placeholders + ")";
            jdbcClient.sql(sql)
                    .params(batch)
                    .update();
        }
    }

    @Transactional(readOnly = true)
    public void forEachFilePath(Consumer<String> action) {
        if (action == null) {
            throw new ValidationException("Consumer action cannot be null.");
        }
        try (Stream<String> pathStream = jdbcClient.sql("SELECT file_path FROM images")
                .query(String.class)
                .stream()) {
            pathStream.forEach(action);
        } catch (Exception e) {
            log.error("Error during streaming library traversal", e);
            throw new ApplicationException("Failed to traverse image library.", e);
        }
    }

    public int getRating(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return jdbcClient.sql("SELECT rating FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    @Transactional
    public void setRating(int id, int rating) {
        if (id <= 0) {
            throw new ValidationException("Invalid image ID provided for rating update.");
        }
        jdbcClient.sql("UPDATE images SET rating = ?, is_starred = ? WHERE id = ?")
                .param(rating)
                .param(rating > 0)
                .param(id)
                .update();
    }

    public List<String> getStarredPaths() {
        return jdbcClient.sql("SELECT file_path FROM images WHERE is_starred = 1")
                .query(String.class)
                .list();
    }

    @Transactional
    public void setMissing(String path, boolean missing) {
        jdbcClient.sql("UPDATE images SET is_missing = ? WHERE file_path = ?")
                .param(missing)
                .param(path)
                .update();
    }

    public boolean isMissing(String path) {
        return jdbcClient.sql("SELECT is_missing FROM images WHERE file_path = ?")
                .param(path)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    public Long getDHash(String path) {
        return jdbcClient.sql("SELECT dhash FROM images WHERE file_path = ?")
                .param(path)
                .query(Long.class)
                .optional()
                .orElse(null);
    }
}
