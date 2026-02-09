package com.nilsson.backend.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Metadata parsing strategy for NovelAI-generated JSON.
 *
 * <p>This strategy extracts prompts, generation parameters, and basic
 * model identification from NovelAI metadata, handling both textual
 * and numeric configuration values.</p>
 *
 * <p>The implementation avoids overwriting higher-priority data that
 * may already have been resolved by other strategies.</p>
 */
@Service
public class NovelAIStrategy implements MetadataStrategy {

    private static final ObjectMapper mapper = new ObjectMapper();

    /* ============================================================
       Public API
       ============================================================ */

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

    /* ============================================================
       MetadataStrategy Implementation
       ============================================================ */

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        if (!value.isTextual() && !value.isNumber()) return;

        String text = value.asText().trim();
        if (text.isEmpty()) return;

        if (key.equals("prompt")) {
            if (!results.containsKey("Prompt")) {
                results.put("Prompt", text);
            }
        }
        else if (key.equals("uc")) {
            results.put("Negative", text);
        }

        else if (key.equals("scale")) {
            results.put("CFG", text);
        }
        else if (key.equals("steps")) {
            results.put("Steps", text);
        }
        else if (key.equals("seed")) {
            results.put("Seed", text);
        }
        else if (key.equals("sampler")) {
            results.put("Sampler", text);
        }

        else if (key.equals("software") && text.equalsIgnoreCase("novelai")) {
            if (!results.containsKey("Model")) {
                results.put("Model", "NovelAI Diffusion");
            }
        }
    }
}