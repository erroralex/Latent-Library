package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Service for generating, caching, and retrieving image thumbnails with resource-aware concurrency.
 * <p>
 * This service provides a high-performance mechanism for managing image thumbnails. It utilizes
 * a striped locking mechanism to minimize contention when multiple threads attempt to access or
 * generate thumbnails for different files. To prevent system resource exhaustion during heavy
 * image processing, it employs a semaphore-based throttling mechanism that limits the number of
 * concurrent resizing operations.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Striped Locking:</b> Reduces lock contention by hashing file paths to a fixed set of
 *   locks, allowing parallel generation of thumbnails for different files.</li>
 *   <li><b>Resource Throttling:</b> Uses a {@link Semaphore} to limit CPU-intensive image resizing
 *   tasks, ensuring the system remains responsive during bulk indexing.</li>
 *   <li><b>Virtual Thread Support:</b> Provides a non-blocking preloading mechanism using Java 21+
 *   virtual threads to fire off hundreds of generation tasks efficiently.</li>
 *   <li><b>Persistent Caching:</b> Stores generated thumbnails in a dedicated {@code data/thumbnails}
 *   directory, using SHA-256 hashes of the file path and modification time for cache keys.</li>
 *   <li><b>Cache Invalidation:</b> Automatically detects changes to source images by including the
 *   last modified timestamp in the cache key, ensuring thumbnails are always up-to-date.</li>
 * </ul>
 */
@Service
public class ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    private static final String THUMBNAIL_DIR = "data/thumbnails";
    private static final int THUMBNAIL_SIZE = 300;
    private static final double OUTPUT_QUALITY = 0.85;

    private static final int STRIPE_COUNT = 128;
    private final ReentrantLock[] locks;
    private final Semaphore cpuPermits;
    private final Path thumbnailCacheDir;

    public ThumbnailService(@Value("${app.data.dir:.}") String appDataDir) {
        this.thumbnailCacheDir = Paths.get(appDataDir).resolve(THUMBNAIL_DIR).toAbsolutePath().normalize();
        try {
            if (!Files.exists(thumbnailCacheDir)) {
                Files.createDirectories(thumbnailCacheDir);
            }
        } catch (IOException e) {
            throw new ApplicationException("Failed to initialize thumbnail cache directory", e);
        }

        this.locks = IntStream.range(0, STRIPE_COUNT)
                .mapToObj(i -> new ReentrantLock())
                .toArray(ReentrantLock[]::new);

        int cores = Runtime.getRuntime().availableProcessors();
        this.cpuPermits = new Semaphore(Math.max(2, cores - 2));
    }

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

    public void preloadCache(List<File> files) {
        if (files == null) {
            throw new ValidationException("File list for preloading cannot be null.");
        }
        if (files.isEmpty()) return;

        logger.info("Starting background preload for {} files using Virtual Threads...", files.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File file : files) {
                executor.submit(() -> getThumbnail(file));
            }
        }

        logger.info("Preload completed.");
    }

    private ReentrantLock getLock(String key) {
        // Use bitwise masking to ensure a positive index, preventing ArrayIndexOutOfBoundsException
        // if hashCode() returns Integer.MIN_VALUE.
        int index = (key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT;
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
            throw new ApplicationException("Critical Failure: SHA-256 algorithm not found", e);
        }
    }
}
