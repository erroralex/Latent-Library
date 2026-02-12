package com.nilsson.backend.service;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Map;

/**
 * Service for managing the lifecycle, persistence, and caching of image-specific technical metadata.
 * <p>
 * This service provides a high-level interface for retrieving and persisting technical metadata
 * extracted from AI-generated images. It implements a caching strategy that prioritizes
 * database-stored metadata over expensive file-system extraction. It also ensures that
 * whenever metadata is cached, the corresponding search index (FTS) is updated to reflect
 * the new data.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Transparent Caching:</b> Automatically extracts and saves metadata to the database
 *   if it is not already present, reducing subsequent I/O overhead.</li>
 *   <li><b>Index Synchronization:</b> Coordinates with the {@link FtsService} to keep the
 *   SQLite FTS5 search index synchronized with the latest metadata.</li>
 *   <li><b>Transactional Integrity:</b> Ensures that metadata persistence and search indexing
 *   occur atomically within a single transaction.</li>
 *   <li><b>Metadata Discovery:</b> Provides efficient checks for the existence of cached
 *   metadata for specific file paths.</li>
 * </ul>
 */
@Service
public class ImageMetadataService {

    private final ImageRepository imageRepository;
    private final ImageMetadataRepository imageMetadataRepository;
    private final MetadataService metadataService;
    private final FtsService ftsService;

    public ImageMetadataService(ImageRepository imageRepository, ImageMetadataRepository imageMetadataRepository, MetadataService metadataService, FtsService ftsService) {
        this.imageRepository = imageRepository;
        this.imageMetadataRepository = imageMetadataRepository;
        this.metadataService = metadataService;
        this.ftsService = ftsService;
    }

    public Map<String, String> getCachedMetadata(File file, String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path cannot be empty for metadata retrieval.");
        }
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("Image file", path);
        }

        int imageId = imageRepository.getIdByPath(path);
        if (imageId != -1 && imageMetadataRepository.hasMetadata(imageId)) {
            return imageMetadataRepository.getMetadata(imageId);
        }

        Map<String, String> meta = metadataService.getExtractedData(file);
        if (imageId != -1) {
            saveMetadataAndIndex(imageId, meta);
        }
        return meta;
    }

    public boolean hasCachedMetadata(String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path cannot be empty for metadata check.");
        }
        int imageId = imageRepository.getIdByPath(path);
        return imageId != -1 && imageMetadataRepository.hasMetadata(imageId);
    }

    public void cacheMetadata(int imageId, Map<String, String> meta) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID for caching metadata.");
        }
        if (meta == null || meta.isEmpty()) {
            throw new ValidationException("Metadata map cannot be null or empty.");
        }
        saveMetadataAndIndex(imageId, meta);
    }

    @Transactional
    protected void saveMetadataAndIndex(int imageId, Map<String, String> meta) {
        if (imageId > 0 && meta != null && !meta.isEmpty()) {
            imageMetadataRepository.saveMetadata(imageId, meta);
            ftsService.updateFtsIndex(imageId);
        }
    }
}