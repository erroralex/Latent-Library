package com.nilsson.imagetoolbox.service;

import com.nilsson.backend.service.MetadataService;
import com.nilsson.backend.service.strategy.ComfyUIStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 Unit tests for {@link MetadataService} focusing on the extraction and parsing
 of AI generation metadata from various raw string formats.
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

        // 1. Identity
        assertEquals("ComfyUI", results.get("Software"), "Should identify software as ComfyUI");

        // 2. Prompt Extraction
        assertTrue(results.containsKey("Prompt"), "Should contain Prompt key");
        assertTrue(results.get("Prompt").contains("beautiful landscape"), "Prompt should contain the text input");

        // 3. Technical Parameters
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
