package com.nilsson.backend.model;

import java.util.List;

public record CreateCollectionRequest(
        String name,
        boolean isSmart,
        CollectionFilters filters
) {
    public record CollectionFilters(
            List<String> models,
            List<String> loras,
            List<String> samplers,
            Integer rating,
            List<String> prompt
    ) {}
}
