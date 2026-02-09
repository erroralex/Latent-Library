package com.nilsson.backend.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Metadata parsing strategy for SwarmUI-generated JSON.
 * Extracts model information, prompts, and generation parameters.
 * Supports both textual and numeric value formats.
 */
@Service
public class SwarmUIStrategy implements MetadataStrategy {

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
        if (!value.isTextual() && !value.isNumber()) return;
        String text = value.asText();

        if (key.equals("model") && text.length() > 4 && !text.contains("{")) {
            results.put("Model", text);
        }
        else if (key.equals("sampler")) {
            results.put("Sampler", text);
        }

        else if (key.equals("prompt") && text.length() > 5) {
            if (!results.containsKey("Prompt")) {
                results.put("Prompt", text);
            }
        }
        else if (key.equals("negativeprompt")) {
            results.put("Negative", text);
        }

        else if (key.equals("cfgscale")) {
            results.put("CFG", text);
        }
        else if (key.equals("steps")) {
            results.put("Steps", text);
        }
        else if (key.equals("seed")) {
            results.put("Seed", text);
        }
    }
}
