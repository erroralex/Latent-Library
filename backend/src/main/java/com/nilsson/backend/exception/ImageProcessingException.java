package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when a failure occurs during image manipulation or metadata extraction.
 * <p>
 * This exception maps to an {@code HTTP 500 Internal Server Error} status. It is used to
 * wrap low-level I/O or AWT/ImageIO errors that occur during thumbnail generation,
 * metadata scrubbing, or AI tagging.
 */
public class ImageProcessingException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ImageProcessingException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PROCESSING_ERROR");
    }

    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PROCESSING_ERROR");
    }
}
