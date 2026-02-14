package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.AppSettings;
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
 * <p>
 * This controller provides the backend logic for a high-efficiency image triage tool. It
 * manages the configuration of source and target directories and facilitates rapid file
 * movement between these locations. It also implements a basic undo mechanism to revert
 * the most recent move operation.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Configuration Management:</b> Persists and retrieves user-defined input and
 *   target folder paths for the sorting workflow.</li>
 *   <li><b>File Discovery:</b> Lists all images in the configured input directory, sorted
 *   by modification date for efficient triage.</li>
 *   <li><b>Rapid Movement:</b> Executes physical file moves to pre-configured target
 *   slots, ensuring unique filenames in the destination to prevent overwrites.</li>
 *   <li><b>Undo Support:</b> Facilitates the restoration of a moved file to its original
 *   location.</li>
 * </ul>
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
        AppSettings.SpeedSorterSettings settings = dataManager.getSettings().getSpeedSorter();

        Map<String, Object> config = new HashMap<>();
        config.put("inputDir", settings.getInputDir());

        List<Map<String, String>> targets = new ArrayList<>();
        List<String> paths = settings.getTargets();

        for (int i = 0; i < 5; i++) {
            String path = (i < paths.size()) ? paths.get(i) : null;
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
            dataManager.updateSettings(s -> s.getSpeedSorter().setInputDir(folder.getAbsolutePath()));
            return ResponseEntity.ok().build();
        }
        throw new ValidationException("Input path must be a valid directory.");
    }

    @PostMapping("/config/target")
    public ResponseEntity<Void> setTargetFolder(@RequestParam("index") int index, @RequestParam("path") String path) {
        File folder = new File(path);
        if (index >= 0 && index < 5 && folder.exists() && folder.isDirectory()) {
            dataManager.updateSettings(s -> {
                List<String> targets = s.getSpeedSorter().getTargets();
                while (targets.size() <= index) {
                    targets.add(null);
                }
                targets.set(index, folder.getAbsolutePath());
            });
            return ResponseEntity.ok().build();
        }
        throw new ValidationException("Invalid target index or directory path.");
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> getFiles() {
        String inputPath = dataManager.getSettings().getSpeedSorter().getInputDir();
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
        List<String> targets = dataManager.getSettings().getSpeedSorter().getTargets();

        if (targetIndex < 0 || targetIndex >= targets.size() || targets.get(targetIndex) == null) {
            throw new ValidationException("Target slot " + targetIndex + " is not configured.");
        }

        String targetPathStr = targets.get(targetIndex);
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
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path cannot be empty.");
        }
        dataManager.batchDeleteFiles(List.of(path));
        return ResponseEntity.ok("Deleted");
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
