package com.nilsson.backend.service;

import com.nilsson.backend.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathServiceTest is responsible for validating the path normalization and resolution logic within the application.
 * It ensures that file system paths are consistently formatted across different operating systems,
 * specifically verifying the conversion of OS-specific separators to a unified forward-slash format.
 * The tests also confirm that the service correctly resolves string-based paths into File objects
 * and handles malformed or illegal path segments by throwing appropriate validation exceptions.
 * This ensures that the application can reliably navigate and store file references in a
 * platform-independent manner.
 */
class PathServiceTest {

    private final PathService pathService = new PathService();

    @Test
    @DisplayName("resolve should return normalized File object")
    void testResolve() {
        File file = pathService.resolve("C:\\test\\path\\..\\file.txt");
        assertNotNull(file);
        assertTrue(file.getAbsolutePath().contains("test"));
        assertFalse(file.getAbsolutePath().contains("path"));
    }

    @Test
    @DisplayName("resolve should throw exception for null or empty path")
    void testResolveInvalid() {
        assertThrows(ValidationException.class, () -> pathService.resolve(null));
        assertThrows(ValidationException.class, () -> pathService.resolve("  "));
    }

    @Test
    @DisplayName("getNormalizedAbsolutePath should return forward-slash path")
    void testNormalization() {
        File file = new File("C:\\Users\\Test\\Image.png");
        String normalized = pathService.getNormalizedAbsolutePath(file);

        assertNotNull(normalized);
        assertTrue(normalized.contains("/"));
        assertFalse(normalized.contains("\\"));
    }
}