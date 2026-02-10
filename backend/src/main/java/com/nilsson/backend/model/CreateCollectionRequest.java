package com.nilsson.backend.model;

import java.util.List;

/**
 * Data carrier for creating or updating an image collection.
 * <p>
 * This record supports both static collections (manual groupings) and "Smart Collections"
 * (dynamic groupings based on metadata filters). When {@code isSmart} is true, the {@code filters}
 * object defines the criteria used to automatically populate the collection.
 *
 * @param name
 *         The unique name of the collection.
 * @param isSmart
 *         Indicates if the collection is dynamic (true) or static (false).
 * @param filters
 *         The criteria for dynamic population, ignored if {@code isSmart} is false.
 */
public record CreateCollectionRequest(
        String name,
        boolean isSmart,
        CollectionFilters filters
) {
    /**
     * Defines the specific metadata attributes used to filter images for a Smart Collection.
     *
     * @param models
     *         List of AI model names to include.
     * @param loras
     *         List of LoRA names to include.
     * @param samplers
     *         List of sampler algorithms to include.
     * @param rating
     *         The specific star rating to match.
     * @param prompt
     *         List of keywords or phrases to search for within the positive prompt.
     */
    public record CollectionFilters(
            List<String> models,
            List<String> loras,
            List<String> samplers,
            String rating,
            List<String> prompt
    ) {
    }
}
