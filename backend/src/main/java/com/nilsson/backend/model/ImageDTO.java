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
 */
public record ImageDTO(String path, int rating, String model) {
}
