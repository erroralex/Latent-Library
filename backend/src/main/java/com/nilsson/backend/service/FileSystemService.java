package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service dedicated to low-level file system operations.
 * <p>
 * This service encapsulates OS-specific logic such as moving files to the system trash,
 * performing physical file renames, and managing an application-specific fallback trash
 * directory when system-level integration is unavailable.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>System Trash Integration:</b> Utilizes the AWT {@link Desktop} API to move files
 *   to the OS-native trash bin, providing a safe deletion mechanism.</li>
 *   <li><b>Linux Support:</b> Implements specific support for {@code gio trash} and {@code trash-cli}
 *   to ensure reliable deletion on Linux environments where AWT might fail.</li>
 *   <li><b>Fallback Deletion:</b> Implements a custom trash directory ({@code data/trash})
 *   to handle deletions on systems where native trash integration is unsupported or fails.</li>
 *   <li><b>Physical Renaming:</b> Manages the atomic renaming of files on disk while
 *   ensuring destination paths do not conflict with existing files.</li>
 *   <li><b>Path Resolution:</b> Coordinates with application data directories to ensure
 *   all file operations occur within safe, normalized paths.</li>
 * </ul>
 */
@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);
    private static final String APP_TRASH_DIR = "data/trash";

    private final String appDataDir;

    public FileSystemService(@Value("${app.data.dir:.}") String appDataDir) {
        this.appDataDir = appDataDir;
    }

    public boolean moveFileToTrash(File file) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("File to delete", file != null ? file.getAbsolutePath() : "null");
        }

        boolean success = false;
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("linux")) {
            success = tryLinuxTrash(file);
        }

        if (!success && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            try {
                success = Desktop.getDesktop().moveToTrash(file);
            } catch (Exception e) {
                logger.warn("System trash operation failed for: {}. Attempting fallback.", file.getAbsolutePath());
            }
        }

        // Application-level fallback
        if (!success) {
            success = moveFileToAppTrash(file);
        }

        if (!success) {
            throw new ApplicationException("Failed to delete file. System trash unavailable and fallback failed.");
        }

        return true;
    }

    private boolean tryLinuxTrash(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder("gio", "trash", file.getAbsolutePath());
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Moved file to trash using 'gio trash': {}", file.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            logger.debug("'gio trash' failed or not available: {}", e.getMessage());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("trash-put", file.getAbsolutePath());
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Moved file to trash using 'trash-put': {}", file.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            logger.debug("'trash-put' failed or not available: {}", e.getMessage());
        }
        
        return false;
    }

    private boolean moveFileToAppTrash(File file) {
        try {
            Path trashDir = Paths.get(appDataDir).resolve(APP_TRASH_DIR).toAbsolutePath().normalize();
            if (!Files.exists(trashDir)) {
                Files.createDirectories(trashDir);
            }

            String uniqueName = System.currentTimeMillis() + "_" + file.getName();
            Path target = trashDir.resolve(uniqueName);

            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Moved file to application trash: {}", target);
            return true;
        } catch (IOException e) {
            logger.error("Fallback trash operation failed", e);
            return false;
        }
    }

    public File renameFile(File file, String newName) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("File to rename", file != null ? file.getAbsolutePath() : "null");
        }
        if (newName == null || newName.isBlank()) {
            throw new ValidationException("New filename cannot be empty.");
        }
        if (newName.contains("/") || newName.contains("\\") || newName.contains("..")) {
            throw new ValidationException("New filename must not contain path separators or '..'.");
        }

        File parent = file.getParentFile();
        File newFile = new File(parent, newName);

        // Guard against symlink tricks: ensure the resolved destination is still
        // a direct child of the same parent directory.
        if (!newFile.toPath().normalize().getParent().equals(parent.toPath().normalize())) {
            throw new ValidationException("New filename resolves outside the source directory.");
        }

        if (newFile.exists()) {
            throw new ValidationException("A file with that name already exists in the destination folder.");
        }

        boolean success = file.renameTo(newFile);
        if (!success) {
            throw new ApplicationException("Failed to rename file on disk.");
        }

        return newFile;
    }
}
