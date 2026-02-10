package com.nilsson.backend.model;

import java.util.List;

/**
 * Represents a set of filters for a complex image search.
 * Used for Smart Collections.
 *
 * @param key The metadata key to filter on (e.g., "Model", "Sampler", "Loras").
 * @param values A list of values to match for the given key.
 */
public record ImageSearchFilter(String key, List<String> values) {
}
