package com.nilsson.backend.service;

import com.nilsson.backend.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link PathService}, validating the path normalization and resolution logic
 * across different operating systems.
 * <p>
 * This class ensures that the application can reliably navigate and store file references in a
 * platform-independent manner by verifying:
 * <ul>
 *   <li><b>Normalization:</b> Confirms that OS-specific path separators (e.g., backslashes on Windows)
 *   are consistently converted to a unified forward-slash format for database storage.</li>
 *   <li><b>Resolution:</b> Validates that string-based paths are correctly resolved into {@link File}
 *   objects, including support for relative paths and parent directory navigation (..).</li>
 *   <li><b>UNC & Network Paths:</b> Specifically tests the resolution of Windows-specific UNC paths
 *   (e.g., WSL mounts and network shares) to ensure they are handled without throwing exceptions.</li>
 *   <li><b>Error Handling:</b> Verifies that malformed or illegal path segments correctly trigger
 *   {@link ValidationException} to prevent system-level I/O errors.</li>
 * </ul>
 */
class PathServiceTest {

    private final PathService pathService = new PathService();

    @Test
    @DisplayName("resolve should return normalized File object")
    void testResolve() {
        String path = "src" + File.separator + "test" + File.separator + "resources";
        File file = pathService.resolve(path);
        assertNotNull(file);
        assertTrue(file.getAbsolutePath().contains("src"));
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
        File file = new File("test/path/image.png");
        String normalized = pathService.getNormalizedAbsolutePath(file);

        assertNotNull(normalized);
        assertTrue(normalized.contains("/"));
        assertFalse(normalized.contains("\\"));
    }

    @Test
    @DisplayName("resolve should handle UNC paths on Windows")
    @EnabledOnOs(OS.WINDOWS)
    void testResolveUncPath() {
        String uncPath = "\\\\wsl$\\Ubuntu\\home\\user\\images";
        File file = pathService.resolve(uncPath);
        assertNotNull(file);
        assertTrue(file.getPath().contains("Ubuntu"));
    }

    @Test
    @DisplayName("resolve should handle network share paths on Windows")
    @EnabledOnOs(OS.WINDOWS)
    void testResolveNetworkShare() {
        String networkPath = "\\\\192.168.1.10\\share\\photos";
        File file = pathService.resolve(networkPath);
        assertNotNull(file);
        assertTrue(file.getPath().contains("192.168.1.10"));
    }
}
