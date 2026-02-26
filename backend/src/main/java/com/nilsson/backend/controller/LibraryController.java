package com.nilsson.backend.controller;

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
import java.util.List;

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
 *   <li><b>Optimized DTO Mapping:</b> Returns a sorted list of {@link ImageDTO} objects for the scanned
 *   folder, using a high-performance bulk fetch to retrieve ratings and model information.</li>
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
            @RequestParam(defaultValue = "false") boolean recursive,
            @RequestParam(defaultValue = "false") boolean skipIndex) {
        
        File folder = pathService.resolve(path);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new ResourceNotFoundException("Folder", path);
        }

        if (!skipIndex) {
            userDataManager.setLastFolder(folder);

            String folderPath = pathService.getNormalizedAbsolutePath(folder);
            List<String> excludedPaths = userDataManager.getExcludedPaths();
            boolean isExcluded = excludedPaths.stream()
                    .anyMatch(ex -> folderPath.startsWith(ex.replace("\\", "/")));

            if (!isExcluded) {
                indexingService.indexFolder(folder, recursive);
                indexingService.startWatching(folder, null);
            }
        }

        List<File> files = indexingService.getImagesInFolder(folder, recursive, userDataManager.getExcludedPaths());
        List<ImageDTO> imageDTOs = userDataManager.getBulkImageDTOs(files);

        return ResponseEntity.ok(imageDTOs);
    }
}
