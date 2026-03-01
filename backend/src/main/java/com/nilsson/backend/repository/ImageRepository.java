package com.nilsson.backend.repository;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.ImageInfo;
import com.nilsson.backend.model.UpdateMetadataRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for managing core image entities and their persistent state within the library.
 * <p>
 * This class serves as the primary data access layer for the {@code images} table. It handles
 * the registration of new files, tracking of file movements via cryptographic hashes, and
 * management of user-facing attributes such as ratings, "starred" status, and custom
 * metadata overrides. It also maintains the "missing" state for files that are indexed
 * but currently unreachable on disk.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>File Registration & Tracking:</b> Implements logic that uses file hashes to detect
 *   moved or renamed files, preserving metadata across path changes.</li>
 *   <li><b>State Management:</b> Persists and retrieves image ratings, starred status, and
 *   perceptual hashes (dHash) used for similarity detection.</li>
 *   <li><b>Custom Metadata:</b> Manages user-defined notes and prompt/model overrides
 *   that supplement or replace extracted technical metadata.</li>
 *   <li><b>Bulk Data Retrieval:</b> Provides an optimized method to fetch essential image information
 *   for a large list of file paths in a single query.</li>
 *   <li><b>Batch Operations:</b> Provides efficient batch deletion mechanisms to handle
 *   large-scale library reconciliation or folder removals.</li>
 *   <li><b>Pagination:</b> Supports efficient paginated retrieval of images for infinite scroll.</li>
 * </ul>
 */
@Repository
public class ImageRepository {

    private static final Logger log = LoggerFactory.getLogger(ImageRepository.class);
    private final JdbcClient jdbcClient;

    public ImageRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public Map<String, ImageInfo> getBulkImageInfo(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = String.join(",", Collections.nCopies(paths.size(), "?"));
        String sql = """
            SELECT
                i.file_path,
                i.rating,
                (SELECT value FROM image_metadata WHERE image_id = i.id AND key = 'Model' LIMIT 1) as model
            FROM images i
            WHERE i.file_path IN (""" + placeholders + ")";

        return jdbcClient.sql(sql)
                .params(paths)
                .query((rs, rowNum) -> new ImageInfo(
                        rs.getString("file_path"),
                        rs.getInt("rating"),
                        rs.getString("model")
                ))
                .list()
                .stream()
                .collect(Collectors.toMap(ImageInfo::path, info -> info));
    }

    /**
     * Retrieves a paginated list of image paths for a specific folder, optionally including subfolders.
     *
     * @param folderPath The absolute path of the root folder to search.
     * @param recursive  If true, includes images from all subdirectories.
     * @param pageable   The pagination information (page number and size).
     * @return A {@link Page} containing the list of file paths for the requested chunk.
     */
    public Page<String> findPathsByFolder(String folderPath, boolean recursive, Pageable pageable) {
        String normalizedPath = folderPath.replace("\\", "/");
        String countSql;
        String querySql;
        List<Object> params;

        if (recursive) {
            countSql = "SELECT COUNT(*) FROM images WHERE file_path LIKE ? || '%'";
            querySql = "SELECT file_path FROM images WHERE file_path LIKE ? || '%' ORDER BY last_scanned DESC, file_path ASC LIMIT ? OFFSET ?";
            params = List.of(normalizedPath, pageable.getPageSize(), pageable.getOffset());
        } else {
            String globPattern = normalizedPath + "/*";
            String excludePattern = normalizedPath + "/*/*";
            
            countSql = "SELECT COUNT(*) FROM images WHERE file_path GLOB ? AND file_path NOT GLOB ?";
            querySql = "SELECT file_path FROM images WHERE file_path GLOB ? AND file_path NOT GLOB ? ORDER BY last_scanned DESC, file_path ASC LIMIT ? OFFSET ?";
            params = List.of(globPattern, excludePattern, pageable.getPageSize(), pageable.getOffset());
        }

        Long total = jdbcClient.sql(countSql)
                .params(recursive ? List.of(normalizedPath) : List.of(params.get(0), params.get(1)))
                .query(Long.class)
                .single();

        List<String> paths = jdbcClient.sql(querySql)
                .params(params)
                .query(String.class)
                .list();

        return new PageImpl<>(paths, pageable, total);
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

    @Transactional
    public void updateCustomMetadata(int id, UpdateMetadataRequest request) {
        if (id <= 0) {
            throw new ValidationException("Invalid image ID provided for metadata update.");
        }
        jdbcClient.sql("UPDATE images SET user_notes = ?, custom_prompt = ?, custom_negative_prompt = ?, custom_model = ? WHERE id = ?")
                .param(request.userNotes())
                .param(request.customPrompt())
                .param(request.customNegativePrompt())
                .param(request.customModel())
                .param(id)
                .update();
    }
}
