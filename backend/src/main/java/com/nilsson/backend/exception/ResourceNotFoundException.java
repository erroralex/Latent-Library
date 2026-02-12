package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ResourceNotFoundException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s not found with id: %s", resource, id), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}