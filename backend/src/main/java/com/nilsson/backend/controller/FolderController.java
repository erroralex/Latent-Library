package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
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
 * REST Controller for file system navigation, directory traversal, and bookmark management.
 * <p>
 * This controller provides the backend logic for the application's file explorer. It allows
 * for the discovery of system roots (drives), lazy-loading of directory contents, and
 * management of user-pinned folders. It ensures that all path resolutions are handled
 * consistently via the {@link PathService}.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>System Discovery:</b> Provides access to top-level system roots for initial
 *   navigation.</li>
 *   <li><b>Lazy Traversal:</b> Implements efficient directory listing, filtering out hidden
 *   files and non-directory entries for the navigation tree.</li>
 *   <li><b>Bookmark Management:</b> Facilitates the pinning and unpinning of frequently
 *   accessed folders.</li>
 *   <li><b>DTO Mapping:</b> Transforms raw {@link File} objects into a structured
 *   {@link FileDTO} format suitable for tree-based UI components.</li>
 * </ul>
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

        if (!folder.exists()) {
            throw new ResourceNotFoundException("Folder", path);
        }
        if (!folder.isDirectory()) {
            throw new ValidationException("Path is not a directory: " + path);
        }

        File[] files = folder.listFiles();

        if (files == null) {
            logger.warn("Access denied or IO error reading: {}", path);
            throw new ApplicationException("Cannot access folder (Access Denied): " + path);
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
        if (!folder.exists()) {
            throw new ResourceNotFoundException("Folder", path);
        }
        if (!folder.isDirectory()) {
            throw new ValidationException("Cannot pin a file, only folders.");
        }
        dataManager.addPinnedFolder(folder);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unpin")
    public ResponseEntity<Void> unpinFolder(@RequestParam String path) {
        File folder = pathService.resolve(path);
        dataManager.removePinnedFolder(folder);
        return ResponseEntity.ok().build();
    }

    public record FileDTO(String name, String path, boolean isDirectory, boolean isPinned, String key, String label,
                          String icon, boolean leaf) {
        public FileDTO(String name, String path, boolean isDirectory, boolean isPinned) {
            this(name, path, isDirectory, isPinned, path, name, isDirectory ? "pi pi-folder" : "pi pi-file", !isDirectory);
        }
    }
}
