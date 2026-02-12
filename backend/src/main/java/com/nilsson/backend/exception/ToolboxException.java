package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;
import java.io.Serial;

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

    // Add this constructor to support the 'cause' (Throwable)
    protected ToolboxException(String message, Throwable cause, HttpStatus status, String code) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}