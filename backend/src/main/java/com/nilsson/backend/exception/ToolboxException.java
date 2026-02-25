package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Base abstract exception for the Latent Library application.
 * <p>
 * This class serves as the root of the application's custom exception hierarchy. It integrates
 * with Spring's {@code GlobalExceptionHandler} to provide standardized error responses
 * across all API endpoints. Each exception carries an HTTP status code and a unique
 * application-specific error code string.
 */
public abstract class ToolboxException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    protected ToolboxException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ToolboxException(String message, Throwable cause, HttpStatus status, String code) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
