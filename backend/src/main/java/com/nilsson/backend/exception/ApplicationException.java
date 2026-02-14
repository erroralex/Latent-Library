package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * General-purpose exception for unexpected application-level errors.
 * <p>
 * This exception is used when a high-level operation fails due to internal logic errors,
 * database inconsistencies, or failed system calls that are not covered by more specific
 * exception types. It maps to an {@code HTTP 400 Bad Request} status by default.
 */
public class ApplicationException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "APPLICATION_ERROR");
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "APPLICATION_ERROR");
    }
}
