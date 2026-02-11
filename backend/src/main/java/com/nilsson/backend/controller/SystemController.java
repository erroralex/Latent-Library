package com.nilsson.backend.controller;

import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * REST Controller for system-level operations, application lifecycle management, and OS integration.
 * <p>
 * This controller serves as a bridge between the web-based frontend and the host operating system.
 * It provides critical endpoints for managing the application's lifecycle (shutdown), interacting
 * with native file managers (Explorer/Finder), and performing maintenance tasks such as
 * database cleanup and search index rebuilding.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Lifecycle Management:</b> Facilitates graceful application shutdown by closing the
 *   Spring Boot context in a separate thread.</li>
 *   <li><b>Native OS Integration:</b> Utilizes Java's {@code Desktop} API and {@code ProcessBuilder}
 *   to open folders or highlight specific files within the host's native file explorer.</li>
 *   <li><b>Maintenance & Cleanup:</b> Provides administrative endpoints to clear the SQLite database,
 *   purge generated thumbnails, and trigger background FTS5 index rebuilds.</li>
 *   <li><b>Path Exclusion:</b> Manages a persistent list of directory paths that should be ignored
 *   during library indexing operations.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    private final ConfigurableApplicationContext context;
    private final FtsService ftsService;
    private final PathService pathService;
    private final UserDataManager userDataManager;

    public SystemController(ConfigurableApplicationContext context, FtsService ftsService, PathService pathService, UserDataManager userDataManager) {
        this.context = context;
        this.ftsService = ftsService;
        this.pathService = pathService;
        this.userDataManager = userDataManager;
    }

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        logger.info("Shutdown request received via API.");

        new Thread(() -> {
            try {
                Thread.sleep(500);
                logger.info("Closing application context...");
                context.close();
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.ok("Shutting down");
    }

    @PostMapping("/open-folder")
    public ResponseEntity<String> openFolder(@RequestParam("path") String path) {
        File folder = new File(path).getAbsoluteFile();

        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn("Folder not found: {}", folder.getAbsolutePath());
            return ResponseEntity.badRequest().body("Folder does not exist: " + folder.getAbsolutePath());
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows: Use explorer.exe
                new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                // macOS: Use open command
                new ProcessBuilder("open", folder.getAbsolutePath()).start();
            } else {
                // Linux: Try xdg-open
                new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
            }
            return ResponseEntity.ok("Opened");
        } catch (IOException e) {
            logger.error("Failed to open folder: {}", path, e);
            return ResponseEntity.internalServerError().body("Failed to open folder: " + e.getMessage());
        }
    }

    @PostMapping("/show-in-explorer")
    public ResponseEntity<String> showInExplorer(@RequestParam("path") String path) {
        File file = pathService.resolve(path);
        if (!file.exists()) {
            return ResponseEntity.badRequest().body("File does not exist");
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
            } else {
                Desktop.getDesktop().open(file.getParentFile());
            }
            return ResponseEntity.ok("Command sent");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to show file: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-fts-index")
    public ResponseEntity<String> rebuildFtsIndex() {
        new Thread(ftsService::rebuildFtsIndex).start();
        return ResponseEntity.accepted().body("FTS index rebuild initiated.");
    }

    @PostMapping("/open-data-folder")
    public ResponseEntity<String> openDataFolder() {
        File dataDir = Paths.get(".").resolve("data").toAbsolutePath().normalize().toFile();
        logger.info("Attempting to open data folder at: {}", dataDir.getAbsolutePath());

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return openFolder(dataDir.getAbsolutePath());
    }

    @PostMapping("/clear-database")
    public ResponseEntity<String> clearDatabase() {
        userDataManager.clearDatabase();
        return ResponseEntity.ok("Database cleared.");
    }

    @PostMapping("/clear-thumbnails")
    public ResponseEntity<String> clearThumbnails() {
        Path thumbDir = Paths.get("data", "thumbnails");
        if (Files.exists(thumbDir)) {
            try (Stream<Path> walk = Files.walk(thumbDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Failed to clear thumbnails: " + e.getMessage());
            }
        }
        return ResponseEntity.ok("Thumbnails cleared.");
    }

    @GetMapping("/excluded-paths")
    public ResponseEntity<List<String>> getExcludedPaths() {
        return ResponseEntity.ok(userDataManager.getExcludedPaths());
    }

    @PostMapping("/excluded-paths")
    public ResponseEntity<Void> addExcludedPath(@RequestParam String path) {
        userDataManager.addExcludedPath(path);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/excluded-paths")
    public ResponseEntity<Void> removeExcludedPath(@RequestParam String path) {
        userDataManager.removeExcludedPath(path);
        return ResponseEntity.ok().build();
    }
}
