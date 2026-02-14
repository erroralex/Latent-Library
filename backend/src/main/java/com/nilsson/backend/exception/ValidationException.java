package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when user input or request parameters fail business validation rules.
 * <p>
 * This exception maps to an {@code HTTP 400 Bad Request} status. It is typically used for
 * malformed paths, empty required fields, or invalid configuration parameters.
 */
public class ValidationException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
