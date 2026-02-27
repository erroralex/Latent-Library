package com.nilsson.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link TextParamsParser}, validating the orchestration and routing
 * logic for diverse AI generation metadata formats.
 * <p>
 * This class ensures that raw metadata strings are correctly identified and dispatched to
 * the appropriate parsing strategies by verifying:
 * <ul>
 *   <li><b>Format Detection:</b> Confirms that the service can distinguish between
 *   Automatic1111 text blocks, ComfyUI JSON graphs, and InvokeAI metadata.</li>
 *   <li><b>Strategy Dispatch:</b> Validates that the correct internal strategy is
 *   invoked based on the structural signatures of the input.</li>
 *   <li><b>Resilience:</b> Ensures that unrecognized, null, or empty inputs are handled
 *   gracefully, returning empty result sets instead of throwing exceptions.</li>
 * </ul>
 */
class TextParamsParserTest {

    private final TextParamsParser parser = new TextParamsParser(null);

    @Test
    @DisplayName("Should detect and parse Common (A1111) format")
    void parse_ShouldDetectCommonFormat() {
        String text = "A beautiful sunset\nSteps: 20, Sampler: Euler a, CFG scale: 7, Seed: 123";
        Map<String, String> result = parser.parse(text);

        assertFalse(result.isEmpty());
        assertEquals("20", result.get("Steps"));
        assertEquals("Euler a", result.get("Sampler"));
    }

    @Test
    @DisplayName("Should detect and parse ComfyUI JSON format")
    void parse_ShouldDetectComfyUI() {
        String json = "{\"3\": {\"inputs\": {\"seed\": 123}, \"class_type\": \"KSampler\"}}";
        Map<String, String> result = parser.parse(json);

        assertFalse(result.isEmpty());
        assertEquals("123", result.get("Seed"));
    }

    @Test
    @DisplayName("Should detect and parse InvokeAI format")
    void parse_ShouldDetectInvokeAI() {
        String json = "{\"app_version\": \"3.0\", \"invokeai\": {}, \"positive_prompt\": \"wizard\"}";
        Map<String, String> result = parser.parse(json);

        assertFalse(result.isEmpty());
        assertEquals("wizard", result.get("Prompt"));
    }

    @Test
    @DisplayName("Should return empty map for unrecognized format")
    void parse_ShouldHandleUnknown() {
        Map<String, String> result = parser.parse("Just some random text without markers");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle null or empty input")
    void parse_ShouldHandleNullEmpty() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }
}
