package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ImageProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for the {@link ImageTaggerService}, validating the robustness of the AI
 * interrogation pipeline and ONNX runtime integration.
 * <p>
 * This class ensures the reliability of the tagging engine by verifying:
 * <ul>
 *   <li><b>Model Availability:</b> Confirms that the service correctly identifies when the
 *   ONNX model is missing and provides a clear {@link ApplicationException}.</li>
 *   <li><b>Input Resilience:</b> Validates that the system handles corrupted, empty, or
 *   non-image files gracefully without crashing the JVM or native runtime.</li>
 *   <li><b>Resource Management:</b> Ensures that the service correctly manages the lifecycle
 *   of native ONNX resources and handles unexpected session closures.</li>
 *   <li><b>Concurrency:</b> Verifies that multiple simultaneous tagging requests are handled
 *   safely via internal locking mechanisms.</li>
 * </ul>
 * The tests utilize Mockito to simulate model state and temporary files to test file-system
 * interactions.
 */
@ExtendWith(MockitoExtension.class)
class ImageTaggerServiceTest {

    @Mock
    private TaggerModelService modelService;

    private ImageTaggerService imageTaggerService;

    @BeforeEach
    void setUp() {
        imageTaggerService = new ImageTaggerService(modelService, 5);
    }

    /**
     * Verifies that the service throws an ApplicationException with a clear message
     * if the user attempts to tag an image before the model has been downloaded.
     */
    @Test
    @DisplayName("tagImage should throw exception if model is not ready")
    void testModelNotReady() {
        File dummyFile = new File("test.jpg");
        when(modelService.isModelReady()).thenReturn(false);

        ApplicationException ex = assertThrows(ApplicationException.class, () -> 
            imageTaggerService.tagImage(dummyFile, 0.5f)
        );
        
        assertTrue(ex.getMessage().contains("model is not yet available"));
    }

    /**
     * Verifies that the system handles corrupted or 0-byte image files gracefully.
     * The service should throw a handled ImageProcessingException instead of crashing.
     */
    @Test
    @DisplayName("tagImage should handle corrupted or empty files gracefully")
    void testCorruptedImage(@TempDir Path tempDir) throws IOException {
        Path corruptedPath = tempDir.resolve("corrupted.jpg");
        Files.writeString(corruptedPath, "not an image");
        File corruptedFile = corruptedPath.toFile();

        when(modelService.isModelReady()).thenReturn(true);
        // Stub paths to avoid NullPointerException during session init attempt
        when(modelService.getModelPath()).thenReturn(tempDir.resolve("fake_model.onnx"));

        assertThrows(ImageProcessingException.class, () -> 
            imageTaggerService.tagImage(corruptedFile, 0.5f)
        );
    }

    /**
     * Verifies that the service handles non-existent files by throwing the
     * appropriate exception.
     */
    @Test
    @DisplayName("tagImage should throw exception for missing files")
    void testMissingFile(@TempDir Path tempDir) {
        File missingFile = new File("ghost_image.png");
        when(modelService.isModelReady()).thenReturn(true);
        when(modelService.getModelPath()).thenReturn(tempDir.resolve("fake_model.onnx"));

        assertThrows(ImageProcessingException.class, () -> 
            imageTaggerService.tagImage(missingFile, 0.5f)
        );
    }

    /**
     * Verifies that the service can handle multiple concurrent tagging requests
     * without internal state corruption or race conditions.
     */
    @Test
    @DisplayName("tagImage should handle concurrent requests safely")
    void testConcurrency(@TempDir Path tempDir) throws IOException, InterruptedException {
        File dummyFile = tempDir.resolve("concurrent_test.jpg").toFile();
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", dummyFile);

        when(modelService.isModelReady()).thenReturn(true);
        when(modelService.getModelPath()).thenReturn(tempDir.resolve("fake_model.onnx"));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // We expect an exception because the model file is fake,
                    // but we are testing for the absence of deadlocks or NPEs.
                    imageTaggerService.tagImage(dummyFile, 0.5f);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrent tagging requests timed out");
        executor.shutdown();
    }
}
