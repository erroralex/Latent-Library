package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for library-wide management and indexing operations.
 * <p>
 * This controller handles the high-level orchestration of the image library. It provides endpoints
 * to initiate folder scans, which trigger background indexing processes (metadata extraction and
 * FTS indexing). It also returns an immediate snapshot of the folder's contents to the frontend
 * to ensure a responsive user experience while background tasks continue.
 * <p>
 * Key functionalities:
 * - Folder Scanning: Triggers the {@code IndexingService} to process new or modified images.
 * - Snapshot Delivery: Returns a list of {@code ImageDTO}s for the scanned folder, including
 * cached ratings and model information.
 * - State Persistence: Updates the application's "last visited folder" state.
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final IndexingService indexingService;
    private final UserDataManager userDataManager;

    public LibraryController(IndexingService indexingService, UserDataManager userDataManager) {
        this.indexingService = indexingService;
        this.userDataManager = userDataManager;
    }

    @PostMapping("/scan")
    public ResponseEntity<List<ImageDTO>> scanFolder(@RequestParam String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        userDataManager.setLastFolder(folder);
        indexingService.indexFolder(folder);

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
