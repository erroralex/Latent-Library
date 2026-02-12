package com.nilsson.backend.service;

import com.nilsson.backend.strategy.ComfyUIStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetadataServiceTest provides unit tests for the MetadataService, focusing on the
 * extraction and parsing of AI generation metadata from various raw string formats.
 * It verifies the service's ability to correctly identify the generation software
 * (e.g., ComfyUI), extract complex prompt structures, and parse technical parameters
 * like seeds, steps, and samplers. The tests ensure that the metadata extraction
 * pipeline is robust and handles both valid and empty metadata scenarios gracefully.
 */
class MetadataServiceTest {

    private final MetadataService service = new MetadataService(List.of(new ComfyUIStrategy()));

    @Test
    void testParseComfyUIJson() {
        String json = """
                    {
                        "prompt": {
                            "3": {
                                "inputs": {
                                    "seed": 847593291,
                                    "steps": 25,
                                    "cfg": 7.0,
                                    "sampler_name": "euler",
                                    "scheduler": "normal",
                                    "denoise": 1.0,
                                    "model": ["4", 0],
                                    "positive": ["6", 0],
                                    "negative": ["7", 0],
                                    "latent_image": ["5", 0]
                                },
                                "class_type": "KSampler"
                            },
                            "6": {
                                "inputs": { "text": "A beautiful landscape, mountains" },
                                "class_type": "CLIPTextEncode"
                            }
                        }
                    }
                """;

        Map<String, String> results = service.processRawMetadata(json);

        assertNotNull(results, "Result map should not be null");

        assertEquals("ComfyUI", results.get("Software"), "Should identify software as ComfyUI");

        assertTrue(results.containsKey("Prompt"), "Should contain Prompt key");
        assertTrue(results.get("Prompt").contains("beautiful landscape"), "Prompt should contain the text input");

        assertEquals("847593291", results.get("Seed"), "Seed should match KSampler input");
        assertEquals("25", results.get("Steps"), "Steps should match KSampler input");

        String cfg = results.get("CFG");
        assertNotNull(cfg, "CFG should be present");
        assertEquals(7.0, Double.parseDouble(cfg), 0.001, "CFG value should be numerically equivalent to 7.0");

        assertEquals("euler", results.get("Sampler"), "Sampler name should be extracted");
        assertEquals("normal", results.get("Scheduler"), "Scheduler should be extracted");
    }

    @Test
    void testEmptyMetadata() {
        Map<String, String> results = service.processRawMetadata("");
        assertEquals("No metadata found in this image.", results.get("Prompt"));

        results = service.processRawMetadata(null);
        assertEquals("No metadata found in this image.", results.get("Prompt"));
    }
}