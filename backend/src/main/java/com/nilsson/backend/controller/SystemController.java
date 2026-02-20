package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;
import java.util.stream.Stream;

/**
 * REST Controller for system-level operations, application lifecycle management, and OS integration.
 * <p>
 * This controller provides a centralized interface for administrative and system-level tasks.
 * It handles application shutdown, OS-specific file explorer integration, database maintenance,
 * and management of application-wide settings such as excluded paths and themes.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Lifecycle Management:</b> Facilitates graceful application shutdown via API.</li>
 *   <li><b>OS Integration:</b> Implements cross-platform logic to open folders and reveal
 *   files in the native system explorer (Windows Explorer, macOS Finder, etc.).</li>
 *   <li><b>Database Maintenance:</b> Provides endpoints for clearing AI tags, purging
 *   unorganized images, and performing full library re-indexing.</li>
 *   <li><b>Storage Cleanup:</b> Manages the deletion of cached thumbnails and AI tagging
 *   models to reclaim disk space.</li>
 *   <li><b>Settings Persistence:</b> Handles the retrieval and update of user preferences,
 *   including excluded directory paths and UI themes.</li>
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
    private final IndexingService indexingService;
    private final DatabaseService databaseService;
    private final String appDataDir;
    private final String version;

    public SystemController(ConfigurableApplicationContext context,
                            FtsService ftsService,
                            PathService pathService,
                            UserDataManager userDataManager,
                            IndexingService indexingService,
                            DatabaseService databaseService,
                            @Value("${app.data.dir:.}") String appDataDir,
                            @Value("${project.version:0.0.1-SNAPSHOT}") String version) {
        this.context = context;
        this.ftsService = ftsService;
        this.pathService = pathService;
        this.userDataManager = userDataManager;
        this.indexingService = indexingService;
        this.databaseService = databaseService;
        this.appDataDir = appDataDir;
        this.version = version;
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

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of("version", version));
    }

    @PostMapping("/open-folder")
    public ResponseEntity<String> openFolder(@RequestParam("path") String path) {
        File folder = pathService.resolve(path);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new ResourceNotFoundException("Folder", folder.getAbsolutePath());
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", folder.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
            }
            return ResponseEntity.ok("Opened");
        } catch (IOException e) {
            logger.error("Failed to open folder: {}", path, e);
            throw new ApplicationException("Failed to open folder in OS explorer: " + e.getMessage());
        }
    }

    @PostMapping("/show-in-explorer")
    public ResponseEntity<String> showInExplorer(@RequestParam("path") String path) {
        File file = pathService.resolve(path);
        if (!file.exists()) {
            throw new ResourceNotFoundException("File", path);
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
            throw new ApplicationException("Failed to reveal file in explorer: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-fts-index")
    public ResponseEntity<String> rebuildFtsIndex() {
        new Thread(ftsService::rebuildFtsIndex).start();
        return ResponseEntity.accepted().body("FTS index rebuild initiated.");
    }

    @PostMapping("/re-index-all")
    public ResponseEntity<String> reIndexAll() {
        new Thread(() -> {
            logger.info("Starting full library re-index...");
            userDataManager.clearDatabase();
            File lastFolder = userDataManager.getLastFolder();
            if (lastFolder != null) {
                indexingService.indexFolder(lastFolder);
            }
            logger.info("Full re-index completed.");
        }).start();
        return ResponseEntity.accepted().body("Full re-index initiated.");
    }

    @PostMapping("/clear-ai-tags")
    public ResponseEntity<String> clearAiTags() {
        databaseService.clearAiTags();
        return ResponseEntity.ok("AI tags cleared.");
    }

    @PostMapping("/clear-unorganized")
    public ResponseEntity<String> clearUnorganized() {
        databaseService.clearUnorganizedImages();
        return ResponseEntity.ok("Unorganized images cleared.");
    }

    @PostMapping("/open-data-folder")
    public ResponseEntity<String> openDataFolder() {
        File dataDir = Paths.get(appDataDir).resolve("data").toAbsolutePath().normalize().toFile();
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
        Path thumbDir = Paths.get(appDataDir).resolve("data/thumbnails").toAbsolutePath().normalize();
        if (Files.exists(thumbDir)) {
            try (Stream<Path> walk = Files.walk(thumbDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(thumbDir))
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new ApplicationException("Failed to clear thumbnails: " + e.getMessage());
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

    @GetMapping("/custom-nodes/prompt")
    public ResponseEntity<List<String>> getCustomPromptNodes() {
        return ResponseEntity.ok(userDataManager.getCustomPromptNodes());
    }

    @PostMapping("/custom-nodes/prompt")
    public ResponseEntity<Void> addCustomPromptNode(@RequestParam String node) {
        userDataManager.addCustomPromptNode(node);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/custom-nodes/prompt")
    public ResponseEntity<Void> removeCustomPromptNode(@RequestParam String node) {
        userDataManager.removeCustomPromptNode(node);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/custom-nodes/lora")
    public ResponseEntity<List<String>> getCustomLoraNodes() {
        return ResponseEntity.ok(userDataManager.getCustomLoraNodes());
    }

    @PostMapping("/custom-nodes/lora")
    public ResponseEntity<Void> addCustomLoraNode(@RequestParam String node) {
        userDataManager.addCustomLoraNode(node);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/custom-nodes/lora")
    public ResponseEntity<Void> removeCustomLoraNode(@RequestParam String node) {
        userDataManager.removeCustomLoraNode(node);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/last-folder")
    public ResponseEntity<Map<String, String>> getLastFolder() {
        File folder = userDataManager.getLastFolder();
        if (folder != null) {
            return ResponseEntity.ok(Map.of("path", pathService.getNormalizedAbsolutePath(folder)));
        }
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/theme")
    public ResponseEntity<Map<String, String>> getTheme() {
        return ResponseEntity.ok(Map.of("theme", userDataManager.getSettings().getTheme()));
    }

    @PostMapping("/theme")
    public ResponseEntity<Void> setTheme(@RequestParam String theme) {
        userDataManager.updateSettings(s -> s.setTheme(theme));
        return ResponseEntity.ok().build();
    }
}
