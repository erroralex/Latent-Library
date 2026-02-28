package com.nilsson.backend.model;

/**
 * Data Transfer Object (DTO) representing a summary of an image's state and metadata.
 * <p>
 * This record is primarily used to optimize frontend performance by bundling essential image information
 * into a single network response. It eliminates the "N+1" problem where the frontend would otherwise
 * need to fetch metadata individually for every image in a gallery or search result.
 *
 * @param path
 *         The normalized absolute file system path to the image.
 * @param rating
 *         The user-assigned star rating (0-5).
 * @param model
 *         The name of the AI model used to generate the image, if available in cached metadata.
 * @param userNotes
 *         User-defined notes associated with the image.
 * @param customPrompt
 *         User-defined override for the generation prompt.
 * @param customNegativePrompt
 *         User-defined override for the negative generation prompt.
 * @param customModel
 *         User-defined override for the model name.
 */
public record ImageDTO(String path, int rating, String model, String userNotes, String customPrompt, String customNegativePrompt, String customModel) {
    /**
     * Minimal constructor for backward compatibility and basic image listings.
     */
    public ImageDTO(String path, int rating, String model) {
        this(path, rating, model, null, null, null, null);
    }
}
