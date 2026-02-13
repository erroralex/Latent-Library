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
 */
@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);
    private static final String APP_TRASH_DIR = "data/trash";
    
    private final String appDataDir;

    public FileSystemService(@Value("${app.data.dir:.}") String appDataDir) {
        this.appDataDir = appDataDir;
    }

    /**
     * Attempts to move a file to the system trash. Falls back to an application-specific
     * trash directory if the OS does not support the operation or if it fails.
     *
     * @param file The file to delete.
     * @return true if the file was successfully moved to trash.
     * @throws ResourceNotFoundException if the file does not exist.
     * @throws ApplicationException if both system trash and fallback fail.
     */
    public boolean moveFileToTrash(File file) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("File to delete", file != null ? file.getAbsolutePath() : "null");
        }

        boolean success = false;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            try {
                success = Desktop.getDesktop().moveToTrash(file);
            } catch (Exception e) {
                logger.warn("System trash operation failed for: {}. Attempting fallback.", file.getAbsolutePath());
            }
        }

        if (!success) {
            success = moveFileToAppTrash(file);
        }

        if (!success) {
            throw new ApplicationException("Failed to delete file. System trash unavailable and fallback failed.");
        }
        
        return true;
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

    /**
     * Renames a file on the physical disk.
     *
     * @param file    The source file.
     * @param newName The new filename (not full path).
     * @return The new File object.
     * @throws ResourceNotFoundException if the source file is missing.
     * @throws ValidationException       if the new name is empty or already exists.
     * @throws ApplicationException      if the OS rename operation fails.
     */
    public File renameFile(File file, String newName) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("File to rename", file != null ? file.getAbsolutePath() : "null");
        }
        if (newName == null || newName.isBlank()) {
            throw new ValidationException("New filename cannot be empty.");
        }

        File parent = file.getParentFile();
        File newFile = new File(parent, newName);

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
