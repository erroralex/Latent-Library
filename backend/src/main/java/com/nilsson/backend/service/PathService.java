package com.nilsson.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Utility service for path normalization and resolution in a local-first environment.
 * <p>
 * This service provides a centralized mechanism for handling file system paths across the application.
 * It ensures that paths are consistently formatted (e.g., using forward slashes) and correctly
 * resolved regardless of the host operating system. Designed for a local file browser context,
 * it allows for navigation across the entire accessible file system.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Path Normalization:</b> Converts OS-specific path separators into a consistent,
 *   web-friendly forward-slash format to prevent database inconsistencies.</li>
 *   <li><b>Safe Resolution:</b> Resolves string-based paths into {@link File} objects,
 *   providing robust error handling for malformed or illegal path segments.</li>
 *   <li><b>Absolute Path Generation:</b> Provides a reliable way to obtain normalized absolute
 *   paths for persistent storage and cross-service communication.</li>
 * </ul>
 */
@Service
public class PathService {

    private static final Logger logger = LoggerFactory.getLogger(PathService.class);

    public PathService() {
        logger.info("PathService initialized in open (local file browser) mode.");
    }

    public File resolve(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            throw new InvalidPathException(pathString, "Path cannot be null or empty.");
        }
        try {
            return Path.of(pathString).normalize().toFile();
        } catch (InvalidPathException e) {
            logger.error("Invalid path string provided: {}", pathString, e);
            throw e;
        }
    }

    public String getNormalizedAbsolutePath(File file) {
        if (file == null) {
            return null;
        }
        return file.toPath().normalize().toAbsolutePath().toString().replace("\\", "/");
    }
}
