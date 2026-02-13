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

/**
 * Repository for managing core image entities and their persistent state within the library.
 * <p>
 * This class serves as the primary data access layer for the {@code images} table. It handles the
 * registration of new images, path updates for file movements, and user-driven state changes
 * such as ratings and "starred" status. It also supports hash-based lookups to detect and
 * reconcile file movements across the local file system, ensuring metadata is preserved
 * even when files are renamed or moved externally.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Identity Management:</b> Resolves absolute file system paths to internal database IDs
 *   for relational associations.</li>
 *   <li><b>File Move Detection:</b> Utilizes SHA-256 hashes to identify existing records when a
 *   file is moved, allowing for seamless path updates without data loss.</li>
 *   <li><b>Batch Processing:</b> Provides a streaming mechanism for library-wide operations,
 *   enabling efficient reconciliation and maintenance tasks.</li>
 *   <li><b>Organization Logic:</b> Manages the {@code rating} and {@code is_starred} flags,
 *   which are central to the application's filtering and collection systems.</li>
 *   <li><b>Atomic Registration:</b> Implements thread-safe image indexing using {@code INSERT OR IGNORE}
 *   and transactional ID retrieval.</li>
 * </ul>
 */
@Repository
public class ImageRepository {

    private static final Logger logger = LoggerFactory.getLogger(ImageRepository.class);
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

    public void updatePath(String oldPath, String newPath) {
        if (oldPath == null || oldPath.isBlank() || newPath == null || newPath.isBlank()) {
            throw new ValidationException("Both old and new paths are required for update.");
        }
        jdbcClient.sql("UPDATE images SET file_path = ? WHERE file_path = ?")
                .param(newPath)
                .param(oldPath)
                .update();
    }

    @Transactional
    public int getOrCreateId(String path, String hash) {
        if (path == null || path.isBlank() || hash == null || hash.isBlank()) {
            throw new ValidationException("Path and hash are required for registration.");
        }

        jdbcClient.sql("INSERT OR IGNORE INTO images(file_path, file_hash, last_scanned) VALUES(?, ?, ?)")
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
        // SQLite limit for parameters is usually 999 or 32766 depending on version.
        // Batching in chunks of 500 is safe.
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

    public void forEachFilePath(Consumer<String> action) {
        if (action == null) {
            throw new ValidationException("Consumer action cannot be null.");
        }
        jdbcClient.sql("SELECT file_path FROM images")
                .query(String.class)
                .list()
                .forEach(action);
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
}
