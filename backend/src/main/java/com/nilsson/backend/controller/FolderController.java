package com.nilsson.backend.controller;

import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for file system navigation and folder management.
 * Handles listing of root drives, directory contents, and pinned folder operations.
 * Provides the data structure for the frontend file tree navigation.
 */
@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "http://localhost:5173")
public class FolderController {

    private static final Logger logger = LoggerFactory.getLogger(FolderController.class);
    private final UserDataManager dataManager;

    public FolderController(UserDataManager dataManager) {
        this.dataManager = dataManager;
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

        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        File folder = new File(path);

        // Security/Robustness: Check existence before canonicalization to avoid IO errors on bad paths
        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("Path does not exist or is not directory: {}", path);
            return ResponseEntity.badRequest().build();
        }

        File[] files = folder.listFiles();

        if (files == null) {
            logger.warn("Access denied or IO error reading: {}", path);
            // Return empty list instead of error so the UI just shows an empty folder
            return ResponseEntity.ok(List.of());
        }

        List<FileDTO> dtos = Arrays.stream(files)
                // Filter out hidden/system files to keep the tree clean
                .filter(f -> !f.isHidden())
                .filter(File::isDirectory)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .map(f -> new FileDTO(
                        f.getName().isEmpty() ? f.getAbsolutePath() : f.getName(), // Handle root drives appearing as children
                        f.getAbsolutePath(),
                        f.isDirectory(),
                        false // "pinned" status calculation can be expensive, skip for general browse
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<FileDTO>> getPinnedFolders() {
        return ResponseEntity.ok(dataManager.getPinnedFolders().stream()
                .map(f -> new FileDTO(f.getName(), f.getAbsolutePath(), true, true))
                .collect(Collectors.toList()));
    }

    @PostMapping("/pin")
    public ResponseEntity<Void> pinFolder(@RequestParam String path) {
        File folder = new File(path);
        if (folder.exists() && folder.isDirectory()) {
            dataManager.addPinnedFolder(folder);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/unpin")
    public ResponseEntity<Void> unpinFolder(@RequestParam String path) {
        File folder = new File(path);
        dataManager.removePinnedFolder(folder);
        return ResponseEntity.ok().build();
    }

    public record FileDTO(String name, String path, boolean isDirectory, boolean isPinned, String key, String label, String icon, boolean leaf) {
        public FileDTO(String name, String path, boolean isDirectory, boolean isPinned) {
            // Logic: If it is a directory, it is NOT a leaf.
            this(name, path, isDirectory, isPinned, path, name, isDirectory ? "pi pi-folder" : "pi pi-file", !isDirectory);
        }
    }
}