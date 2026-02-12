package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ValidationException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}