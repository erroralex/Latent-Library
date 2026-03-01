package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.IndexingStatusTracker;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
 *   <li><b>Pagination:</b> Supports paginated retrieval of folder contents to enable infinite scrolling
 *   on the frontend.</li>
 *   <li><b>Progress Monitoring:</b> Exposes the current status of background indexing jobs.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    private final IndexingService indexingService;
    private final UserDataManager userDataManager;
    private final PathService pathService;
    private final IndexingStatusTracker statusTracker;

    public LibraryController(IndexingService indexingService, UserDataManager userDataManager, PathService pathService, IndexingStatusTracker statusTracker) {
        this.indexingService = indexingService;
        this.userDataManager = userDataManager;
        this.pathService = pathService;
        this.statusTracker = statusTracker;
    }

    /**
     * Scans a folder and returns a paginated list of images.
     * <p>
     * This endpoint performs two distinct actions:
     * 1. Triggers an asynchronous background index of the folder (unless skipIndex is true).
     * 2. Returns the first page (or requested page) of images currently known in the database for that folder.
     *
     * @param path       The absolute path of the folder to scan.
     * @param recursive  Whether to include subfolders in the scan and result set.
     * @param skipIndex  If true, skips the background indexing step (useful for pure navigation).
     * @param page       The zero-based page index (default: 0).
     * @param size       The size of the page to return (default: 100).
     * @return A {@link ResponseEntity} containing a {@link Page} of {@link ImageDTO}s.
     */
    @PostMapping("/scan")
    public ResponseEntity<Page<ImageDTO>> scanFolder(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive,
            @RequestParam(defaultValue = "false") boolean skipIndex,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        
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

        Pageable pageable = PageRequest.of(page, size);
        Page<ImageDTO> imagePage = userDataManager.getImagesInFolderPaginated(folder, recursive, pageable);

        return ResponseEntity.ok(imagePage);
    }

    /**
     * Retrieves the real-time status of the current background indexing operation.
     *
     * @return A {@link IndexingStatusTracker.IndexingStatus} DTO containing progress metrics.
     */
    @GetMapping("/indexing-status")
    public ResponseEntity<IndexingStatusTracker.IndexingStatus> getIndexingStatus() {
        return ResponseEntity.ok(statusTracker.getStatus());
    }
}
