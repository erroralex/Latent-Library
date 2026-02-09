package com.nilsson.backend.controller;

import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "http://localhost:5173")
public class FolderController {

    private final UserDataManager dataManager;

    public FolderController(UserDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @GetMapping("/roots")
    public ResponseEntity<List<FileDTO>> getRoots() {
        File[] roots = File.listRoots();
        if (roots == null) return ResponseEntity.ok(new ArrayList<>());
        
        return ResponseEntity.ok(Arrays.stream(roots)
                .filter(File::exists)
                .map(f -> new FileDTO(f.getAbsolutePath(), f.getAbsolutePath(), true, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/children")
    public ResponseEntity<List<FileDTO>> getChildren(@RequestParam String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return ResponseEntity.badRequest().build();

        File[] files = folder.listFiles(File::isDirectory);
        if (files == null) return ResponseEntity.ok(new ArrayList<>());

        return ResponseEntity.ok(Arrays.stream(files)
                .sorted()
                .map(f -> new FileDTO(f.getName(), f.getAbsolutePath(), true, false))
                .collect(Collectors.toList()));
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

    // DTO for frontend consumption
    public record FileDTO(String name, String path, boolean isDirectory, boolean isPinned, String key, String label, String icon, boolean leaf) {
        public FileDTO(String name, String path, boolean isDirectory, boolean isPinned) {
            this(name, path, isDirectory, isPinned, path, name, isDirectory ? "pi pi-folder" : "pi pi-file", !isDirectory);
        }
    }
}
