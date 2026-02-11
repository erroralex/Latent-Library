package com.nilsson.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Strategy interface for extracting and normalizing metadata from diverse AI generation tools.
 * <p>
 * This interface defines the contract for tool-specific parsing logic. Implementations are
 * responsible for identifying and extracting technical parameters (e.g., prompts, seeds,
 * models, samplers) from either structured JSON nodes or raw text blocks. This strategy
 * pattern allows the application to support a wide variety of software (Automatic1111,
 * ComfyUI, InvokeAI, etc.) while maintaining a clean, extensible architecture.
 */
public interface MetadataStrategy {
    /**
     * Inspects a single JSON node and extracts relevant metadata into the results map.
     *
     * @param key
     *         The JSON key currently being inspected.
     * @param value
     *         The JSON value associated with the key.
     * @param parentNode
     *         The parent object, providing context for sibling-dependent parameters.
     * @param results
     *         The map to be populated with normalized metadata key-value pairs.
     */
    void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results);

    /**
     * Parses a raw metadata string directly into a map of normalized key-value pairs.
     *
     * @param rawMetadata
     *         The raw, unparsed metadata string extracted from the image.
     *
     * @return A map containing the extracted and normalized metadata.
     */
    default Map<String, String> parse(String rawMetadata) {
        return Map.of();
    }
}
