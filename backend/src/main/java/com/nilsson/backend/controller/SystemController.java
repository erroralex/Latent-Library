package com.nilsson.backend.controller;

import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.PathService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * REST Controller for system-level operations and OS integration.
 * <p>
 * This controller bridges the gap between the web-based frontend and the host operating system.
 * It provides endpoints for lifecycle management (graceful shutdown), native file system
 * interactions (opening folders in Explorer/Finder), and maintenance tasks like rebuilding
 * the search index.
 * <p>
 * Key functionalities:
 * - Graceful Shutdown: Terminates the Spring Boot application context on request.
 * - Native Explorer Integration: Opens directories or highlights specific files in the OS file manager.
 * - Index Maintenance: Triggers background tasks to rebuild the SQLite FTS5 search index.
 * - Desktop API Bridge: Utilizes Java's {@code Desktop} and {@code ProcessBuilder} for cross-platform OS commands.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ConfigurableApplicationContext context;
    private final FtsService ftsService;
    private final PathService pathService;

    public SystemController(ConfigurableApplicationContext context, FtsService ftsService, PathService pathService) {
        this.context = context;
        this.ftsService = ftsService;
        this.pathService = pathService;
    }

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                context.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.ok("Shutting down");
    }

    @PostMapping("/open-folder")
    public ResponseEntity<String> openFolder(@RequestParam("path") String path) {
        File folder = pathService.resolve(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.badRequest().body("Folder does not exist or is not a directory.");
        }

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(folder);
                return ResponseEntity.ok("Opened");
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Failed to open folder: " + e.getMessage());
            }
        }
        return ResponseEntity.internalServerError().body("Desktop API not supported");
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
}
