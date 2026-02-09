package com.nilsson.backend.service;

import com.nilsson.backend.repository.CollectionRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Facade service managing data persistence, file system interactions, and path normalization.
 * Abstracts the complexity of repositories and ensures consistent data access.
 * Handles path relativization, file hashing, metadata caching, and domain delegation.
 */
@Service
public class UserDataManager {

    private static final Logger logger = LoggerFactory.getLogger(UserDataManager.class);

    private final DatabaseService db;
    private final SettingsRepository settingsRepo;
    private final CollectionRepository collectionRepo;
    private final ImageRepository imageRepo;
    private final MetadataService metaService;

    private final File libraryRoot;

    public UserDataManager(DatabaseService db,
                           SettingsRepository settingsRepo,
                           CollectionRepository collectionRepo,
                           ImageRepository imageRepo,
                           MetadataService metaService) {
        this.db = db;
        this.settingsRepo = settingsRepo;
        this.collectionRepo = collectionRepo;
        this.imageRepo = imageRepo;
        this.metaService = metaService;
        this.libraryRoot = new File(System.getProperty("user.dir")).getAbsoluteFile();
        logger.info("UserDataManager initialized. Library root: {}", libraryRoot);
    }

    public void shutdown() {
        logger.info("Shutting down data services...");
        db.shutdown();
    }

    public File resolvePath(String dbPath) {
        if (dbPath == null) return null;
        File potentiallyAbsolute = new File(dbPath);
        if (potentiallyAbsolute.isAbsolute()) {
            return potentiallyAbsolute;
        }
        String systemPath = dbPath.replace("/", File.separator);
        return new File(libraryRoot, systemPath);
    }

    private String relativizePath(File file) {
        if (file == null) return null;
        try {
            java.nio.file.Path rootPath = libraryRoot.toPath();
            java.nio.file.Path filePath = file.getAbsoluteFile().toPath();
            if (filePath.startsWith(rootPath)) {
                return rootPath.relativize(filePath).toString().replace("\\", "/");
            }
        } catch (Exception e) {
            logger.warn("Failed to relativize path: {}", file, e);
        }
        return file.getAbsolutePath().replace("\\", "/");
    }

    public List<String> getDistinctMetadataValues(String key) {
        List<String> raw = imageRepo.getDistinctValues(key);
        if ("Loras".equals(key)) {
            return raw.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .flatMap(s -> java.util.Arrays.stream(s.split(",")))
                    .map(String::trim)
                    .map(this::cleanLoraName)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
    
    private String cleanLoraName(String raw) {
        if (raw.toLowerCase().startsWith("<lora:")) raw = raw.substring(6);
        if (raw.endsWith(">")) raw = raw.substring(0, raw.length() - 1);
        int lastColon = raw.lastIndexOf(':');
        if (lastColon > 0 && raw.substring(lastColon + 1).matches("[\\d.]+")) {
            raw = raw.substring(0, lastColon);
        }
        return raw.trim();
    }

    public CompletableFuture<List<File>> findFilesWithFilters(String query, Map<String, String> filters, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            List<String> paths = imageRepo.findPaths(query, filters, limit);
            List<File> files = new ArrayList<>();
            for (String path : paths) {
                File f = resolvePath(path);
                if (f != null) files.add(f);
            }
            logger.debug("Search found {} files in {}ms", files.size(), System.currentTimeMillis() - start);
            return files;
        });
    }

    public boolean moveFileToTrash(File file) {
        if (file == null || !file.exists()) return false;

        boolean success = false;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            try {
                success = Desktop.getDesktop().moveToTrash(file);
            } catch (Exception e) {
                logger.error("System trash failed", e);
                return false;
            }
        }

        if (success) {
            imageRepo.deleteByPath(relativizePath(file));
        }
        return success;
    }

    private int getOrCreateImageIdInternal(File file) {
        try {
            String path = relativizePath(file);
            String hash = calculateHash(file);
            
            int id = imageRepo.getIdByPath(path);
            if (id != -1) return id;

            List<String> existingPaths = imageRepo.findPathsByHash(hash);
            if (!existingPaths.isEmpty()) {
                String oldPath = existingPaths.get(0);
                logger.info("Detected file move: {} -> {}", oldPath, path);
                imageRepo.updatePath(oldPath, path);
                return imageRepo.getIdByPath(path);
            }

            return imageRepo.getOrCreateId(path, hash);
        } catch (Exception e) {
            logger.error("Failed to get ID for file: {}", file, e);
            return -1;
        }
    }

    private String calculateHash(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            logger.error("Hash calculation failed: {}", file, e);
            return "hash_error_" + System.currentTimeMillis();
        }
    }

    public void cacheMetadata(File file, Map<String, String> meta) {
        if (file == null || meta == null || meta.isEmpty()) return;
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) imageRepo.saveMetadata(id, meta);
    }

    public Map<String, String> getCachedMetadata(File file) {
        String relPath = relativizePath(file);
        if (imageRepo.hasMetadata(relPath)) {
            return imageRepo.getMetadata(relPath);
        }

        logger.debug("Metadata missing in DB for {}, extracting on-demand.", file.getName());
        Map<String, String> meta = metaService.getExtractedData(file);
        cacheMetadata(file, meta);
        return meta;
    }

    public boolean hasCachedMetadata(File file) {
        return imageRepo.hasMetadata(relativizePath(file));
    }

    public void addTag(File file, String tag) {
        if (file == null || tag == null || tag.isBlank()) return;
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) imageRepo.addTag(id, tag.trim());
    }

    public void removeTag(File file, String tag) {
        if (file == null || tag == null || tag.isBlank()) return;
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) imageRepo.removeTag(id, tag.trim());
    }

    public Set<String> getTags(File file) {
        return imageRepo.getTags(relativizePath(file));
    }

    public int getRating(File file) {
        return imageRepo.getRating(relativizePath(file));
    }

    public void setRating(File file, int rating) {
        if (file == null) return;
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) imageRepo.setRating(id, rating);
    }

    public List<File> getStarredFilesList() {
        List<String> paths = imageRepo.getStarredPaths();
        List<File> files = new ArrayList<>();
        for (String p : paths) {
            File f = resolvePath(p);
            if (f != null && f.exists()) files.add(f);
        }
        return files;
    }

    public List<File> getPinnedFolders() {
        return imageRepo.getPinnedFolders(this::resolvePath);
    }

    public void addPinnedFolder(File folder) {
        if (folder != null && folder.isDirectory()) {
            imageRepo.addPinnedFolder(relativizePath(folder));
        }
    }

    public void removePinnedFolder(File folder) {
        if (folder != null) {
            imageRepo.removePinnedFolder(relativizePath(folder));
        }
    }

    public List<String> getCollections() {
        return collectionRepo.getAllNames();
    }

    public void createCollection(String name) {
        collectionRepo.create(name);
    }

    public void deleteCollection(String name) {
        collectionRepo.delete(name);
    }

    public void addImageToCollection(String collectionName, File file) {
        if (collectionName == null || file == null) return;
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) {
            collectionRepo.addImage(collectionName, id);
        }
    }

    public List<File> getFilesFromCollection(String collectionName) {
        List<String> paths = collectionRepo.getFilePaths(collectionName);
        List<File> files = new ArrayList<>();
        for (String p : paths) {
            File f = resolvePath(p);
            if (f != null && f.exists()) files.add(f);
        }
        return files;
    }

    public String getSetting(String key, String defaultValue) {
        return settingsRepo.get(key, defaultValue);
    }

    public void setSetting(String key, String value) {
        settingsRepo.set(key, value);
    }

    public File getLastFolder() {
        String path = settingsRepo.get("last_folder", null);
        return path != null ? resolvePath(path) : null;
    }

    public void setLastFolder(File folder) {
        if (folder != null) {
            settingsRepo.set("last_folder", relativizePath(folder));
        }
    }
}
