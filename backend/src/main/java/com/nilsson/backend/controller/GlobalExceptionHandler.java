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
 * This class intercepts exceptions thrown by any controller and transforms them into a standardized
 * {@link ErrorResponse} format. It handles both custom application exceptions (via the
 * {@link ToolboxException} hierarchy) and unexpected generic exceptions, ensuring that the
 * frontend receives consistent and actionable error information.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Standardized Error Responses:</b> Ensures all API errors follow a consistent JSON
 *   structure including error codes, messages, and timestamps.</li>
 *   <li><b>Exception Mapping:</b> Maps specific exception types to appropriate HTTP status
 *   codes (e.g., 404 for ResourceNotFound, 400 for Validation).</li>
 *   <li><b>Logging:</b> Provides centralized logging for all application errors, distinguishing
 *   between expected validation warnings and critical system failures.</li>
 *   <li><b>Security:</b> Prevents internal implementation details from leaking to the client
 *   by sanitizing generic exception messages.</li>
 * </ul>
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
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
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
