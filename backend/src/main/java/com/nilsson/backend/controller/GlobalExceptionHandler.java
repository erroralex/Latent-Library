package com.nilsson.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.InvalidPathException;
import java.util.Map;

/**
 * Global exception handler for the application.
 * <p>
 * This class provides a centralized mechanism for intercepting and handling exceptions thrown by
 * any controller. It ensures that the API returns consistent, well-formatted error responses
 * to the frontend, improving debuggability and user experience.
 * <p>
 * Handled Exceptions:
 * - {@code InvalidPathException}: Returns a 400 Bad Request when a file system path is malformed.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<Object> handleInvalidPathException(InvalidPathException ex, WebRequest request) {
        Map<String, Object> body = Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", "The provided path is invalid: " + ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}
