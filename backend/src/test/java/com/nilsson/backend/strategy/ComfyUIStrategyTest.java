package com.nilsson.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Exhaustive test suite for the {@link ComfyUIStrategy}, validating the complex graph-traversal
 * and metadata extraction logic for ComfyUI workflows.
 * <p>
 * This class ensures the accuracy of the extraction engine by verifying:
 * <ul>
 *   <li><b>UI Schema Traversal:</b> Confirms that the strategy can navigate the node-link
 *   structure of exported UI workflows to resolve prompts and parameters.</li>
 *   <li><b>API Schema Parsing:</b> Validates the extraction of generation data from
 *   execution graphs (API format), including recursive parameter resolution.</li>
 *   <li><b>Reroute & Utility Handling:</b> Ensures that prompts are correctly traced through
 *   reroute, switch, and other passthrough nodes.</li>
 *   <li><b>Custom Node Integration:</b> Verifies that user-defined custom nodes are
 *   correctly identified and parsed based on application settings.</li>
 *   <li><b>LoRA Discovery:</b> Validates LoRA extraction from both specialized loader
 *   nodes and embedded prompt tags.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ComfyUIStrategyTest {

    @Mock
    private UserDataManager userDataManager;

    private ComfyUIStrategy strategy;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        strategy = new ComfyUIStrategy(userDataManager);
    }

    @Test
    @DisplayName("extract should resolve prompts from UI Schema with Reroute nodes")
    void testUiSchemaWithReroute() throws Exception {
        // Simplified UI Schema: CLIPTextEncode -> Reroute -> KSampler
        // Note: Seed must be > 1,000,000 to be recognized by the strategy's widget extractor
        String json = """
                {
                  "nodes": [
                    {
                      "id": 6,
                      "type": "CLIPTextEncode",
                      "widgets_values": ["A beautiful galaxy, nebula, stars"],
                      "mode": 0
                    },
                    {
                      "id": 10,
                      "type": "Reroute",
                      "inputs": [{ "name": "input", "type": "*", "link": 1 }],
                      "mode": 0
                    },
                    {
                      "id": 3,
                      "type": "KSampler",
                      "inputs": [
                        { "name": "positive", "type": "CONDITIONING", "link": 2 },
                        { "name": "negative", "type": "CONDITIONING", "link": null }
                      ],
                      "widgets_values": [12345678, "randomize", 20, 7.0, "euler", "normal", 1.0],
                      "mode": 0
                    }
                  ],
                  "links": [
                    [1, 6, 0, 10, 0, "*"],
                    [2, 10, 0, 3, 0, "CONDITIONING"]
                  ]
                }
                """;

        JsonNode root = mapper.readTree(json);
        Map<String, String> results = new HashMap<>();
        
        strategy.extract("nodes", root.get("nodes"), root, results);

        assertEquals("A beautiful galaxy, nebula, stars", results.get("Prompt"));
        assertEquals("20", results.get("Steps"));
        assertEquals("7", results.get("CFG"));
        assertEquals("12345678", results.get("Seed"));
    }

    @Test
    @DisplayName("extract should parse API Schema execution graphs")
    void testApiSchema() throws Exception {
        String json = """
                {
                  "3": {
                    "inputs": {
                      "seed": 98765432,
                      "steps": 25,
                      "cfg": 8.0,
                      "sampler_name": "dpmpp_2m",
                      "scheduler": "karras",
                      "positive": ["6", 0],
                      "model": ["4", 0]
                    },
                    "class_type": "KSampler"
                  },
                  "6": {
                    "inputs": { "text": "Cyberpunk city, neon lights" },
                    "class_type": "CLIPTextEncode"
                  }
                }
                """;

        JsonNode root = mapper.readTree(json);
        Map<String, String> results = new HashMap<>();

        strategy.extract("3", root.get("3"), root, results);

        assertEquals("Cyberpunk city, neon lights", results.get("Prompt"));
        assertEquals("25", results.get("Steps"));
        assertEquals("8", results.get("CFG"));
        assertEquals("98765432", results.get("Seed"));
        assertEquals("dpmpp_2m", results.get("Sampler"));
    }

    @Test
    @DisplayName("extract should handle Custom Nodes defined in settings")
    void testCustomNodes() throws Exception {
        // Strategy lowercases node types, so we must provide lowercase needles for the contains check
        when(userDataManager.getCustomPromptNodes()).thenReturn(List.of("mycustomtextnode"));
        
        String json = """
                {
                  "inputs": {
                    "value": "Custom prompt text from user node"
                  },
                  "class_type": "MyCustomTextNode"
                }
                """;

        JsonNode node = mapper.readTree(json);
        Map<String, String> results = new HashMap<>();

        strategy.extract("inputs", node.get("inputs"), node, results);

        assertEquals("Custom prompt text from user node", results.get("Prompt"));
    }

    @Test
    @DisplayName("extract should identify LoRAs from both nodes and prompt tags")
    void testLoraExtraction() throws Exception {
        String json = """
                {
                  "nodes": [
                    {
                      "id": 15,
                      "type": "LoraLoader",
                      "widgets_values": ["epi_noise_offset.safetensors", 0.6, 0.6],
                      "mode": 0
                    },
                    {
                      "id": 6,
                      "type": "CLIPTextEncode",
                      "widgets_values": ["masterpiece, <lora:detail_slider:0.5>"],
                      "mode": 0
                    }
                  ]
                }
                """;

        JsonNode root = mapper.readTree(json);
        Map<String, String> results = new HashMap<>();

        strategy.extract("nodes", root.get("nodes"), root, results);

        assertTrue(results.containsKey("Loras"));
        assertTrue(results.get("Loras").contains("epi_noise_offset"));
        assertTrue(results.get("Loras").contains("detail_slider"));
    }
}
