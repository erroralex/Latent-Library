package com.nilsson.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for managing application settings persisted in a JSON file.
 * <p>
 * This service manages user preferences and application configuration (e.g., excluded paths,
 * last visited folder) by persisting them in a portable JSON file. This ensures that
 * configuration survives database resets and is easily accessible for manual editing.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>JSON Persistence:</b> Handles the serialization and deserialization of
 *   {@link AppSettings} to and from {@code data/settings.json}.</li>
 *   <li><b>Thread-Safe Access:</b> Utilizes a {@link ReentrantReadWriteLock} to ensure
 *   consistent state when settings are concurrently read or modified.</li>
 *   <li><b>Default Initialization:</b> Automatically creates a default settings file if
 *   none exists upon service startup.</li>
 *   <li><b>Atomic Updates:</b> Provides a safe update mechanism that ensures changes are
 *   committed to disk immediately after modification.</li>
 * </ul>
 */
@Service
public class JsonSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(JsonSettingsService.class);
    private static final String SETTINGS_FILE = "data/settings.json";

    private final ObjectMapper mapper;
    private final Path settingsPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private AppSettings currentSettings;

    public JsonSettingsService(@Value("${app.data.dir:.}") String appDataDir) {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.settingsPath = Paths.get(appDataDir).resolve(SETTINGS_FILE).toAbsolutePath().normalize();
        initialize();
    }

    private void initialize() {
        File file = settingsPath.toFile();
        if (!file.exists()) {
            this.currentSettings = new AppSettings();
            save();
        } else {
            load();
        }
    }

    private void load() {
        lock.writeLock().lock();
        try {
            this.currentSettings = mapper.readValue(settingsPath.toFile(), AppSettings.class);
        } catch (IOException e) {
            logger.error("Failed to load settings.json", e);
            this.currentSettings = new AppSettings();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() {
        lock.writeLock().lock();
        try {
            File parent = settingsPath.getParent().toFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            mapper.writeValue(settingsPath.toFile(), currentSettings);
        } catch (IOException e) {
            logger.error("Failed to save settings.json", e);
            throw new ApplicationException("Could not save application settings.", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AppSettings get() {
        lock.readLock().lock();
        try {
            return currentSettings;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void update(java.util.function.Consumer<AppSettings> updater) {
        lock.writeLock().lock();
        try {
            updater.accept(currentSettings);
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
