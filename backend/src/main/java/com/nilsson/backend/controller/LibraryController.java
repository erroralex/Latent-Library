package com.nilsson.backend.controller;

import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/library")
@CrossOrigin(origins = "http://localhost:5173")
public class LibraryController {

    private final IndexingService indexingService;
    private final UserDataManager userDataManager;

    public LibraryController(IndexingService indexingService, UserDataManager userDataManager) {
        this.indexingService = indexingService;
        this.userDataManager = userDataManager;
    }

    @PostMapping("/scan")
    public ResponseEntity<List<String>> scanFolder(@RequestParam String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        // Update last accessed folder
        userDataManager.setLastFolder(folder);

        // Trigger background indexing
        indexingService.indexFolder(folder);

        // Return initial file list immediately for UI responsiveness
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
        });

        if (files == null) return ResponseEntity.ok(List.of());

        List<String> paths = Arrays.stream(files)
                .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())) // Newest first
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

        return ResponseEntity.ok(paths);
    }
}
