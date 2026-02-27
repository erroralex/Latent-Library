package com.nilsson.backend.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link CommonStrategy}, validating the parsing of standard AI generation
 * metadata (Automatic1111/Forge format).
 * <p>
 * This class ensures the accuracy of the regex-based extraction engine by verifying:
 * <ul>
 *   <li><b>Prompt Decomposition:</b> Correctly splits positive and negative prompts from the
 *   technical parameter block.</li>
 *   <li><b>Parameter Mapping:</b> Accurately identifies and normalizes key-value pairs such as
 *   Steps, Sampler, CFG, and Seed.</li>
 *   <li><b>LoRA Discovery:</b> Validates the extraction of {@code <lora:...>} tags directly
 *   from the prompt text, including strength values.</li>
 *   <li><b>Advanced Features:</b> Ensures that parameters like Hires. fix and Denoising strength
 *   are correctly interpreted.</li>
 * </ul>
 * The tests cover both standard and edge-case formatting to guarantee robust metadata
 * extraction across different software versions.
 */
class CommonStrategyTest {

    private final CommonStrategy strategy = new CommonStrategy();

    @Test
    @DisplayName("parse should extract standard A1111 metadata")
    void testStandardA1111Parsing() {
        String metadata = """
                A beautiful mountain landscape, sunset, highly detailed
                Negative prompt: blurry, low quality, distorted
                Steps: 20, Sampler: Euler a, Schedule type: Karras, CFG scale: 7, Seed: 12345, Size: 512x512, Model hash: abc123def, Model: sd_xl_base_1.0
                """;

        Map<String, String> results = strategy.parse(metadata);

        assertEquals("A beautiful mountain landscape, sunset, highly detailed", results.get("Prompt"));
        assertEquals("blurry, low quality, distorted", results.get("Negative"));
        assertEquals("20", results.get("Steps"));
        assertEquals("Euler a", results.get("Sampler"));
        assertEquals("Karras", results.get("Scheduler"));
        assertEquals("7", results.get("CFG"));
        assertEquals("12345", results.get("Seed"));
        assertEquals("512", results.get("Width"));
        assertEquals("512", results.get("Height"));
        assertEquals("sd_xl_base_1.0", results.get("Model"));
    }

    @Test
    @DisplayName("parse should extract LoRA tags from prompt")
    void testLoraExtraction() {
        String metadata = """
                1girl, solo, <lora:cyberpunk_v1:0.8>, <lora:neon_lights:1.0>
                Steps: 25, Sampler: DPM++ 2M SDE
                """;

        Map<String, String> results = strategy.parse(metadata);

        assertTrue(results.containsKey("Loras"));
        assertTrue(results.get("Loras").contains("<lora:cyberpunk_v1:0.8>"));
        assertTrue(results.get("Loras").contains("<lora:neon_lights:1.0>"));
    }

    @Test
    @DisplayName("parse should handle missing negative prompt")
    void testMissingNegativePrompt() {
        String metadata = """
                A simple cat sitting on a fence
                Steps: 15, Sampler: Euler
                """;

        Map<String, String> results = strategy.parse(metadata);

        assertEquals("A simple cat sitting on a fence", results.get("Prompt"));
        assertNull(results.get("Negative"));
        assertEquals("15", results.get("Steps"));
    }

    @Test
    @DisplayName("parse should handle Hires. fix parameters")
    void testHiresFixParsing() {
        String metadata = """
                landscape
                Steps: 20, Sampler: Euler, CFG scale: 7, Seed: 1, Size: 512x512, Hires upscale: 2, Denoising strength: 0.5
                """;

        Map<String, String> results = strategy.parse(metadata);

        assertEquals("Enabled (2x)", results.get("Hires. fix"));
        assertEquals("0.5", results.get("Denoise"));
    }

    @Test
    @DisplayName("parse should handle Distilled CFG (Flux format)")
    void testDistilledCfgParsing() {
        String metadata = """
                flux image
                Steps: 20, Sampler: Euler, CFG scale: 1, Distilled CFG scale: 3.5, Seed: 99
                """;

        Map<String, String> results = strategy.parse(metadata);

        assertEquals("1 (distilled 3.5)", results.get("CFG"));
    }
}
