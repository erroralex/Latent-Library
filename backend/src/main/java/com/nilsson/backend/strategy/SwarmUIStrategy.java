package com.nilsson.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Metadata parsing strategy for SwarmUI-generated images.
 * <p>
 * This strategy is designed to extract generation parameters from the structured JSON
 * metadata used by SwarmUI. It identifies core attributes such as the model name,
 * sampler, and prompts, ensuring they are normalized for the application's search
 * and display layers.
 * <p>
 * Key functionalities:
 * - JSON Extraction: Parses the "sui_image_params" or similar JSON blocks.
 * - Model Identification: Extracts the primary model name while ignoring utility JSON structures.
 * - Parameter Normalization: Maps SwarmUI-specific keys (e.g., "cfgscale", "negativeprompt")
 * to standard application fields.
 * - Type Safety: Robustly handles both string and numeric representations of technical values.
 */
@Service
public class SwarmUIStrategy implements MetadataStrategy {

    private static final Logger log = LoggerFactory.getLogger(SwarmUIStrategy.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, String> parse(String text) {
        if (text == null || text.isBlank()) {
            return new HashMap<>();
        }

        Map<String, String> results = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(text);

            JsonNode paramsNode = root.has("sui_image_params") ? root.get("sui_image_params") : root;

            Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                extract(field.getKey(), field.getValue(), paramsNode, results);
            }
        } catch (Exception e) {
            log.error("Failed to parse SwarmUI metadata block", e);
            throw new ApplicationException("System failed to parse SwarmUI generation parameters from JSON block.", e);
        }
        return results;
    }

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        try {
            if (key.equals("loras") && value.isArray()) {
                extractLoras(value, parentNode, results);
                return;
            }

            if (!value.isTextual() && !value.isNumber()) return;
            String text = value.asText();

            if (key.equals("model") && text.length() > 4 && !text.contains("{")) {
                results.put("Model", text);
            } else if (key.equals("sampler")) {
                results.put("Sampler", text);
            } else if (key.equals("scheduler")) {
                results.put("Scheduler", text);
            } else if (key.equals("prompt") && text.length() > 5) {
                if (!results.containsKey("Prompt")) {
                    results.put("Prompt", text);
                }
            } else if (key.equals("negativeprompt")) {
                results.put("Negative", text);
            } else if (key.equals("cfgscale")) {
                results.put("CFG", text);
            } else if (key.equals("steps")) {
                results.put("Steps", text);
            } else if (key.equals("seed")) {
                results.put("Seed", text);
            }
        } catch (Exception e) {
            log.error("Error during SwarmUI JSON extraction for key: {}", key, e);
            // Non-throwing catch to allow processing of other keys in the JSON tree
        }
    }

    private void extractLoras(JsonNode lorasNode, JsonNode parentNode, Map<String, String> results) {
        if (lorasNode.isEmpty()) return;

        JsonNode weightsNode = parentNode.get("loraweights");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lorasNode.size(); i++) {
            String name = lorasNode.get(i).asText();
            String weight = "1";
            if (weightsNode != null && weightsNode.isArray() && weightsNode.size() > i) {
                weight = weightsNode.get(i).asText();
            }

            if (!sb.isEmpty()) sb.append(", ");
            sb.append("<lora:").append(name).append(":").append(weight).append(">");
        }

        if (!sb.isEmpty()) {
            results.put("Loras", sb.toString());
        }
    }
}
