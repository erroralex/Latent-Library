package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for the Speed Sorter utility.
 */
@RestController
@RequestMapping("/api/speedsorter")
public class SpeedSorterController {

    private final UserDataManager dataManager;

    public SpeedSorterController(UserDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        String inputDir = dataManager.getSetting("speed_input_dir", null);
        config.put("inputDir", inputDir);

        List<Map<String, String>> targets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String path = dataManager.getSetting("speed_target_" + i, null);
            Map<String, String> target = new HashMap<>();
            target.put("index", String.valueOf(i));
            target.put("path", path);
            target.put("name", path != null ? new File(path).getName() : "");
            targets.add(target);
        }
        config.put("targets", targets);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config/input")
    public ResponseEntity<Void> setInputFolder(@RequestParam("path") String path) {
        File folder = new File(path);
        if (folder.exists() && folder.isDirectory()) {
            dataManager.setSetting("speed_input_dir", folder.getAbsolutePath());
            return ResponseEntity.ok().build();
        }
        throw new ValidationException("Input path must be a valid directory.");
    }

    @PostMapping("/config/target")
    public ResponseEntity<Void> setTargetFolder(@RequestParam("index") int index, @RequestParam("path") String path) {
        File folder = new File(path);
        if (index >= 0 && index < 5 && folder.exists() && folder.isDirectory()) {
            dataManager.setSetting("speed_target_" + index, folder.getAbsolutePath());
            return ResponseEntity.ok().build();
        }
        throw new ValidationException("Invalid target index or directory path.");
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> getFiles() {
        String inputPath = dataManager.getSetting("speed_input_dir", null);
        if (inputPath == null) return ResponseEntity.ok(Collections.emptyList());

        File dir = new File(inputPath);
        if (!dir.exists() || !dir.isDirectory()) return ResponseEntity.ok(Collections.emptyList());

        File[] files = dir.listFiles((d, name) -> {
            String low = name.toLowerCase();
            return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".webp");
        });

        if (files == null) return ResponseEntity.ok(Collections.emptyList());

        List<String> paths = Arrays.stream(files)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

        return ResponseEntity.ok(paths);
    }

    @PostMapping("/move")
    public ResponseEntity<String> moveFile(@RequestParam("source") String sourcePath, @RequestParam("targetIndex") int targetIndex) {
        String targetPathStr = dataManager.getSetting("speed_target_" + targetIndex, null);
        if (targetPathStr == null) {
            throw new ValidationException("Target slot " + targetIndex + " is not configured.");
        }

        File source = new File(sourcePath);
        File targetDir = new File(targetPathStr);

        if (!source.exists()) throw new ResourceNotFoundException("Source file", sourcePath);
        if (!targetDir.exists()) throw new ResourceNotFoundException("Target directory", targetPathStr);

        File dest = generateUniqueDest(targetDir, source.getName());
        try {
            Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(dest.getAbsolutePath());
        } catch (IOException e) {
            throw new ApplicationException("Failed to move file: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam("path") String path) {
        File file = new File(path);
        if (!file.exists()) throw new ResourceNotFoundException("File", path);

        if (dataManager.moveFileToTrash(file)) {
            return ResponseEntity.ok("Deleted");
        }
        throw new ApplicationException("Failed to delete file (System Trash operation failed).");
    }

    @PostMapping("/undo")
    public ResponseEntity<String> undoMove(@RequestParam("source") String sourcePath, @RequestParam("original") String originalPath) {
        File current = new File(sourcePath);
        File original = new File(originalPath);

        if (!current.exists()) throw new ResourceNotFoundException("File to undo", sourcePath);

        try {
            Files.move(current.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok("Undone");
        } catch (IOException e) {
            throw new ApplicationException("Undo failed: " + e.getMessage());
        }
    }

    private File generateUniqueDest(File folder, String name) {
        File dest = new File(folder, name);
        if (!dest.exists()) return dest;

        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        String ext = (dot > 0) ? name.substring(dot) : "";
        return new File(folder, base + "_" + System.currentTimeMillis() + ext);
    }
}