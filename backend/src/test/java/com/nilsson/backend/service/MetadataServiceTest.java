package com.nilsson.backend.service;

import com.nilsson.backend.strategy.ComfyUIStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link MetadataService}, focusing on the extraction and normalization
 * of technical parameters from AI-generated image metadata.
 * <p>
 * This class validates the service's ability to:
 * <ul>
 *   <li><b>Software Identification:</b> Correcty detect the generation tool (e.g., ComfyUI, A1111)
 *   based on the structure of the embedded metadata.</li>
 *   <li><b>Parameter Extraction:</b> Accurately parse complex JSON and text blocks to retrieve
 *   seeds, steps, samplers, and prompts.</li>
 *   <li><b>Resilience:</b> Gracefully handle malformed JSON, type mismatches, and adversarial
 *   inputs (e.g., deeply nested structures) without crashing or throwing unhandled exceptions.</li>
 * </ul>
 * The tests ensure that the metadata pipeline remains robust across a wide variety of
 * generation software ecosystems and potentially corrupted file states.
 */
class MetadataServiceTest {

    private final TextParamsParser textParamsParser = new TextParamsParser(null);
    private final MetadataService service = new MetadataService(List.of(new ComfyUIStrategy()), textParamsParser);

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

    @Test
    void testMalformedJson_ShouldNotCrash() {
        String truncatedJson = "{\"prompt\": {\"3\": {\"inputs\": {\"seed\": 123";

        Map<String, String> results = service.processRawMetadata(truncatedJson);

        assertNotNull(results);
        assertTrue(results.get("Prompt").contains("Error parsing JSON") || results.get("Prompt").equals(truncatedJson),
                "Should gracefully handle malformed JSON");
    }

    @Test
    void testInvalidDataTypes_ShouldHandleGracefully() {
        String invalidTypeJson = """
                    {
                        "prompt": {
                            "3": {
                                "inputs": {
                                    "steps": "twenty",
                                    "seed": 999
                                },
                                "class_type": "KSampler"
                            }
                        }
                    }
                """;

        Map<String, String> results = service.processRawMetadata(invalidTypeJson);

        assertNotNull(results);
        assertEquals("twenty", results.get("Steps"));
        assertEquals("999", results.get("Seed"));
    }

    @Test
    void testDeeplyNestedJson_ShouldNotStackOverflow() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("{\"a\":");
        }
        sb.append("\"value\"");
        for (int i = 0; i < 500; i++) {
            sb.append("}");
        }

        String deepJson = sb.toString();

        assertDoesNotThrow(() -> service.processRawMetadata(deepJson));
    }
}
