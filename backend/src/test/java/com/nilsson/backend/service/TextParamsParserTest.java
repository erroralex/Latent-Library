package com.nilsson.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextParamsParserTest is designed to validate the orchestration and routing logic of the TextParamsParser service.
 * It ensures that raw metadata strings from various AI generation tools (such as Automatic1111, ComfyUI,
 * and InvokeAI) are correctly identified based on their unique structural signatures and dispatched to
 * the appropriate parsing strategies. The tests verify that the resulting metadata maps are accurately
 * populated with technical parameters and that the service handles unrecognized, null, or empty
 * inputs gracefully without failure.
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