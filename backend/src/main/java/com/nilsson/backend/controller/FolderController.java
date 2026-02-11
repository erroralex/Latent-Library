package com.nilsson.backend.controller;

import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for file system navigation and directory management.
 * <p>
 * This controller provides the backend infrastructure for the application's file explorer. It enables
 * the frontend to traverse the local file system by listing root drives and dynamically fetching
 * child directories. It also manages "pinned" folders, allowing users to bookmark frequently
 * accessed locations for rapid navigation.
 * <p>
 * Key functionalities:
 * - Root Discovery: Identifies and returns all available system root drives.
 * - Lazy Directory Traversal: Provides on-demand listing of subdirectories for a given path.
 * - Folder Pinning: Manages persistent bookmarks for specific file system paths.
 * - Data Normalization: Returns {@code FileDTO} objects optimized for PrimeVue's Tree component.
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private static final Logger logger = LoggerFactory.getLogger(FolderController.class);
    private final UserDataManager dataManager;
    private final PathService pathService;

    public FolderController(UserDataManager dataManager, PathService pathService) {
        this.dataManager = dataManager;
        this.pathService = pathService;
    }

    @GetMapping("/roots")
    public ResponseEntity<List<FileDTO>> getRoots() {
        File[] roots = File.listRoots();
        if (roots == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(Arrays.stream(roots)
                .filter(File::exists)
                .map(f -> new FileDTO(f.getAbsolutePath(), f.getAbsolutePath(), true, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/children")
    public ResponseEntity<List<FileDTO>> getChildren(@RequestParam String path) {
        logger.info("Requesting children for path: {}", path);

        File folder = pathService.resolve(path);

        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("Path does not exist or is not directory: {}", path);
            return ResponseEntity.badRequest().build();
        }

        File[] files = folder.listFiles();

        if (files == null) {
            logger.warn("Access denied or IO error reading: {}", path);
            return ResponseEntity.ok(List.of());
        }

        List<FileDTO> dtos = Arrays.stream(files)
                .filter(f -> !f.isHidden())
                .filter(File::isDirectory)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .map(f -> new FileDTO(
                        f.getName().isEmpty() ? f.getAbsolutePath() : f.getName(),
                        pathService.getNormalizedAbsolutePath(f),
                        f.isDirectory(),
                        false
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<FileDTO>> getPinnedFolders() {
        return ResponseEntity.ok(dataManager.getPinnedFolders().stream()
                .map(f -> new FileDTO(f.getName(), pathService.getNormalizedAbsolutePath(f), true, true))
                .collect(Collectors.toList()));
    }

    @PostMapping("/pin")
    public ResponseEntity<Void> pinFolder(@RequestParam String path) {
        File folder = pathService.resolve(path);
        if (folder.exists() && folder.isDirectory()) {
            dataManager.addPinnedFolder(folder);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/unpin")
    public ResponseEntity<Void> unpinFolder(@RequestParam String path) {
        File folder = pathService.resolve(path);
        dataManager.removePinnedFolder(folder);
        return ResponseEntity.ok().build();
    }

    /**
     * Data Transfer Object representing a file system entry, optimized for tree-view rendering.
     */
    public record FileDTO(String name, String path, boolean isDirectory, boolean isPinned, String key, String label,
                          String icon, boolean leaf) {
        public FileDTO(String name, String path, boolean isDirectory, boolean isPinned) {
            this(name, path, isDirectory, isPinned, path, name, isDirectory ? "pi pi-folder" : "pi pi-file", !isDirectory);
        }
    }
}
