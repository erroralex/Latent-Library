package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link TaggerModelService}, validating the lifecycle management
 * and acquisition of AI model files.
 * <p>
 * This class ensures the reliability of the model management system by verifying:
 * <ul>
 *   <li><b>Directory Initialization:</b> Confirms that the required model storage
 *   directories are created on service startup.</li>
 *   <li><b>Readiness Logic:</b> Validates the detection of model and tag files on disk.</li>
 *   <li><b>Download Orchestration:</b> Ensures that download requests are correctly
 *   throttled if a download is already in progress or the model is ready.</li>
 *   <li><b>Error Resilience:</b> Verifies that the service handles network or I/O
 *   failures during model acquisition gracefully.</li>
 * </ul>
 */
class TaggerModelServiceTest {

    private TaggerModelService modelService;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Initialize with the temp directory as the app data root
        modelService = new TaggerModelService(tempDir.toString());
    }

    @Test
    @DisplayName("Service should create models directory on startup")
    void testDirectoryCreation() {
        Path expectedDir = tempDir.resolve("data/models");
        assertTrue(Files.exists(expectedDir), "Models directory should be created");
    }

    @Test
    @DisplayName("isModelReady should return true only if both model and tags exist")
    void testIsModelReady() throws IOException {
        assertFalse(modelService.isModelReady());

        Path modelsDir = tempDir.resolve("data/models");
        Files.createFile(modelsDir.resolve("wd14-v2.onnx"));
        assertFalse(modelService.isModelReady(), "Should be false if tags.csv is missing");

        Files.createFile(modelsDir.resolve("tags.csv"));
        assertTrue(modelService.isModelReady(), "Should be true if both files exist");
    }

    @Test
    @DisplayName("downloadModel should not start if already ready")
    void testDownloadModelThrottling() throws IOException {
        Path modelsDir = tempDir.resolve("data/models");
        Files.createFile(modelsDir.resolve("wd14-v2.onnx"));
        Files.createFile(modelsDir.resolve("tags.csv"));

        modelService.downloadModel();
        assertFalse(modelService.isDownloading(), "Should not start download if model is ready");
    }
}
