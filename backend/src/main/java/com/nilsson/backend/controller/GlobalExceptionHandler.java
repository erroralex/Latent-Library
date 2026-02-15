package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ToolboxException;
import com.nilsson.backend.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

/**
 * Global exception handler for the application, providing centralized error mapping and response formatting.
 * <p>
 * This class intercepts exceptions thrown by controllers and transforms them into standardized
 * {@link ErrorResponse} objects. it handles specific application exceptions ({@link ToolboxException})
 * with custom status codes and messages, while also providing a fallback for generic system errors.
 * It includes specialized logic to silently ignore common network-related exceptions that occur
 * during high-frequency client interactions, such as rapid scrolling through image lists.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ToolboxException.class)
    public ResponseEntity<ErrorResponse> handleToolboxException(ToolboxException ex, HttpServletRequest request) {
        log.warn("Application Error: {} - {}", ex.getCode(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getCode(),
                ex.getMessage(),
                ex.getStatus().value(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, HttpServletRequest request) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";

        if (msg.contains("An established connection was aborted") || msg.contains("Broken pipe")) {
            return null;
        }

        log.error("Unexpected System Error", ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please check the logs.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now(),
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
