package com.nilsson.backend.model;

import java.util.List;

/**
 * Data Transfer Object (DTO) representing a collection with its preview images.
 * <p>
 * This record is used to transmit collection summary data to the frontend. It includes
 * the collection's unique name, its type (static vs. smart), and a list of paths for
 * images that should be displayed in the UI's stacked folder preview.
 *
 * @param name
 *         The unique name of the collection.
 * @param isSmart
 *         Whether the collection is dynamic (filter-based) or static.
 * @param previewPaths
 *         A list of up to 4 image paths to be used for the stacked folder preview in the UI.
 */
public record CollectionDTO(String name, boolean isSmart, List<String> previewPaths) {
}
