package com.nilsson.backend.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 <h2>InvokeAIStrategy</h2>
 <p>
 This class implements a metadata extraction strategy specifically for <b>InvokeAI</b>.
 Unlike the text-based parameters used by other UIs, InvokeAI typically embeds
 generation data in a structured JSON format within the image metadata chunks.
 </p>

 <h3>Key Extraction Features:</h3>
 <ul>
 <li><b>Model Identification:</b> Dynamically maps {@code model_name}, {@code model_weights},
 or {@code variant} fields to a unified "Model" attribute.</li>
 <li><b>Prompt Resolution:</b> Distinguishes between {@code positive_prompt} and
 {@code negative_prompt}, with fallback logic for generic {@code prompt} keys.</li>
 <li><b>Parameter Normalization:</b> Maps software-specific keys like {@code cfg_scale}
 and {@code sampler_name} to the standard application keys (CFG, Sampler, etc.).</li>
 <li><b>Collision Avoidance:</b> Implements conditional checks (e.g., {@code !containsKey})
 to ensure that higher-fidelity primary fields are not overwritten by secondary fallbacks.</li>
 </ul>
 */
@Service
public class InvokeAIStrategy implements MetadataStrategy {

    // ------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------

    private static final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     Parses a raw JSON string representing InvokeAI metadata.
     * @param text The raw JSON metadata string.

     @return A map containing normalized generation parameters.
     */
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

    // ------------------------------------------------------------------------
    // MetadataStrategy Implementation
    // ------------------------------------------------------------------------

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        if (!value.isTextual()) return;
        String text = value.asText();

        // Model Extraction
        if (key.equals("model_name") || key.equals("model_weights")) {
            results.put("Model", text);
        }

        // Prompt Extraction
        else if (key.equals("positive_prompt") || (key.equals("prompt") && !results.containsKey("Prompt"))) {
            results.put("Prompt", text);
        } else if (key.equals("negative_prompt")) {
            results.put("Negative", text);
        }

        // Generation Parameters
        else if (key.equals("cfg_scale") || key.equals("cfg_rescale_multiplier")) {
            results.put("CFG", text);
        } else if ((key.equals("sampler_name") || key.equals("scheduler")) && !results.containsKey("Sampler")) {
            results.put("Sampler", text);
        }

        // Fallback Logic
        else if (key.equals("variant") && !results.containsKey("Model")) {
            results.put("Model", text);
        }
    }
}