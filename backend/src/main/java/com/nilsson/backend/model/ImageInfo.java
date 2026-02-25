package com.nilsson.backend.model;

/**
 * A lightweight record for holding essential image information retrieved in bulk.
 *
 * @param path The normalized, absolute path of the image file.
 * @param rating The user-assigned rating (0-5).
 * @param model The name of the AI model used to generate the image, if available.
 */
public record ImageInfo(String path, int rating, String model) {
}
