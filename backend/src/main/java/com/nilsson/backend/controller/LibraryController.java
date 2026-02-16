package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST Controller for library-wide management, folder scanning, and indexing orchestration.
 * <p>
 * This controller serves as the entry point for synchronizing the application's database with
 * the local file system. It provides endpoints for scanning specific directories, which
 * triggers background indexing of metadata and thumbnails while respecting user-defined
 * exclusion rules.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Folder Scanning:</b> Initiates the indexing process for a given directory path,
 *   ensuring that new images are registered and metadata is extracted.</li>
 *   <li><b>Exclusion Enforcement:</b> Verifies that requested paths are not within user-defined
 *   excluded directories before proceeding with indexing.</li>
 *   <li><b>State Persistence:</b> Updates the application's "last visited folder" setting
 *   upon successful scan.</li>
 *   <li><b>DTO Mapping:</b> Returns a sorted list of {@link ImageDTO} objects for the scanned
 *   folder, enriched with ratings and model information for immediate UI display.</li>
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
    public ResponseEntity<List<ImageDTO>> scanFolder(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) {
        
        File folder = pathService.resolve(path);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new ResourceNotFoundException("Folder", path);
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
            indexingService.indexFolder(folder, recursive);
        }

        List<File> files = new ArrayList<>();
        if (recursive) {
            try (Stream<Path> stream = Files.walk(folder.toPath())) {
                stream.filter(p -> !Files.isDirectory(p))
                      .filter(p -> {
                          String pStr = pathService.getNormalizedAbsolutePath(p.toFile());
                          for (String excluded : excludedPaths) {
                              if (pStr.startsWith(excluded.replace("\\", "/"))) return false;
                          }
                          return true;
                      })
                      .map(Path::toFile)
                      .filter(this::isImageFile)
                      .forEach(files::add);
            } catch (IOException e) {
                logger.error("Failed to walk directory recursively: {}", folderPath, e);
                throw new ApplicationException("Failed to scan subfolders. Please check file permissions.", e);
            }
        } else {
            File[] directFiles = folder.listFiles((dir, name) -> isImageFile(new File(dir, name)));
            if (directFiles != null) {
                files.addAll(Arrays.asList(directFiles));
            }
        }

        List<ImageDTO> imageDTOs = files.stream()
                .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()))
                .map(file -> {
                    int rating = userDataManager.getRating(file);
                    String model = "";
                    if (userDataManager.hasCachedMetadata(file)) {
                        Map<String, String> meta = userDataManager.getCachedMetadata(file);
                        model = meta.getOrDefault("Model", "");
                    }
                    return new ImageDTO(pathService.getNormalizedAbsolutePath(file), rating, model);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(imageDTOs);
    }

    private boolean isImageFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }
}
