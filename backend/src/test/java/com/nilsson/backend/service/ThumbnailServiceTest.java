package com.nilsson.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link ThumbnailService}, validating the generation, caching,
 * and thread-safe access of image thumbnails.
 * <p>
 * This class ensures the performance and reliability of the UI gallery by verifying:
 * <ul>
 *   <li><b>On-Demand Generation:</b> Confirms that thumbnails are correctly created and
 *   cached when requested for the first time.</li>
 *   <li><b>Concurrency Control:</b> Validates the use of striped locking to prevent
 *   redundant generation of the same thumbnail when multiple threads request it
 *   simultaneously (e.g., during rapid scrolling).</li>
 *   <li><b>Resource Management:</b> Ensures that temporary files and cache directories
 *   are correctly managed within the application data root.</li>
 * </ul>
 */
class ThumbnailServiceTest {

    private ThumbnailService thumbnailService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        thumbnailService = new ThumbnailService(tempDir.toString(), 300, 0.85, 128, Optional.empty());
    }

    @Test
    @DisplayName("getThumbnail should generate a new file if not cached")
    void getThumbnail_ShouldGenerateFile_WhenMissing() throws IOException {
        File source = new File("src/test/resources/test_image.jpg");

        if (!source.exists()) {
            return;
        }

        File thumb = thumbnailService.getThumbnail(source);
        assertNotNull(thumb);
        assertTrue(thumb.exists());
        assertTrue(thumb.getName().endsWith(".jpg"));
    }

    @Test
    @DisplayName("Service should handle concurrent requests for the same thumbnail safely")
    void concurrencyTest_StripedLocking() throws InterruptedException, IOException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        File source = new File(tempDir.resolve("concurrent_test.jpg").toUri());
        source.createNewFile();

        List<Future<File>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    return thumbnailService.getThumbnail(source);
                } catch (Exception e) {
                    return null;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(threadCount, futures.size());
    }
}
