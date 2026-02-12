package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;
import java.io.Serial;

public class ApplicationException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "APPLICATION_ERROR");
    }

    // Add this constructor to fix DatabaseService and FtsService errors
    public ApplicationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "APPLICATION_ERROR");
    }
}