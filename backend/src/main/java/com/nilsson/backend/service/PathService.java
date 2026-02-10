package com.nilsson.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * A simple utility service for normalizing and resolving file paths for a local-only application.
 * This service does not enforce a root directory, allowing the application to function as a true
 * local file system browser.
 */
@Service
public class PathService {

    private static final Logger logger = LoggerFactory.getLogger(PathService.class);

    public PathService() {
        logger.info("PathService initialized in open (local file browser) mode.");
    }

    /**
     * Resolves a string path into a normalized File object.
     *
     * @param pathString The path to resolve.
     * @return A normalized File object.
     * @throws InvalidPathException if the path string is invalid.
     */
    public File resolve(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            throw new InvalidPathException(pathString, "Path cannot be null or empty.");
        }
        try {
            // Normalize the path to resolve ".." segments and ensure consistent formatting.
            return Path.of(pathString).normalize().toFile();
        } catch (InvalidPathException e) {
            logger.error("Invalid path string provided: {}", pathString, e);
            throw e;
        }
    }

    /**
     * Returns the normalized, absolute path of a file as a string.
     *
     * @param file The file to get the path from.
     * @return The normalized absolute path string, using forward slashes.
     */
    public String getNormalizedAbsolutePath(File file) {
        if (file == null) {
            return null;
        }
        return file.toPath().normalize().toAbsolutePath().toString().replace("\\", "/");
    }
}
