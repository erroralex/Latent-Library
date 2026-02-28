package com.nilsson.backend.model;

/**
 * Data Transfer Object for updating user-defined metadata fields on an image.
 * <p>
 * This record facilitates the manual override or supplementation of technical metadata
 * extracted from AI-generated images. It allows users to persist personal notes and
 * custom prompt/model information that may not be present or accurate in the original
 * file metadata.
 *
 * @param userNotes
 *         Free-form text notes added by the user for organization or reference.
 * @param customPrompt
 *         A manual override or supplement to the extracted positive generation prompt.
 * @param customNegativePrompt
 *         A manual override or supplement to the extracted negative generation prompt.
 * @param customModel
 *         A manual override or supplement to the extracted model name.
 */
public record UpdateMetadataRequest(String userNotes, String customPrompt, String customNegativePrompt, String customModel) {
}
