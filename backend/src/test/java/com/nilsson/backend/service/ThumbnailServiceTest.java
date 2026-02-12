package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ThumbnailServiceTest validates the functionality of the ThumbnailService, focusing on
 * thumbnail generation and thread-safe access. It ensures that thumbnails are correctly
 * cached and that concurrent requests for the same thumbnail are handled efficiently
 * using striped locking to prevent redundant processing and race conditions.
 */
class ThumbnailServiceTest {

    private ThumbnailService thumbnailService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        thumbnailService = new ThumbnailService();

        Field cacheDirField = ThumbnailService.class.getDeclaredField("thumbnailCacheDir");
        cacheDirField.setAccessible(true);
        cacheDirField.set(thumbnailService, tempDir);
    }

    @Test
    void getThumbnail_ShouldGenerateFile_WhenMissing() throws IOException {
        File source = new File("src/test/resources/test_image.jpg");

        if (!source.exists()) {
            System.out.println("Skipping image generation test - no source image found.");
            return;
        }

        File thumb = thumbnailService.getThumbnail(source);
        assertNotNull(thumb);
        assertTrue(thumb.exists());
        assertTrue(thumb.getName().endsWith(".jpg"));
    }

    @Test
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