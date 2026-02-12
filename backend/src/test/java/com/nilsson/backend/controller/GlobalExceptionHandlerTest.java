package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ToolboxException;
import com.nilsson.backend.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandlerTest verifies the centralized error handling logic of the application.
 * It ensures that both custom application exceptions (ToolboxException) and unexpected
 * generic exceptions are correctly caught and transformed into standardized ErrorResponse
 * objects with appropriate HTTP status codes, error codes, and request context information.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
    }

    @Test
    @DisplayName("Should handle ToolboxException and return mapped status and code")
    void handleToolboxException_ShouldReturnMappedResponse() {
        // Using ApplicationException as a concrete implementation of ToolboxException
        String message = "The requested item does not exist";
        ToolboxException ex = new ApplicationException(message);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleToolboxException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("APPLICATION_ERROR", body.code());
        assertEquals(message, body.message());
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.status());
        assertEquals("/api/test", body.path());
        assertNotNull(body.timestamp());
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500 Internal Server Error")
    void handleGenericException_ShouldReturn500() {
        Exception ex = new RuntimeException("Unexpected failure");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_SERVER_ERROR", body.code());
        assertEquals("An unexpected error occurred. Please check the logs.", body.message());
        assertEquals(500, body.status());
        assertEquals("/api/test", body.path());
    }
}