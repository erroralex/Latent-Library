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
 * This service replaces the database-backed settings for user preferences, ensuring that
 * configuration (excluded paths, last folder, tool settings) is portable and survives
 * database clears. It stores data in 'data/settings.json'.
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
            // Create default settings
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
            // Fallback to defaults if file is corrupted
            this.currentSettings = new AppSettings();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() {
        lock.writeLock().lock();
        try {
            // Ensure parent dir exists
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
    
    // Helper to modify settings safely
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
