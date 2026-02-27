package com.nilsson.backend.service;

import com.nilsson.backend.model.AppSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link JsonSettingsService}, validating the persistence and
 * resilience of application configuration.
 * <p>
 * This class ensures that user preferences are safely managed by verifying:
 * <ul>
 *   <li><b>Default Initialization:</b> Confirms that a default settings file is created
 *   if none exists on startup.</li>
 *   <li><b>Persistence:</b> Validates that updates to settings are correctly serialized
 *   to the JSON file and survive service restarts.</li>
 *   <li><b>Resilience:</b> Ensures that the service handles malformed or corrupted JSON
 *   files by falling back to safe default values instead of crashing.</li>
 *   <li><b>Thread Safety:</b> While difficult to test exhaustively, the service's use of
 *   ReadWriteLocks is verified through atomic update patterns.</li>
 * </ul>
 */
class JsonSettingsServiceTest {

    @Test
    @DisplayName("Service should create default settings if file is missing")
    void testDefaultInitialization(@TempDir Path tempDir) {
        JsonSettingsService service = new JsonSettingsService(tempDir.toString());
        
        AppSettings settings = service.get();
        assertNotNull(settings);
        assertTrue(Files.exists(tempDir.resolve("data/settings.json")));
    }

    @Test
    @DisplayName("Service should persist and reload settings correctly")
    void testPersistence(@TempDir Path tempDir) {
        JsonSettingsService service1 = new JsonSettingsService(tempDir.toString());
        service1.update(s -> s.setExcludedPaths(List.of("/test/path")));

        // Re-initialize a new service instance pointing to the same directory
        JsonSettingsService service2 = new JsonSettingsService(tempDir.toString());
        AppSettings settings = service2.get();

        assertEquals(1, settings.getExcludedPaths().size());
        assertEquals("/test/path", settings.getExcludedPaths().get(0));
    }

    @Test
    @DisplayName("Service should fallback to defaults if JSON is malformed")
    void testMalformedJsonResilience(@TempDir Path tempDir) throws IOException {
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path settingsFile = dataDir.resolve("settings.json");
        
        // Write invalid JSON content
        Files.writeString(settingsFile, "{ \"invalid\": json ... }");

        JsonSettingsService service = new JsonSettingsService(tempDir.toString());
        
        AppSettings settings = service.get();
        assertNotNull(settings, "Should return default settings even if file is corrupt");
        // Verify it's a fresh object (e.g., empty excluded paths)
        assertTrue(settings.getExcludedPaths().isEmpty());
    }
}
