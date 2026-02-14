package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when a requested resource (file, folder, collection, or image) cannot be found.
 * <p>
 * This exception maps to an {@code HTTP 404 Not Found} status. It is used when a database
 * lookup returns no results or when a physical file path resolved via {@code PathService}
 * does not exist on the disk.
 */
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
