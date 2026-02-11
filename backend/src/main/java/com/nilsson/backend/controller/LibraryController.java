package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for library-wide management, folder scanning, and indexing orchestration.
 * <p>
 * This controller handles the high-level management of the image library. It provides endpoints
 * to initiate folder scans, which trigger background indexing processes (metadata extraction and
 * FTS indexing) via the {@link IndexingService}. To ensure a responsive UI, the controller
 * returns an immediate snapshot of the folder's contents while indexing continues asynchronously.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Folder Scanning:</b> Triggers the indexing pipeline for new or modified images,
 *   respecting user-defined exclusion paths.</li>
 *   <li><b>Snapshot Delivery:</b> Returns a prioritized list of {@link ImageDTO}s for the scanned
 *   folder, including cached ratings and model information for immediate display.</li>
 *   <li><b>State Persistence:</b> Updates the application's "last visited folder" state to
 *   maintain session continuity for the user.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    private final IndexingService indexingService;
    private final UserDataManager userDataManager;
    private final PathService pathService;

    public LibraryController(IndexingService indexingService, UserDataManager userDataManager, PathService pathService) {
        this.indexingService = indexingService;
        this.userDataManager = userDataManager;
        this.pathService = pathService;
    }

    @PostMapping("/scan")
    public ResponseEntity<List<ImageDTO>> scanFolder(@RequestParam String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        userDataManager.setLastFolder(folder);
        
        String folderPath = pathService.getNormalizedAbsolutePath(folder);
        List<String> excludedPaths = userDataManager.getExcludedPaths();
        boolean isExcluded = false;
        for (String excluded : excludedPaths) {
            String normalizedExcluded = excluded.replace("\\", "/");
            if (folderPath.startsWith(normalizedExcluded)) {
                isExcluded = true;
                logger.info("Skipping indexing for excluded folder: {}", folderPath);
                break;
            }
        }

        if (!isExcluded) {
            indexingService.indexFolder(folder);
        }

        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
        });

        if (files == null) return ResponseEntity.ok(List.of());

        List<ImageDTO> imageDTOs = Arrays.stream(files)
                .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()))
                .map(file -> {
                    int rating = userDataManager.getRating(file);
                    String model = "";
                    if (userDataManager.hasCachedMetadata(file)) {
                        Map<String, String> meta = userDataManager.getCachedMetadata(file);
                        model = meta.getOrDefault("Model", "");
                    }
                    return new ImageDTO(file.getAbsolutePath(), rating, model);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(imageDTOs);
    }
}
