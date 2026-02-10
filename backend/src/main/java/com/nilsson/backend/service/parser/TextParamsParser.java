package com.nilsson.backend.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.service.strategy.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Orchestration service for parsing text-based image generation metadata.
 * <p>
 * This class acts as a high-level router that identifies the specific format of a metadata string
 * and delegates the parsing to the appropriate {@code MetadataStrategy}. It supports a wide
 * range of AI generation tools by detecting unique signatures in both structured JSON
 * (ComfyUI, InvokeAI, SwarmUI) and unstructured text blocks (Automatic1111, NovelAI).
 * <p>
 * Key functionalities:
 * - Format Detection: Analyzes raw text to determine the originating software (e.g., ComfyUI vs. A1111).
 * - Strategy Routing: Dispatches metadata to specialized strategy implementations for deep parsing.
 * - ComfyUI Graph Traversal: Implements specialized logic to handle both UI-schema and API-schema
 * JSON structures used by ComfyUI.
 * - Fallback Logic: Provides a safe, empty result if no recognizable metadata format is found.
 */
@Service
public class TextParamsParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, String> parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashMap<>();
        }

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
