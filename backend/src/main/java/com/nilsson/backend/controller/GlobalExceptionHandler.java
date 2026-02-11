package com.nilsson.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.InvalidPathException;
import java.util.Map;

/**
 * Global exception handler for the application, providing centralized error management across all controllers.
 * <p>
 * This class utilizes Spring's {@code @ControllerAdvice} to intercept exceptions thrown during request
 * processing. It ensures that the API returns consistent, well-formatted JSON error responses to the
 * frontend, which improves both the user experience and the ease of debugging.
 * <p>
 * Handled Exceptions:
 * <ul>
 *   <li>{@link InvalidPathException}: Intercepted when a malformed or illegal file system path is
 *   provided by the client. Returns a {@code 400 Bad Request} with a descriptive message.</li>
 * </ul>
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
