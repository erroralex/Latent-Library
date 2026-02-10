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
 * Repository for managing image entities.
 * Handles CRUD operations for images and their ratings.
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
