package com.nilsson.backend.repository;

import com.nilsson.backend.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 <h2>ImageRepository</h2>
 */
@Repository
public class ImageRepository {

    private static final Logger logger = LoggerFactory.getLogger(ImageRepository.class);
    private final DatabaseService db;
    private static final int BATCH_SIZE = 500;

    public ImageRepository(DatabaseService db) {
        this.db = db;
    }

    // --- Core Identity ---

    public int getIdByPath(String path) {
        String sql = "SELECT id FROM images WHERE file_path = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch ID for path: {}", path, e);
        }
        return -1;
    }

    public List<String> findPathsByHash(String hash) {
        List<String> paths = new ArrayList<>();
        String sql = "SELECT file_path FROM images WHERE file_hash = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) paths.add(rs.getString("file_path"));
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch paths by hash", e);
        }
        return paths;
    }

    public void updatePath(String oldPath, String newPath) {
        String sql = "UPDATE images SET file_path = ? WHERE file_path = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPath);
            pstmt.setString(2, oldPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update path from {} to {}", oldPath, newPath, e);
        }
    }

    public int getOrCreateId(String path, String hash) throws SQLException {
        try (Connection conn = db.connect()) {
            String insertSql = "INSERT OR IGNORE INTO images(file_path, file_hash, last_scanned) VALUES(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, path);
                pstmt.setString(2, hash);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.executeUpdate();
            }

            String selectSql = "SELECT id FROM images WHERE file_path = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, path);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }
        throw new SQLException("Failed to get ID for " + path);
    }

    public void deleteByPath(String path) {
        String sql = "DELETE FROM images WHERE file_path = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete record: {}", path, e);
        }
    }

    public void forEachFilePath(Consumer<String> action) {
        String sql = "SELECT file_path FROM images";
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String path = rs.getString("file_path");
                if (path != null) {
                    action.accept(path);
                }
            }
        } catch (SQLException e) {
            logger.error("Error streaming file paths", e);
        }
    }

    // --- Search ---

    public List<String> findPaths(String query, Map<String, String> filters, int limit) {
        List<String> results = new ArrayList<>();
        try (Connection conn = db.connect()) {
            StringBuilder sql = new StringBuilder("SELECT DISTINCT i.file_path FROM images i ");
            List<Object> params = new ArrayList<>();

            sql.append("WHERE 1=1 ");

            if (query != null && !query.isBlank()) {
                sql.append("AND (");
                sql.append("EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.value LIKE ?) ");
                sql.append("OR ");
                sql.append("EXISTS (SELECT 1 FROM image_tags t WHERE t.image_id = i.id AND t.tag LIKE ?) ");
                sql.append(") ");
                String likeQuery = "%" + query + "%";
                params.add(likeQuery);
                params.add(likeQuery);
            }

            if (filters != null) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    if (entry.getValue() == null || "All".equals(entry.getValue())) continue;

                    if ("Rating".equals(entry.getKey())) {
                        if ("Any Star Count".equals(entry.getValue())) {
                            sql.append("AND i.rating > 0 ");
                        } else {
                            sql.append("AND i.rating = ? ");
                            params.add(entry.getValue());
                        }
                    } else if ("Loras".equals(entry.getKey())) {
                        sql.append("AND EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.key = ? AND m.value LIKE ?) ");
                        params.add(entry.getKey());
                        params.add("%" + entry.getValue() + "%");
                    } else if ("Tag".equals(entry.getKey())) {
                        sql.append("AND EXISTS (SELECT 1 FROM image_tags t WHERE t.image_id = i.id AND t.tag LIKE ?) ");
                        params.add("%" + entry.getValue() + "%");
                    } else {
                        sql.append("AND EXISTS (SELECT 1 FROM image_metadata m WHERE m.image_id = i.id AND m.key = ? AND m.value = ?) ");
                        params.add(entry.getKey());
                        params.add(entry.getValue());
                    }
                }
            }
            sql.append("LIMIT ?");
            params.add(limit);

            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                for (int k = 0; k < params.size(); k++) pstmt.setObject(k + 1, params.get(k));
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) results.add(rs.getString("file_path"));
            }
        } catch (SQLException e) {
            logger.error("Search failed", e);
        }
        return results;
    }

    // --- Attributes ---

    public int getRating(String path) {
        String sql = "SELECT rating FROM images WHERE file_path = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("rating");
        } catch (SQLException e) {
            logger.error("Failed to get rating for path: {}", path, e);
        }
        return 0;
    }

    public void setRating(int id, int rating) {
        String sql = "UPDATE images SET rating = ? WHERE id = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, rating);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to set rating", e);
        }
    }

    public List<String> getStarredPaths() {
        List<String> results = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT file_path FROM images WHERE is_starred = 1")) {
            while (rs.next()) results.add(rs.getString("file_path"));
        } catch (SQLException e) {
            logger.error("Failed to fetch starred", e);
        }
        return results;
    }

    // --- Metadata & Tags ---

    public boolean hasMetadata(String path) {
        String sql = "SELECT 1 FROM image_metadata m JOIN images i ON i.id = m.image_id WHERE i.file_path = ? LIMIT 1";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Failed to check metadata existence for path: {}", path, e);
            return false;
        }
    }

    public Map<String, String> getMetadata(String path) {
        Map<String, String> meta = new HashMap<>();
        String sql = """
                    SELECT key, value FROM image_metadata m
                    JOIN images i ON i.id = m.image_id
                    WHERE i.file_path = ?
                """;
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) meta.put(rs.getString("key"), rs.getString("value"));
        } catch (SQLException e) {
            logger.error("Failed to fetch metadata: {}", path, e);
        }
        return meta;
    }

    public void saveMetadata(int imageId, Map<String, String> meta) {
        String deleteSql = "DELETE FROM image_metadata WHERE image_id = ?";
        String insertSql = "INSERT INTO image_metadata(image_id, key, value) VALUES(?, ?, ?)";

        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setInt(1, imageId);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    pstmt.setInt(1, imageId);
                    pstmt.setString(2, entry.getKey());
                    pstmt.setString(3, entry.getValue());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            updateFtsIndex(imageId);
        } catch (SQLException e) {
            logger.error("Failed to save metadata", e);
        }
    }

    public void addTag(int imageId, String tag) {
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO image_tags(image_id, tag) VALUES(?, ?)")) {
            pstmt.setInt(1, imageId);
            pstmt.setString(2, tag);
            pstmt.executeUpdate();
            updateFtsIndex(imageId);
        } catch (SQLException e) {
            logger.error("Failed to add tag", e);
        }
    }

    public void removeTag(int imageId, String tag) {
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM image_tags WHERE image_id = ? AND tag = ?")) {
            pstmt.setInt(1, imageId);
            pstmt.setString(2, tag);
            pstmt.executeUpdate();
            updateFtsIndex(imageId);
        } catch (SQLException e) {
            logger.error("Failed to remove tag", e);
        }
    }

    public Set<String> getTags(String path) {
        Set<String> tags = new HashSet<>();
        String sql = "SELECT tag FROM image_tags t JOIN images i ON i.id = t.image_id WHERE i.file_path = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) tags.add(rs.getString("tag"));
        } catch (SQLException e) {
            logger.error("Failed to get tags for path: {}", path, e);
        }
        return tags;
    }

    // --- Pinned Folders ---

    public List<File> getPinnedFolders(java.util.function.Function<String, File> resolver) {
        List<File> result = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT path FROM pinned_folders ORDER BY path ASC")) {
            while (rs.next()) {
                File f = resolver.apply(rs.getString("path"));
                if (f != null && f.exists()) result.add(f);
            }
        } catch (SQLException e) {
            logger.error("Error fetching pinned folders", e);
        }
        return result;
    }

    public void addPinnedFolder(String path) {
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO pinned_folders(path) VALUES(?)")) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to add pinned folder: {}", path, e);
        }
    }

    public void removePinnedFolder(String path) {
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM pinned_folders WHERE path = ?")) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to remove pinned folder: {}", path, e);
        }
    }

    // --- Internal Helpers ---

    private void updateFtsIndex(int imageId) {
        String sql = """
                    INSERT OR REPLACE INTO metadata_fts(image_id, global_text)
                    SELECT ?, group_concat(val, ' ') FROM (
                        SELECT value as val FROM image_metadata WHERE image_id = ?
                        UNION ALL
                        SELECT tag as val FROM image_tags WHERE image_id = ?
                    )
                """;
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, imageId);
            pstmt.setInt(2, imageId);
            pstmt.setInt(3, imageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("FTS Update failed", e);
        }
    }

    public List<String> getDistinctValues(String key) {
        List<String> results = new ArrayList<>();
        String sql = "SELECT DISTINCT value FROM image_metadata WHERE key = ? ORDER BY value ASC";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String val = rs.getString("value");
                if (val != null && !val.isBlank()) {
                    results.add(val);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch distinct values for key: {}", key, e);
        }
        return results;
    }
}
