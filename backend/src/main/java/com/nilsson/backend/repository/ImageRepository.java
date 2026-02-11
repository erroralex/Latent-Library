package com.nilsson.backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Consumer;

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
        return jdbcClient.sql("SELECT id FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElse(-1);
    }

    public List<String> findPathsByHash(String hash) {
        return jdbcClient.sql("SELECT file_path FROM images WHERE file_hash = ?")
                .param(hash)
                .query(String.class)
                .list();
    }

    public void updatePath(String oldPath, String newPath) {
        jdbcClient.sql("UPDATE images SET file_path = ? WHERE file_path = ?")
                .param(newPath)
                .param(oldPath)
                .update();
    }

    @Transactional
    public int getOrCreateId(String path, String hash) {
        jdbcClient.sql("INSERT OR IGNORE INTO images(file_path, file_hash, last_scanned) VALUES(?, ?, ?)")
                .param(path)
                .param(hash)
                .param(System.currentTimeMillis())
                .update();

        return jdbcClient.sql("SELECT id FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("Failed to get ID for " + path));
    }

    public void deleteByPath(String path) {
        jdbcClient.sql("DELETE FROM images WHERE file_path = ?")
                .param(path)
                .update();
    }

    public void forEachFilePath(Consumer<String> action) {
        jdbcClient.sql("SELECT file_path FROM images")
                .query(String.class)
                .list()
                .forEach(action);
    }

    public int getRating(String path) {
        return jdbcClient.sql("SELECT rating FROM images WHERE file_path = ?")
                .param(path)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    public void setRating(int id, int rating) {
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
