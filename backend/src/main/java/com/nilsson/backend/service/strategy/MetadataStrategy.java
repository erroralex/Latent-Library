package com.nilsson.backend.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Strategy interface for extracting metadata from different AI generation tool outputs.
 * Implementations define how to parse specific JSON structures or raw text formats.
 */
public interface MetadataStrategy {
    /**
     * Inspects a single JSON node and extracts relevant metadata.
     * @param key The JSON key (e.g., "steps", "model_name")
     * @param value The JSON value associated with the key
     * @param parentNode The parent object (useful for context like looking up 'scheduler' sibling)
     * @param results The map to populate with extracted data
     */
    void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results);

    /**
     * Parses a raw metadata string and returns a map of key-value pairs.
     * This is a default method to support strategies that can parse raw strings directly.
     *
     * @param rawMetadata The raw metadata string.
     * @return A map of extracted metadata.
     */
    default Map<String, String> parse(String rawMetadata) {
        return Map.of();
    }
}
