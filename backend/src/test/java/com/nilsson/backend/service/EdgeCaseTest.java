package com.nilsson.backend.service;

import com.nilsson.backend.strategy.ComfyUIStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress and resilience test suite for core application services, validating behavior
 * under extreme or malformed input conditions.
 * <p>
 * This class ensures the stability of the system by verifying:
 * <ul>
 *   <li><b>Payload Limits:</b> Confirms that the {@link MetadataService} can process
 *   exceptionally large metadata strings (e.g., 1MB+ prompts) without memory exhaustion.</li>
 *   <li><b>Character Encoding:</b> Validates full UTF-8 support, ensuring that complex
 *   Unicode characters, emojis, and non-Latin scripts are preserved during parsing.</li>
 *   <li><b>I/O Resilience:</b> Ensures that the {@link ThumbnailService} handles corrupted,
 *   empty, or non-image files gracefully without throwing unhandled exceptions.</li>
 * </ul>
 * These tests are critical for maintaining system uptime when processing untrusted or
 * potentially corrupted user data.
 */
class EdgeCaseTest {

    private final TextParamsParser textParamsParser = new TextParamsParser(null);
    private final MetadataService metadataService = new MetadataService(List.of(new ComfyUIStrategy()), textParamsParser);

    @Test
    @DisplayName("MetadataService should handle extremely large prompt strings")
    void testLargeMetadata() {
        String largePrompt = "a".repeat(1024 * 1024);
        String json = "{\"6\": {\"inputs\": {\"text\": \"" + largePrompt + "\"}, \"class_type\": \"CLIPTextEncode\"}}";

        Map<String, String> results = metadataService.processRawMetadata(json);

        assertNotNull(results);
        assertTrue(results.get("Prompt").length() >= 1024 * 1024);
    }

    @Test
    @DisplayName("MetadataService should handle unusual character encodings (UTF-8)")
    void testEncoding() {
        String unicodePrompt = "🚀 Space Cat 🐱 with 漢字 and symbols ©®";
        String json = "{\"6\": {\"inputs\": {\"text\": \"" + unicodePrompt + "\"}, \"class_type\": \"CLIPTextEncode\"}}";

        Map<String, String> results = metadataService.processRawMetadata(json);

        assertEquals(unicodePrompt, results.get("Prompt"));
    }

    @Test
    @DisplayName("ThumbnailService should handle non-existent or corrupted files gracefully")
    void testCorruptFile(@TempDir Path tempDir) throws IOException {
        ThumbnailService thumbnailService = new ThumbnailService(tempDir.toString(), 300, 0.85, 128, Optional.empty());
        File corruptFile = tempDir.resolve("corrupt.jpg").toFile();
        Files.writeString(corruptFile.toPath(), "not an image");

        assertDoesNotThrow(() -> {
            thumbnailService.getThumbnail(corruptFile);
        });
    }
}
