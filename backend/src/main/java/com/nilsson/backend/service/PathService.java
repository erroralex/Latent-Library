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
 * This service provides a centralized mechanism for handling file system paths. It ensures
 * that paths are consistently formatted (e.g., using forward slashes) and correctly resolved
 * across different operating systems. Unlike traditional web applications, this service
 * operates in an "open" mode, allowing navigation across the entire local file system.
 * <p>
 * Key functionalities:
 * - Path Normalization: Converts OS-specific paths into a consistent, forward-slash format.
 * - Safe Resolution: Resolves string-based paths into {@code File} objects while handling invalid segments.
 * - Absolute Path Generation: Provides a reliable way to obtain normalized absolute paths for database storage.
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
