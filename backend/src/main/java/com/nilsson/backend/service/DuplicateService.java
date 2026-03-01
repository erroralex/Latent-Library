package com.nilsson.backend.service;

import com.nilsson.backend.model.DuplicateImageInfo;
import com.nilsson.backend.model.DuplicatePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for identifying and resolving duplicate images using SHA-256 and dHash.
 */
@Service
public class DuplicateService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateService.class);

    private final int hammingThreshold;
    private final JdbcClient jdbcClient;
    private final PathService pathService;
    private final UserDataManager userDataManager;
    private final DHashService dHashService;

    public DuplicateService(DataSource dataSource,
                            PathService pathService,
                            UserDataManager userDataManager,
                            DHashService dHashService,
                            @Value("${app.duplicates.dhash-threshold:2}") int hammingThreshold) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.pathService = pathService;
        this.userDataManager = userDataManager;
        this.dHashService = dHashService;
        this.hammingThreshold = hammingThreshold;
    }

    public Map<String, Object> getStatus() {
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

        return Map.of(
                "missingHashes", missingHashes != null ? missingHashes : 0,
                "totalImages", totalImages != null ? totalImages : 0
        );
    }

    public String scanAndFixHashes() {
        List<String> paths = jdbcClient.sql("""
                            SELECT file_path FROM images 
                            WHERE dhash IS NULL OR dhash = 0 
                            OR file_hash IS NULL OR file_hash = ''
                        """)
                .query(String.class)
                .list();

        log.info("Starting manual hash repair scan for {} images...", paths.size());

        AtomicInteger successCount = new AtomicInteger(0);

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (String path : paths) {
                executor.submit(() -> {
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
            }
        }

        return "Repaired hashes for " + successCount.get() + " images.";
    }

    public List<DuplicatePair> findDuplicatePairs() {
        log.info("Starting duplicate analysis...");
        List<DuplicatePair> pairs = new ArrayList<>();

        List<ImageRecord> exactDuplicates = jdbcClient.sql("""
                    SELECT id, file_path, dhash, file_hash 
                    FROM images 
                    WHERE file_hash IN (
                        SELECT file_hash 
                        FROM images 
                        WHERE file_hash IS NOT NULL AND file_hash != '' 
                        GROUP BY file_hash 
                        HAVING COUNT(*) > 1
                    )
                """)
                .query(ImageRecord.class)
                .list();

        Map<String, List<ImageRecord>> exactGroups = exactDuplicates.stream()
                .collect(Collectors.groupingBy(ImageRecord::file_hash));

        for (List<ImageRecord> group : exactGroups.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    addPair(pairs, group.get(i), group.get(j));
                    if (pairs.size() >= 1000) return pairs;
                }
            }
        }

        List<ImageRecord> allImages = jdbcClient.sql("""
                    SELECT id, file_path, dhash, file_hash 
                    FROM images 
                    WHERE dhash IS NOT NULL AND dhash != 0
                """)
                .query(ImageRecord.class)
                .list();

        if (allImages.isEmpty()) return pairs;

        BKTree bkTree = new BKTree();
        for (ImageRecord img : allImages) {
            if (img.dhash() != null) {
                bkTree.add(img);
            }
        }

        for (ImageRecord img : allImages) {
            if (img.dhash() == null) continue;
            
            List<ImageRecord> matches = bkTree.search(img, hammingThreshold);
            for (ImageRecord match : matches) {
                if (img.id() < match.id()) {
                    if (img.file_hash() != null && match.file_hash() != null && 
                        img.file_hash().equals(match.file_hash())) {
                        continue;
                    }
                    
                    addPair(pairs, img, match);
                    if (pairs.size() >= 1000) return pairs;
                }
            }
        }

        return pairs;
    }

    private void addPair(List<DuplicatePair> pairs, ImageRecord img1, ImageRecord img2) {
        DuplicatePair pair = createPair(img1.file_path(), img2.file_path());
        if (pair != null) {
            pairs.add(pair);
        }
    }

    private DuplicatePair createPair(String path1, String path2) {
        try {
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
        } catch (Exception e) {
            log.warn("Error creating duplicate pair for {} and {}: {}", path1, path2, e.getMessage());
            return null;
        }
    }

    public String autoResolveDuplicates(String strategy) {
        int exactCount = resolveByField("file_hash", strategy);
        int visualCount = resolveByField("dhash", strategy);
        return "Resolved " + (exactCount + visualCount) + " duplicates.";
    }

    private int resolveByField(String field, String strategy) {
        if (!"file_hash".equals(field) && !"dhash".equals(field)) {
            throw new IllegalArgumentException("Invalid field for duplicate resolution: " + field);
        }

        String sql = String.format("""
                    SELECT i.id, i.file_path, i.%s as hash, i.last_scanned, 
                           (SELECT value FROM image_metadata WHERE image_id = i.id AND key = 'Resolution' LIMIT 1) as resolution,
                           (SELECT value FROM image_metadata WHERE image_id = i.id AND key = 'FileSize' LIMIT 1) as filesize
                    FROM images i
                    WHERE i.%s IN (
                        SELECT %s FROM images 
                        WHERE %s IS NOT NULL AND %s != 0 AND %s != ''
                        GROUP BY %s HAVING COUNT(*) > 1
                    )
                """, field, field, field, field, field, field, field);

        List<Map<String, Object>> rows = jdbcClient.sql(sql).query().listOfRows();

        Map<Object, List<Map<String, Object>>> groups = rows.stream()
                .collect(Collectors.groupingBy(row -> row.get("hash")));

        List<String> pathsToDelete = new ArrayList<>();

        for (List<Map<String, Object>> group : groups.values()) {
            if (group.size() < 2) continue;

            Map<String, Object> survivor = selectSurvivor(group, strategy);
            for (Map<String, Object> row : group) {
                if (row != survivor) {
                    pathsToDelete.add((String) row.get("file_path"));
                }
            }
        }

        userDataManager.batchDeleteFiles(pathsToDelete);
        return pathsToDelete.size();
    }

    private Map<String, Object> selectSurvivor(List<Map<String, Object>> group, String strategy) {
        return switch (strategy) {
            case "OLDEST_SCANNED" -> group.stream()
                    .min(java.util.Comparator.comparing(r -> (Long) r.get("last_scanned")))
                    .orElse(group.get(0));
            case "BEST_RESOLUTION" -> group.stream()
                    .max(java.util.Comparator.comparing(r -> parseResolution((String) r.get("resolution"))))
                    .orElse(group.get(0));
            case "LARGEST_FILESIZE" -> group.stream()
                    .max(java.util.Comparator.comparing(r -> parseFileSize((String) r.get("filesize"))))
                    .orElse(group.get(0));
            default -> group.stream() // LATEST_SCANNED (Default)
                    .max(java.util.Comparator.comparing(r -> (Long) r.get("last_scanned")))
                    .orElse(group.get(0));
        };
    }

    private long parseResolution(String res) {
        if (res == null || !res.contains("x")) return 0;
        try {
            String[] parts = res.split("x");
            return Long.parseLong(parts[0].trim()) * Long.parseLong(parts[1].trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseFileSize(String size) {
        if (size == null) return 0;
        try {
            String clean = size.toUpperCase().replace(" ", "");
            if (clean.endsWith("MB")) return (long) (Double.parseDouble(clean.replace("MB", "")) * 1024 * 1024);
            if (clean.endsWith("KB")) return (long) (Double.parseDouble(clean.replace("KB", "")) * 1024);
            if (clean.endsWith("B")) return Long.parseLong(clean.replace("B", ""));
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    record ImageRecord(int id, String file_path, Long dhash, String file_hash) {
    }

    private class BKTree {
        private Node root;

        public void add(ImageRecord record) {
            if (record.dhash() == null) return;
            
            if (root == null) {
                root = new Node(record);
                return;
            }
            add(root, record);
        }

        private void add(Node node, ImageRecord record) {
            int distance = Long.bitCount(node.record.dhash() ^ record.dhash());
            Node child = node.children.get(distance);
            if (child == null) {
                node.children.put(distance, new Node(record));
            } else {
                add(child, record);
            }
        }

        public List<ImageRecord> search(ImageRecord query, int threshold) {
            List<ImageRecord> results = new ArrayList<>();
            if (root != null && query.dhash() != null) {
                search(root, query, threshold, results);
            }
            return results;
        }

        private void search(Node node, ImageRecord query, int threshold, List<ImageRecord> results) {
            int distance = Long.bitCount(node.record.dhash() ^ query.dhash());
            
            if (distance <= threshold && node.record.id() != query.id()) {
                results.add(node.record);
            }

            int min = Math.max(0, distance - threshold);
            int max = distance + threshold;

            for (int i = min; i <= max; i++) {
                Node child = node.children.get(i);
                if (child != null) {
                    search(child, query, threshold, results);
                }
            }
        }

        private class Node {
            final ImageRecord record;
            final Map<Integer, Node> children = new HashMap<>();

            Node(ImageRecord record) {
                this.record = record;
            }
        }
    }
}
