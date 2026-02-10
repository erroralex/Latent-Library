package com.nilsson.backend.model;

import java.util.List;

/**
 * Represents a specific metadata filter criterion used in complex search operations.
 * <p>
 * This record encapsulates a key-value pair structure where a single metadata attribute (the key)
 * can be matched against multiple potential values. It is a core component of the "Smart Collection"
 * logic, allowing for flexible and extensible search queries against the SQLite FTS5 index.
 *
 * @param key
 *         The metadata attribute to filter by (e.g., "Model", "Sampler", "Loras").
 * @param values
 *         A list of acceptable values for the specified key. The search logic typically
 *         treats these as an "OR" relationship within the specific key.
 */
public record ImageSearchFilter(String key, List<String> values) {
}
