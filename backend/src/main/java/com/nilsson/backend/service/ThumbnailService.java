package com.nilsson.backend.service;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Service for generating, caching, and retrieving image thumbnails.
 *
 * <p>This service provides a high-performance mechanism for managing image thumbnails. It utilizes
 * a striped locking mechanism to minimize contention when multiple threads attempt to access or
 * generate thumbnails for different files. To prevent system resource exhaustion during heavy
 * image processing, it employs a semaphore-based throttling mechanism that limits the number of
 * concurrent resizing operations.
 *
 * <p>Key features include:
 * <ul>
 *   <li><b>Striped Locking:</b> Reduces lock contention by hashing file paths to a fixed set of locks.</li>
 *   <li><b>Resource Throttling:</b> Uses a {@link java.util.concurrent.Semaphore} to limit CPU-intensive image resizing.</li>
 *   <li><b>Virtual Thread Support:</b> Provides a non-blocking preloading mechanism using Java 21+ virtual threads.</li>
 *   <li><b>Persistent Caching:</b> Stores generated thumbnails in a dedicated directory within the user's home folder.</li>
 *   <li><b>Cache Invalidation:</b> Uses a combination of file path and last modified timestamp to ensure thumbnails are updated when source images change.</li>
 * </ul>
 *
 * <p>The service leverages the Thumbnailator library for high-quality image resizing and
 * TwelveMonkeys ImageIO plugins for extended format support.
 */
@Service
public class ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    private static final String THUMBNAIL_DIR = ".aitoolbox/thumbnails";
    private static final int THUMBNAIL_SIZE = 300;
    private static final double OUTPUT_QUALITY = 0.85;

    private static final int STRIPE_COUNT = 128;
    private final ReentrantLock[] locks;
    private final Semaphore cpuPermits;
    private final Path thumbnailCacheDir;

    public ThumbnailService() {
        this.thumbnailCacheDir = Paths.get(System.getProperty("user.home")).resolve(THUMBNAIL_DIR);
        try {
            if (!Files.exists(thumbnailCacheDir)) {
                Files.createDirectories(thumbnailCacheDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize thumbnail cache directory", e);
        }

        this.locks = IntStream.range(0, STRIPE_COUNT)
                .mapToObj(i -> new ReentrantLock())
                .toArray(ReentrantLock[]::new);

        int cores = Runtime.getRuntime().availableProcessors();
        this.cpuPermits = new Semaphore(Math.max(2, cores - 2));
    }

    /**
     * Retrieves a thumbnail file, generating it if necessary.
     * Thread-safe and non-blocking for OS threads (uses ReentrantLock).
     */
    public File getThumbnail(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }

        String uniqueKey = sourceFile.getAbsolutePath() + "_" + sourceFile.lastModified();
        String hash = computeHash(uniqueKey);
        File thumbnailFile = thumbnailCacheDir.resolve(hash + ".jpg").toFile();

        if (thumbnailFile.exists()) {
            return thumbnailFile;
        }

        ReentrantLock lock = getLock(hash);
        lock.lock();
        try {
            if (thumbnailFile.exists()) {
                return thumbnailFile;
            }

            try {
                cpuPermits.acquire();
                generateThumbnail(sourceFile, thumbnailFile);
                return thumbnailFile;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                logger.warn("Failed to generate thumbnail for: {} ({})", sourceFile.getName(), e.getMessage());
                return null;
            } finally {
                cpuPermits.release();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Java 21 Feature: Preloads thumbnails for a list of files using Virtual Threads.
     * This allows us to fire off hundreds of tasks immediately, letting the Semaphore
     * manage the actual CPU load.
     */
    public void preloadCache(List<File> files) {
        if (files == null || files.isEmpty()) return;

        logger.info("Starting background preload for {} files using Virtual Threads...", files.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File file : files) {
                executor.submit(() -> getThumbnail(file));
            }
        }

        logger.info("Preload completed.");
    }

    private ReentrantLock getLock(String key) {
        int index = Math.abs(key.hashCode() % STRIPE_COUNT);
        return locks[index];
    }

    private void generateThumbnail(File source, File destination) throws IOException {
        Thumbnails.of(source)
                .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                .outputFormat("jpg")
                .outputQuality(OUTPUT_QUALITY)
                .toFile(destination);
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}