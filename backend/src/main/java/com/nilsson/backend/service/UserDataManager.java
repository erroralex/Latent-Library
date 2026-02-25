package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.model.ImageInfo;
import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.PinnedFolderRepository;
import com.nilsson.backend.repository.SearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * High-level facade service for managing user data, application state, and complex business logic orchestration.
 * <p>
 * This service acts as the primary orchestrator for the application's data layer, aggregating functionality
 * from multiple specialized services and repositories to provide a unified API for the controller layer.
 * It handles complex operations such as multi-filter searching, file movement detection via hashing,
 * and metadata value normalization for UI display.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Unified Data Access:</b> Provides a single entry point for interacting with images,
 *   collections, tags, and application settings.</li>
 *   <li><b>Advanced Search Orchestration:</b> Combines SQLite FTS5 search with relational filtering
 *   and DTO mapping to deliver responsive search results.</li>
 *   <li><b>Bulk Data Retrieval:</b> Offers an optimized method to fetch DTOs for a large list of files
 *   by performing a single bulk database query.</li>
 *   <li><b>File Integrity & Tracking:</b> Implements a fast fingerprinting mechanism to detect when files have been
 *   moved or renamed, maintaining database consistency without re-indexing.</li>
 *   <li><b>Metadata Normalization:</b> Cleans and formats raw metadata values (e.g., LoRAs, Samplers)
 *   to ensure a consistent and user-friendly experience in the frontend.</li>
 *   <li><b>System Integration:</b> Manages OS-level operations such as moving files to the system trash
 *   and resolving platform-specific file paths.</li>
 *   <li><b>Application State:</b> Persists and retrieves user preferences, such as the last visited
 *   folder and excluded directory paths.</li>
 * </ul>
 */
@Service
public class UserDataManager {

    private static final Logger log = LoggerFactory.getLogger(UserDataManager.class);

    private final int hashChunkSize;

    private final DatabaseService db;
    private final JsonSettingsService settingsService;
    private final ImageRepository imageRepo;
    private final ImageMetadataRepository imageMetadataRepository;
    private final PinnedFolderRepository pinnedFolderRepository;
    private final CollectionService collectionService;
    private final ImageMetadataService imageMetadataService;
    private final TagService tagService;
    private final PathService pathService;
    private final SearchRepository searchRepository;
    private final FileSystemService fileSystemService;
    private final DHashService dHashService;

    public UserDataManager(DatabaseService db,
                           JsonSettingsService settingsService,
                           ImageRepository imageRepo,
                           ImageMetadataRepository imageMetadataRepository, PinnedFolderRepository pinnedFolderRepository,
                           CollectionService collectionService,
                           ImageMetadataService imageMetadataService,
                           TagService tagService,
                           PathService pathService,
                           SearchRepository searchRepository,
                           FileSystemService fileSystemService,
                           DHashService dHashService,
                           @Value("${app.files.hash-chunk-size-kb:64}") int hashChunkSizeKb) {
        this.db = db;
        this.settingsService = settingsService;
        this.imageRepo = imageRepo;
        this.imageMetadataRepository = imageMetadataRepository;
        this.pinnedFolderRepository = pinnedFolderRepository;
        this.collectionService = collectionService;
        this.imageMetadataService = imageMetadataService;
        this.tagService = tagService;
        this.pathService = pathService;
        this.searchRepository = searchRepository;
        this.fileSystemService = fileSystemService;
        this.dHashService = dHashService;
        this.hashChunkSize = hashChunkSizeKb * 1024;
    }

    public List<ImageDTO> getBulkImageDTOs(List<File> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> paths = files.stream()
                .map(pathService::getNormalizedAbsolutePath)
                .collect(Collectors.toList());

        Map<String, ImageInfo> infoMap = imageRepo.getBulkImageInfo(paths);

        return paths.stream()
                .map(path -> {
                    ImageInfo info = infoMap.get(path);
                    if (info != null) {
                        return new ImageDTO(info.path(), info.rating(), info.model());
                    }
                    // Fallback for files not yet in the database
                    return new ImageDTO(path, 0, "");
                })
                .collect(Collectors.toList());
    }

    public void shutdown() {
        log.info("Shutting down data services...");
        db.shutdown();
    }

    public File resolvePath(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) return null;
        try {
            return pathService.resolve(dbPath);
        } catch (Exception e) {
            log.warn("Could not resolve path: {}", dbPath);
            return null;
        }
    }

    public List<String> getDistinctMetadataValues(String key) {
        if (key == null || key.isBlank()) return Collections.emptyList();

        List<String> raw = imageMetadataRepository.getDistinctValues(key);
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
        if ("Sampler".equals(key)) {
            return raw.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> "Euler a".equalsIgnoreCase(s.trim()) ? "Euler a" : s)
                    .collect(Collectors.groupingBy(String::toLowerCase, Collectors.minBy(String::compareTo)))
                    .values()
                    .stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
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

    public CompletableFuture<List<ImageDTO>> findFilesWithFilters(String query, Map<String, String> filters, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, List<String>> listFilters = new java.util.HashMap<>();
                String collectionName = null;

                if (filters != null) {
                    for (Map.Entry<String, String> entry : filters.entrySet()) {
                        if ("Collection".equals(entry.getKey())) {
                            collectionName = entry.getValue();
                        } else {
                            listFilters.put(entry.getKey(), Collections.singletonList(entry.getValue()));
                        }
                    }
                }

                List<String> paths = searchRepository.findPaths(query, listFilters, collectionName, offset, limit);

                return paths.stream()
                        .map(path -> {
                            File file = pathService.resolve(path);
                            int rating = getRating(file);
                            String model = "";
                            if (hasCachedMetadata(file)) {
                                Map<String, String> meta = getCachedMetadata(file);
                                model = meta.getOrDefault("Model", "");
                            }
                            return new ImageDTO(pathService.getNormalizedAbsolutePath(file), rating, model);
                        })
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Async search filter operation failed", e);
                throw new ApplicationException("Failed to execute filtered search query.", e);
            }
        });
    }

    public boolean moveFileToTrash(File file) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("File to delete", file != null ? file.getAbsolutePath() : "null");
        }

        boolean success = fileSystemService.moveFileToTrash(file);

        if (success) {
            imageRepo.deleteByPath(pathService.getNormalizedAbsolutePath(file));
        }
        return success;
    }

    public void batchDeleteFiles(List<String> paths) {
        if (paths == null || paths.isEmpty()) return;

        List<String> deletedPaths = new ArrayList<>();
        for (String path : paths) {
            try {
                File file = pathService.resolve(path);
                if (file.exists()) {
                    if (fileSystemService.moveFileToTrash(file)) {
                        deletedPaths.add(pathService.getNormalizedAbsolutePath(file));
                    }
                } else {
                    deletedPaths.add(path);
                }
            } catch (Exception e) {
                log.warn("Failed to delete file in batch: {}", path);
            }
        }

        if (!deletedPaths.isEmpty()) {
            imageRepo.deleteByPaths(deletedPaths);
        }
    }

    /**
     * Renames a file on disk and updates the database atomically.
     * Uses a DB-First Intent pattern: The database is updated first within a transaction.
     * If the physical file move fails, the transaction is rolled back, keeping the DB in sync.
     */
    @Transactional
    public void renameFile(File file, String newName) {
        String oldPath = pathService.getNormalizedAbsolutePath(file);
        
        File parent = file.getParentFile();
        File newFile = new File(parent, newName);
        String newPath = pathService.getNormalizedAbsolutePath(newFile);

        if (newFile.exists()) {
            throw new ValidationException("A file with that name already exists in the destination.");
        }

        try {
            imageRepo.updatePath(oldPath, newPath);
            log.debug("Database intent updated: {} -> {}", oldPath, newPath);
        } catch (Exception e) {
            throw new ApplicationException("Failed to update database record for rename.", e);
        }

        try {
            fileSystemService.renameFile(file, newName);
            log.info("Successfully renamed file on disk and DB: {} -> {}", oldPath, newPath);
        } catch (Exception e) {
            log.error("Physical file rename failed. Rolling back database transaction...", e);
            throw new ApplicationException("Failed to rename file on disk. Database changes have been rolled back.", e);
        }
    }

    private int getOrCreateImageIdInternal(File file) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("Image file", "null");
        }
        try {
            String path = pathService.getNormalizedAbsolutePath(file);
            int id = imageRepo.getIdByPath(path);
            if (id != -1) return id;

            String hash = calculateHash(file);
            List<String> existingPaths = imageRepo.findPathsByHash(hash);
            
            if (!existingPaths.isEmpty()) {
                String oldPath = existingPaths.get(0);
                File oldFile = pathService.resolve(oldPath);
                
                if (!oldFile.exists()) {
                    log.info("Detected file move: {} -> {}", oldPath, path);
                    imageRepo.updatePath(oldPath, path);
                    return imageRepo.getIdByPath(path);
                } else {
                    log.debug("Detected file copy: {} and {} share hash {}", oldPath, path, hash);
                }
            }

            return imageRepo.getOrCreateId(path, hash);
        } catch (Exception e) {
            log.error("Failed to reconcile or register file: {}", file.getAbsolutePath(), e);
            throw new ApplicationException("System failed to register image in database.", e);
        }
    }

    /**
     * Calculates a unique SHA-256 fingerprint for a file.
     * Publicly accessible for maintenance tasks like duplicate detection repair.
     */
    public String calculateHash(File file) {
        try {
            long length = file.length();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            digest.update(String.valueOf(length).getBytes());

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                byte[] buffer = new byte[hashChunkSize];

                int bytesRead = raf.read(buffer);
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead);
                }

                if (length > hashChunkSize) {
                    raf.seek(length - hashChunkSize);
                    bytesRead = raf.read(buffer);
                    if (bytesRead > 0) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }
            }

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.error("Fingerprint calculation failed: {}", file.getAbsolutePath(), e);
            throw new ApplicationException("Failed to calculate unique file fingerprint.", e);
        }
    }

    public void cacheMetadata(File file, Map<String, String> meta) {
        cacheMetadata(file, meta, 0);
    }

    public void cacheMetadata(File file, Map<String, String> meta, long dHash) {
        int id = getOrCreateImageIdInternal(file);
        imageMetadataService.cacheMetadata(id, meta, dHash);
    }

    public Map<String, String> getCachedMetadata(File file) {
        String path = pathService.getNormalizedAbsolutePath(file);
        return imageMetadataService.getCachedMetadata(file, path);
    }

    public boolean hasCachedMetadata(File file) {
        String path = pathService.getNormalizedAbsolutePath(file);
        return imageMetadataService.hasCachedMetadata(path);
    }

    public void addTag(File file, String tag) {
        int id = getOrCreateImageIdInternal(file);
        tagService.addTag(id, tag);
    }

    public void removeTag(File file, String tag) {
        int id = getOrCreateImageIdInternal(file);
        tagService.removeTag(id, tag);
    }

    public Set<String> getTags(File file) {
        int id = getOrCreateImageIdInternal(file);
        return tagService.getTags(id);
    }

    public int getRating(File file) {
        if (file == null) return 0;
        return imageRepo.getRating(pathService.getNormalizedAbsolutePath(file));
    }

    public void setRating(File file, int rating) {
        int id = getOrCreateImageIdInternal(file);
        if (id > 0) imageRepo.setRating(id, rating);
    }

    public List<File> getStarredFilesList() {
        return imageRepo.getStarredPaths().stream()
                .map(pathService::resolve)
                .filter(f -> f != null && f.exists())
                .collect(Collectors.toList());
    }

    public List<File> getPinnedFolders() {
        return pinnedFolderRepository.getPinnedFolders().stream()
                .map(pathService::resolve)
                .filter(f -> f != null && f.exists())
                .collect(Collectors.toList());
    }

    public void addPinnedFolder(File folder) {
        if (folder == null) {
            throw new ValidationException("Folder parameter cannot be null.");
        }
        if (!folder.isDirectory()) {
            throw new ValidationException("Only directories can be pinned.");
        }
        pinnedFolderRepository.addPinnedFolder(pathService.getNormalizedAbsolutePath(folder));
    }

    public void removePinnedFolder(File folder) {
        if (folder != null) {
            pinnedFolderRepository.removePinnedFolder(pathService.getNormalizedAbsolutePath(folder));
        }
    }

    public List<String> getCollections() {
        return collectionService.getCollections();
    }

    public Optional<CreateCollectionRequest> getCollectionDetails(String name) {
        return collectionService.getCollectionDetails(name);
    }

    public void createCollection(CreateCollectionRequest request) {
        collectionService.createCollection(request);
    }

    public void updateCollection(String oldName, CreateCollectionRequest request) {
        collectionService.updateCollection(oldName, request);
    }

    public void deleteCollection(String name) {
        collectionService.deleteCollection(name);
    }

    public void addImagesToCollection(String collectionName, List<String> paths) {
        List<Integer> ids = new ArrayList<>();
        for (String path : paths) {
            try {
                File file = pathService.resolve(path);
                if (file.exists()) {
                    ids.add(getOrCreateImageIdInternal(file));
                }
            } catch (Exception e) {
                log.warn("Skipping invalid path during batch add: {}", path);
            }
        }
        if (!ids.isEmpty()) {
            collectionService.addImagesToCollection(collectionName, ids);
        }
    }

    public void blacklistImageFromCollection(String collectionName, File file) {
        int id = getOrCreateImageIdInternal(file);
        collectionService.blacklistImageFromCollection(collectionName, id);
    }

    public List<File> getFilesFromCollection(String collectionName) {
        return collectionService.getFilePathsFromCollection(collectionName).stream()
                .map(pathService::resolve)
                .filter(f -> f != null && f.exists())
                .collect(Collectors.toList());
    }

    public AppSettings getSettings() {
        return settingsService.get();
    }

    public void updateSettings(java.util.function.Consumer<AppSettings> updater) {
        settingsService.update(updater);
    }

    public File getLastFolder() {
        String path = settingsService.get().getLastFolder();
        return path != null ? pathService.resolve(path) : null;
    }

    public void setLastFolder(File folder) {
        if (folder != null) {
            String path = pathService.getNormalizedAbsolutePath(folder);
            settingsService.update(s -> s.setLastFolder(path));
        }
    }

    public void clearDatabase() {
        db.clearAllData();
    }

    public List<String> getExcludedPaths() {
        return settingsService.get().getExcludedPaths();
    }

    public void addExcludedPath(String path) {
        if (path == null || path.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getExcludedPaths());
            if (!current.contains(path)) {
                current.add(path);
                s.setExcludedPaths(current);
            }
        });
    }

    public void removeExcludedPath(String path) {
        if (path == null || path.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getExcludedPaths());
            if (current.remove(path)) {
                s.setExcludedPaths(current);
            }
        });
    }

    public List<String> getCustomPromptNodes() {
        return settingsService.get().getCustomPromptNodes();
    }

    public void addCustomPromptNode(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getCustomPromptNodes());
            if (!current.contains(nodeName)) {
                current.add(nodeName);
                s.setCustomPromptNodes(current);
            }
        });
    }

    public void removeCustomPromptNode(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getCustomPromptNodes());
            if (current.remove(nodeName)) {
                s.setCustomPromptNodes(current);
            }
        });
    }

    public List<String> getCustomLoraNodes() {
        return settingsService.get().getCustomLoraNodes();
    }

    public void addCustomLoraNode(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getCustomLoraNodes());
            if (!current.contains(nodeName)) {
                current.add(nodeName);
                s.setCustomLoraNodes(current);
            }
        });
    }

    public void removeCustomLoraNode(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return;
        settingsService.update(s -> {
            List<String> current = new ArrayList<>(s.getCustomLoraNodes());
            if (current.remove(nodeName)) {
                s.setCustomLoraNodes(current);
            }
        });
    }
}
