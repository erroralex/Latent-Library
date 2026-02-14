package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.service.DHashService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * REST Controller for managing duplicate image detection and resolution.
 * <p>
 * This controller provides a comprehensive suite of tools for identifying and resolving
 * both exact byte-for-byte duplicates and visually similar images. It utilizes a combination
 * of cryptographic file hashing (SHA-256) and perceptual hashing (dHash) to detect
 * duplicates across different resolutions or compression levels.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Duplicate Discovery:</b> Implements a high-performance Hamming distance
 *   comparison logic to find visually similar images within a configurable threshold.</li>
 *   <li><b>Hash Repair:</b> Provides a manual trigger to scan the library for images
 *   missing perceptual or cryptographic hashes and backfill them.</li>
 *   <li><b>Automated Resolution:</b> Offers a "Resolve All" mechanism that automatically
 *   keeps the most recently scanned version of a duplicate set and purges the rest.</li>
 *   <li><b>Status Reporting:</b> Tracks the health of the image index by reporting the
 *   number of images missing critical hash data.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    private static final Logger log = LoggerFactory.getLogger(DuplicateController.class);
    private static final int HAMMING_THRESHOLD = 2;

    private final JdbcClient jdbcClient;
    private final PathService pathService;
    private final UserDataManager userDataManager;
    private final DHashService dHashService;
    private final ImageRepository imageRepo;

    public DuplicateController(DataSource dataSource,
                               PathService pathService,
                               UserDataManager userDataManager,
                               DHashService dHashService,
                               ImageRepository imageRepo) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.pathService = pathService;
        this.userDataManager = userDataManager;
        this.dHashService = dHashService;
        this.imageRepo = imageRepo;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Integer missingHashes = jdbcClient.sql("""
                            SELECT COUNT(*) FROM images 
                            WHERE dhash IS NULL OR dhash = 0 
                            OR file_hash IS NULL OR file_hash = ''
                        """)
                .query(Integer.class)
                .single();

        Integer totalImages = jdbcClient.sql("SELECT COUNT(*) FROM images")
                .query(Integer.class)
                .single();

        return ResponseEntity.ok(Map.of(
                "missingHashes", missingHashes,
                "totalImages", totalImages
        ));
    }

    @PostMapping("/scan")
    public ResponseEntity<String> scanForMissingHashes() {
        List<String> paths = jdbcClient.sql("""
                            SELECT file_path FROM images 
                            WHERE dhash IS NULL OR dhash = 0 
                            OR file_hash IS NULL OR file_hash = ''
                        """)
                .query(String.class)
                .list();

        log.info("Starting manual hash repair scan for {} images...", paths.size());

        AtomicInteger successCount = new AtomicInteger(0);

        paths.parallelStream().forEach(path -> {
            try {
                File file = pathService.resolve(path);
                if (file.exists()) {
                    long dhash = dHashService.calculateDHash(file);
                    String fileHash = userDataManager.calculateHash(file);

                    jdbcClient.sql("UPDATE images SET dhash = ?, file_hash = ? WHERE file_path = ?")
                            .param(dhash)
                            .param(fileHash)
                            .param(path)
                            .update();
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.warn("Failed to calculate hashes for: {}", path);
            }
        });

        return ResponseEntity.ok("Repaired hashes for " + successCount.get() + " images.");
    }

    @GetMapping("/pairs")
    public ResponseEntity<List<DuplicatePair>> getDuplicatePairs() {
        log.info("Fetching all images for duplicate analysis...");

        List<ImageRecord> allImages = jdbcClient.sql("SELECT id, file_path, dhash, file_hash FROM images WHERE (dhash IS NOT NULL AND dhash != 0) OR (file_hash IS NOT NULL AND file_hash != '')")
                .query(ImageRecord.class)
                .list();

        List<DuplicatePair> pairs = new ArrayList<>();

        // Perform Hamming Distance and File Hash comparison in Java (O(N^2) but fast for longs)
        for (int i = 0; i < allImages.size(); i++) {
            ImageRecord img1 = allImages.get(i);
            for (int j = i + 1; j < allImages.size(); j++) {
                ImageRecord img2 = allImages.get(j);

                boolean isDuplicate = false;

                if (img1.file_hash() != null && !img1.file_hash().isEmpty() && img1.file_hash().equals(img2.file_hash())) {
                    isDuplicate = true;
                } else if (img1.dhash() != 0 && img2.dhash() != 0) {
                    int distance = Long.bitCount(img1.dhash() ^ img2.dhash());
                    if (distance <= HAMMING_THRESHOLD) {
                        isDuplicate = true;
                    }
                }

                if (isDuplicate) {
                    DuplicatePair pair = createPair(img1.file_path(), img2.file_path());
                    if (pair != null) {
                        pairs.add(pair);
                    }
                }

                if (pairs.size() >= 1000) break;
            }
            if (pairs.size() >= 1000) break;
        }

        log.info("Found {} duplicate pairs.", pairs.size());
        return ResponseEntity.ok(pairs);
    }

    private DuplicatePair createPair(String path1, String path2) {
        File f1 = pathService.resolve(path1);
        File f2 = pathService.resolve(path2);

        if (!f1.exists() || !f2.exists()) return null;

        Map<String, String> meta1 = userDataManager.getCachedMetadata(f1);
        Map<String, String> meta2 = userDataManager.getCachedMetadata(f2);

        int rating1 = userDataManager.getRating(f1);
        int rating2 = userDataManager.getRating(f2);

        return new DuplicatePair(
                new DuplicateImageInfo(path1, rating1, meta1),
                new DuplicateImageInfo(path2, rating2, meta2)
        );
    }

    @PostMapping("/resolve-all")
    public ResponseEntity<String> resolveAllDuplicates() {
        int exactCount = resolveByField("file_hash");
        int visualCount = resolveByField("dhash");

        return ResponseEntity.ok("Resolved " + (exactCount + visualCount) + " duplicates.");
    }

    private int resolveByField(String field) {
        String sql = String.format("""
                    SELECT file_path, %s as hash, last_scanned 
                    FROM images 
                    WHERE %s IN (
                        SELECT %s FROM images 
                        WHERE %s IS NOT NULL AND %s != 0 AND %s != ''
                        GROUP BY %s HAVING COUNT(*) > 1
                    )
                    ORDER BY %s, last_scanned DESC
                """, field, field, field, field, field, field, field, field);

        List<Map<String, Object>> rows = jdbcClient.sql(sql).query().listOfRows();

        Map<Object, List<String>> groups = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.get("hash"),
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
        return pathsToDelete.size();
    }

    private String getModel(File file) {
        if (userDataManager.hasCachedMetadata(file)) {
            return userDataManager.getCachedMetadata(file).getOrDefault("Model", "");
        }
        return "";
    }

    public record ImageRecord(int id, String file_path, long dhash, String file_hash) {
    }

    public record DuplicateImageInfo(String path, int rating, Map<String, String> metadata) {
    }

    public record DuplicatePair(DuplicateImageInfo left, DuplicateImageInfo right) {
    }
}
