package com.nilsson.backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Repository for managing image entities and their associated metadata.
 * Handles CRUD operations for images, metadata, tags, and ratings.
 * Provides search capabilities and manages the full-text search index.
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

    public List<String> findPaths(String query, Map<String, List<String>> filters, int limit) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT i.file_path FROM images i WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            // Split query into tokens for AND-based search
            String[] tokens = query.trim().split("\\s+");
            for (String token : tokens) {
                sql.append("AND (");
                sql.append("EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.value LIKE ?) ");
                sql.append("OR ");
                sql.append("EXISTS (SELECT 1 FROM image_tags t WHERE t.image_id = i.id AND t.tag LIKE ?) ");
                sql.append(") ");
                String likeQuery = "%" + token + "%";
                params.add(likeQuery);
                params.add(likeQuery);
            }
        }

        if (filters != null) {
            for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) continue;

                // Filter out "All" or empty strings
                List<String> validValues = new ArrayList<>();
                for (String v : values) {
                    if (v != null && !v.isBlank() && !"All".equals(v)) {
                        validValues.add(v);
                    }
                }
                if (validValues.isEmpty()) continue;

                if ("Rating".equals(entry.getKey())) {
                    // Special handling for Rating: usually single value, but if list, treat as OR
                    sql.append("AND (");
                    boolean first = true;
                    for (String val : validValues) {
                        if ("Any Star Count".equals(val)) {
                            if (!first) sql.append(" OR ");
                            sql.append("i.rating > 0");
                            first = false;
                        } else {
                            try {
                                int ratingVal = Integer.parseInt(val);
                                if (!first) sql.append(" OR ");
                                sql.append("i.rating = ?");
                                params.add(ratingVal);
                                first = false;
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid rating value: {}", val);
                            }
                        }
                    }
                    if (first) {
                        // If no valid ratings were added (e.g. all parse errors), add a false condition to avoid syntax error
                        sql.append("1=0");
                    }
                    sql.append(") ");

                } else if ("Loras".equals(entry.getKey())) {
                    // For Loras, we want images that contain ANY of the selected Loras (OR logic)
                    sql.append("AND EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.key = ? AND (");
                    params.add(entry.getKey());
                    for (int j = 0; j < validValues.size(); j++) {
                        if (j > 0) sql.append(" OR ");
                        sql.append("m.value LIKE ?");
                        params.add("%" + validValues.get(j) + "%");
                    }
                    sql.append(")) ");

                } else if ("Tag".equals(entry.getKey())) {
                    sql.append("AND EXISTS (SELECT 1 FROM image_tags t WHERE t.image_id = i.id AND (");
                    for (int j = 0; j < validValues.size(); j++) {
                        if (j > 0) sql.append(" OR ");
                        sql.append("t.tag LIKE ?");
                        params.add("%" + validValues.get(j) + "%");
                    }
                    sql.append(")) ");

                } else {
                    // Standard metadata (Model, Sampler, etc.)
                    // OR logic for multiple values of the same key
                    sql.append("AND EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.key = ? AND (");
                    params.add(entry.getKey());
                    for (int j = 0; j < validValues.size(); j++) {
                        if (j > 0) sql.append(" OR ");
                        sql.append("m.value = ?");
                        params.add(validValues.get(j));
                    }
                    sql.append(")) ");
                }
            }
        }
        sql.append("LIMIT ?");
        params.add(limit);

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(String.class)
                .list();
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

    public boolean hasMetadata(String path) {
        return jdbcClient.sql("SELECT 1 FROM image_metadata m JOIN images i ON i.id = m.image_id WHERE i.file_path = ? LIMIT 1")
                .param(path)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public Map<String, String> getMetadata(String path) {
        return jdbcClient.sql("""
                    SELECT key, value FROM image_metadata m
                    JOIN images i ON i.id = m.image_id
                    WHERE i.file_path = ?
                """)
                .param(path)
                .query(rs -> {
                    Map<String, String> meta = new HashMap<>();
                    while (rs.next()) {
                        meta.put(rs.getString("key"), rs.getString("value"));
                    }
                    return meta;
                });
    }

    @Transactional
    public void saveMetadata(int imageId, Map<String, String> meta) {
        jdbcClient.sql("DELETE FROM image_metadata WHERE image_id = ?")
                .param(imageId)
                .update();

        for (Map.Entry<String, String> entry : meta.entrySet()) {
            jdbcClient.sql("INSERT INTO image_metadata(image_id, key, value) VALUES(?, ?, ?)")
                    .param(imageId)
                    .param(entry.getKey())
                    .param(entry.getValue())
                    .update();
        }

        updateFtsIndex(imageId);
    }

    public void addTag(int imageId, String tag) {
        jdbcClient.sql("INSERT OR IGNORE INTO image_tags(image_id, tag) VALUES(?, ?)")
                .param(imageId)
                .param(tag)
                .update();
        updateFtsIndex(imageId);
    }

    public void removeTag(int imageId, String tag) {
        jdbcClient.sql("DELETE FROM image_tags WHERE image_id = ? AND tag = ?")
                .param(imageId)
                .param(tag)
                .update();
        updateFtsIndex(imageId);
    }

    public Set<String> getTags(String path) {
        return new HashSet<>(jdbcClient.sql("SELECT tag FROM image_tags t JOIN images i ON i.id = t.image_id WHERE i.file_path = ?")
                .param(path)
                .query(String.class)
                .list());
    }

    public List<File> getPinnedFolders(java.util.function.Function<String, File> resolver) {
        return jdbcClient.sql("SELECT path FROM pinned_folders ORDER BY path ASC")
                .query(String.class)
                .list()
                .stream()
                .map(resolver)
                .filter(f -> f != null && f.exists())
                .toList();
    }

    public void addPinnedFolder(String path) {
        jdbcClient.sql("INSERT OR IGNORE INTO pinned_folders(path) VALUES(?)")
                .param(path)
                .update();
    }

    public void removePinnedFolder(String path) {
        jdbcClient.sql("DELETE FROM pinned_folders WHERE path = ?")
                .param(path)
                .update();
    }

    private void updateFtsIndex(int imageId) {
        jdbcClient.sql("""
                    INSERT OR REPLACE INTO metadata_fts(image_id, global_text)
                    SELECT ?, group_concat(val, ' ') FROM (
                        SELECT value as val FROM image_metadata WHERE image_id = ?
                        UNION ALL
                        SELECT tag as val FROM image_tags WHERE image_id = ?
                    )
                """)
                .param(imageId)
                .param(imageId)
                .param(imageId)
                .update();
    }

    public List<String> getDistinctValues(String key) {
        return jdbcClient.sql("SELECT DISTINCT value FROM image_metadata WHERE key = ? ORDER BY value ASC")
                .param(key)
                .query(String.class)
                .list()
                .stream()
                .filter(val -> val != null && !val.isBlank())
                .toList();
    }
}
