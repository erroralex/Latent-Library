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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Service for generating, caching, and retrieving image thumbnails with resource-aware concurrency.
 * <p>
 * This service manages the lifecycle of image thumbnails, providing high-performance generation
 * through a combination of virtual threads and semaphore-based CPU throttling. It implements
 * a striped locking mechanism to prevent redundant generation of the same thumbnail and
 * utilizes an atomic write pattern (temp file + move) to ensure cache integrity.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>On-Demand Generation:</b> Creates thumbnails for images as they are requested by the UI.</li>
 *   <li><b>Proactive Caching:</b> Pre-generates thumbnails for batches of images during folder indexing.</li>
 *   <li><b>Concurrency Control:</b> Limits simultaneous CPU-intensive resizing operations to maintain system responsiveness.</li>
 *   <li><b>Request Deduplication:</b> Ensures that multiple requests for the same thumbnail are handled by a single generation task.</li>
 *   <li><b>Persistent Storage:</b> Manages a local disk cache for generated thumbnails, using SHA-256 hashes for unique identification.</li>
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
    
    private final Map<String, CompletableFuture<File>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService preloadExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
        if (sourceFile == null || !sourceFile.exists()) return null;

        String uniqueKey = sourceFile.getAbsolutePath() + "_" + sourceFile.lastModified();
        String hash = computeHash(uniqueKey);
        File thumbnailFile = thumbnailCacheDir.resolve(hash + ".jpg").toFile();

        if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
            return thumbnailFile;
        }

        CompletableFuture<File> future = inFlight.computeIfAbsent(hash, k -> CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = getLock(hash);
            try {
                if (lock.tryLock(30, TimeUnit.SECONDS)) {
                    try {
                        if (thumbnailFile.exists() && thumbnailFile.length() > 0) return thumbnailFile;

                        if (cpuPermits.tryAcquire(30, TimeUnit.SECONDS)) {
                            try {
                                generateThumbnailAtomic(sourceFile, thumbnailFile, hash);
                                return thumbnailFile;
                            } finally {
                                cpuPermits.release();
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Thumbnail generation failed for {}: {}", sourceFile.getName(), e.getMessage());
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.remove(hash);
            }
            return null;
        }, preloadExecutor));

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    public void preloadCache(List<File> files) {
        if (files == null || files.isEmpty()) return;
        for (File file : files) {
            preloadExecutor.submit(() -> getThumbnail(file));
        }
    }

    private ReentrantLock getLock(String key) {
        int index = (key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT;
        return locks[index];
    }

    private void generateThumbnailAtomic(File source, File destination, String hash) throws IOException {
        Path tempFile = thumbnailCacheDir.resolve(hash + ".tmp");
        try {
            Thumbnails.of(source)
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(OUTPUT_QUALITY)
                    .toFile(tempFile.toFile());

            Files.move(tempFile, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
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
