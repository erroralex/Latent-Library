package com.nilsson.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Metadata extraction strategy for InvokeAI-generated images.
 * <p>
 * This strategy parses the structured JSON metadata block used by InvokeAI. It maps
 * InvokeAI-specific keys (e.g., "model_name", "positive_prompt", "cfg_scale") to the
 * application's standard metadata schema, ensuring consistency in the UI and search index.
 * <p>
 * Key functionalities:
 * - JSON Schema Mapping: Translates InvokeAI's internal property names to standard fields.
 * - Model Resolution: Extracts model names from multiple potential keys (name, weights, variant).
 * - Prompt Extraction: Identifies both positive and negative prompts from the JSON structure.
 * - Parameter Normalization: Captures CFG scale and sampler/scheduler information.
 */
@Service
public class InvokeAIStrategy implements MetadataStrategy {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, String> parse(String text) {
        Map<String, String> results = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(text);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                extract(field.getKey(), field.getValue(), root, results);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        if (!value.isTextual()) return;
        String text = value.asText();

        if (key.equals("model_name") || key.equals("model_weights")) {
            results.put("Model", text);
        } else if (key.equals("positive_prompt") || (key.equals("prompt") && !results.containsKey("Prompt"))) {
            results.put("Prompt", text);
        } else if (key.equals("negative_prompt")) {
            results.put("Negative", text);
        } else if (key.equals("cfg_scale") || key.equals("cfg_rescale_multiplier")) {
            results.put("CFG", text);
        } else if ((key.equals("sampler_name") || key.equals("scheduler")) && !results.containsKey("Sampler")) {
            results.put("Sampler", text);
        } else if (key.equals("variant") && !results.containsKey("Model")) {
            results.put("Model", text);
        }
    }
}
