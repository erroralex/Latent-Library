package com.nilsson.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ImageProcessingException extends ToolboxException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ImageProcessingException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PROCESSING_ERROR");
    }

    public ImageProcessingException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_PROCESSING_ERROR");
        this.initCause(cause);
    }
}