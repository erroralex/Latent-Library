package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.FileSystemService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing duplicate image detection and resolution.
 * <p>
 * This controller provides endpoints for identifying visually identical images using perceptual
 * hashing (dHash) and performing bulk resolution of these duplicates. It leverages the
 * {@link UserDataManager} for file system operations and the {@link JdbcClient} for
 * efficient database-level duplicate discovery.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Duplicate Discovery:</b> Identifies pairs of images with identical dHash values,
 *   filtering out uninitialized or zero hashes.</li>
 *   <li><b>Bulk Resolution:</b> Implements an automated cleanup mechanism that keeps the
 *   most recently scanned version of a duplicate set and moves others to the trash.</li>
 *   <li><b>DTO Mapping:</b> Enriches raw database paths with metadata (ratings, models)
 *   to provide a comprehensive view for the frontend comparison tool.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateController.class);
    private final JdbcClient jdbcClient;
    private final PathService pathService;
    private final UserDataManager userDataManager;
    private final FileSystemService fileSystemService;

    public DuplicateController(DataSource dataSource, PathService pathService, UserDataManager userDataManager, FileSystemService fileSystemService) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.pathService = pathService;
        this.userDataManager = userDataManager;
        this.fileSystemService = fileSystemService;
    }

    @GetMapping("/pairs")
    public ResponseEntity<List<DuplicatePair>> getDuplicatePairs() {
        String sql = """
                    SELECT i1.file_path as path1, i2.file_path as path2, i1.dhash
                    FROM images i1
                    JOIN images i2 ON i1.dhash = i2.dhash AND i1.id < i2.id
                    WHERE i1.dhash IS NOT NULL AND i1.dhash != 0
                    ORDER BY i1.dhash
                """;

        logger.info("Fetching duplicate pairs from database...");

        List<DuplicatePair> pairs = jdbcClient.sql(sql)
                .query((rs, rowNum) -> {
                    String path1 = rs.getString("path1");
                    String path2 = rs.getString("path2");

                    File f1 = pathService.resolve(path1);
                    File f2 = pathService.resolve(path2);

                    if (!f1.exists() || !f2.exists()) return null;

                    ImageDTO dto1 = new ImageDTO(path1, userDataManager.getRating(f1), getModel(f1));
                    ImageDTO dto2 = new ImageDTO(path2, userDataManager.getRating(f2), getModel(f2));

                    return new DuplicatePair(dto1, dto2);
                })
                .list()
                .stream()
                .filter(p -> p != null)
                .collect(Collectors.toList());

        logger.info("Found {} duplicate pairs.", pairs.size());
        return ResponseEntity.ok(pairs);
    }

    @PostMapping("/resolve-all")
    public ResponseEntity<String> resolveAllDuplicates() {
        String sql = """
                    SELECT file_path, dhash, last_scanned 
                    FROM images 
                    WHERE dhash IN (
                        SELECT dhash FROM images 
                        WHERE dhash IS NOT NULL AND dhash != 0 
                        GROUP BY dhash HAVING COUNT(*) > 1
                    )
                    ORDER BY dhash, last_scanned DESC
                """;

        List<Map<String, Object>> rows = jdbcClient.sql(sql).query().listOfRows();

        Map<Long, List<String>> groups = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row.get("dhash"),
                        Collectors.mapping(row -> (String) row.get("file_path"), Collectors.toList())
                ));

        List<String> pathsToDelete = new ArrayList<>();

        for (List<String> group : groups.values()) {
            if (group.size() < 2) continue;
            for (int i = 1; i < group.size(); i++) {
                pathsToDelete.add(group.get(i));
            }
        }

        userDataManager.batchDeleteFiles(pathsToDelete);
        return ResponseEntity.ok("Resolved " + pathsToDelete.size() + " duplicates.");
    }

    private String getModel(File file) {
        if (userDataManager.hasCachedMetadata(file)) {
            return userDataManager.getCachedMetadata(file).getOrDefault("Model", "");
        }
        return "";
    }

    public record DuplicatePair(ImageDTO left, ImageDTO right) {
    }
}
