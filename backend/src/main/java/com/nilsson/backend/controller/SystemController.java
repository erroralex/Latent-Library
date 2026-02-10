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
 * Controller for system-level operations.
 * Handles interactions with the host operating system, such as opening folders in the native file explorer
 * and graceful shutdown.
 */
@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "http://localhost:5173")
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
        // Execute shutdown in a separate thread to ensure the response is sent back to the client first
        new Thread(() -> {
            try {
                Thread.sleep(100); // Give a small buffer for the response to flush
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
                // For Windows, "explorer.exe /select," is the command to highlight a file.
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                // For macOS, "open -R" reveals the file in Finder.
                new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
            } else {
                // For Linux/other, fall back to opening the parent directory.
                Desktop.getDesktop().open(file.getParentFile());
            }
            return ResponseEntity.ok("Command sent");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to show file: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-fts-index")
    public ResponseEntity<String> rebuildFtsIndex() {
        // Run in a background thread to not block the HTTP request
        new Thread(ftsService::rebuildFtsIndex).start();
        return ResponseEntity.accepted().body("FTS index rebuild initiated.");
    }
}
