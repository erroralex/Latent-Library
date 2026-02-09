package com.nilsson.backend.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.service.strategy.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 <h2>TextParamsParser</h2>
 <p>
 This class serves as a high-level orchestration layer for parsing text-based image generation
 metadata into structured key-value pairs. It acts as a central router that identifies the
 originating software or format and delegates processing to specific strategy implementations.
 </p>

 <h3>Parsing Logic:</h3>
 <ul>
 <li><b>JSON-Based Routing:</b> Detects ComfyUI workflows (both Web UI and API versions)
 by checking for JSON structures and specific node identifiers.</li>
 <li><b>Signature Detection:</b> Identifies string-based metadata blocks for popular
 generators like Automatic1111 (Common), InvokeAI, NovelAI, and SwarmUI.</li>
 <li><b>Strategy Pattern:</b> Utilizes a modular approach to extraction, allowing the
 parser to remain extensible as new AI generation tools emerge.</li>
 </ul>

 <h3>Supported Formats:</h3>
 <p>
 ComfyUI, Automatic1111, InvokeAI, NovelAI, and SwarmUI.
 </p>
 */
@Service
public class TextParamsParser {

    // ------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------

    private static final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     Entry point for parsing metadata strings.
     * @param text The raw metadata string extracted from an image header or sidecar file.

     @return A {@link Map} containing the structured keys (e.g., "Seed", "Steps") and their values.
     */
    public static Map<String, String> parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashMap<>();
        }

        // Handle JSON-based formats (ComfyUI)
        if (text.trim().startsWith("{")) {
            try {
                JsonNode root = mapper.readTree(text);
                Map<String, String> results = new HashMap<>();
                ComfyUIStrategy strategy = new ComfyUIStrategy();

                if (root.has("nodes")) {
                    for (JsonNode node : root.get("nodes")) {
                        processComfyNode(node, strategy, results);
                    }
                    strategy.extract("nodes_wrapper", root, null, results);
                } else {
                    boolean isApi = false;
                    Iterator<JsonNode> it = root.elements();
                    while (it.hasNext()) {
                        if (it.next().has("class_type")) {
                            isApi = true;
                            break;
                        }
                    }

                    if (isApi) {
                        strategy.extract("api_nodes", root, null, results);
                    } else {
                        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            JsonNode node = entry.getValue();
                            if (node.has("inputs") && node.has("class_type")) {
                                processComfyNode(node, strategy, results);
                            }
                        }
                    }
                }

                if (!results.isEmpty()) return results;

            } catch (Exception ignored) {
            }
        }

        // Identify and delegate to specific string-based strategies
        if (text.contains("Steps: ") && text.contains("Sampler: ")) {
            return new CommonStrategy().parse(text);
        }

        if (text.contains("\"app_version\":") && text.contains("invokeai")) {
            return new InvokeAIStrategy().parse(text);
        }

        if (text.contains("NovelAI")) {
            return new NovelAIStrategy().parse(text);
        }

        if (text.contains("sui_image_params")) {
            return new SwarmUIStrategy().parse(text);
        }

        return new HashMap<>();
    }

    // ------------------------------------------------------------------------
    // Internal Helpers
    // ------------------------------------------------------------------------

    private static void processComfyNode(
            JsonNode node,
            ComfyUIStrategy strategy,
            Map<String, String> results
    ) {
        Iterator<Map.Entry<String, JsonNode>> nodeFields = node.fields();
        while (nodeFields.hasNext()) {
            Map.Entry<String, JsonNode> field = nodeFields.next();
            strategy.extract(field.getKey(), field.getValue(), node, results);
        }
    }
}