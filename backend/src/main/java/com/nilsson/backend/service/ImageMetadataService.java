package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Map;

/**
 * Service for managing the lifecycle and caching of image-specific metadata.
 * <p>
 * This service provides a high-level interface for retrieving and persisting technical metadata.
 * It implements a caching strategy that prioritizes database-stored metadata over expensive
 * file-system extraction. It also ensures that whenever metadata is cached, the corresponding
 * search index (FTS) is updated to reflect the new data.
 * <p>
 * Key functionalities:
 * - Transparent Caching: Automatically extracts and saves metadata if not already present in the DB.
 * - Index Synchronization: Coordinates with {@code FtsService} to keep search results up to date.
 * - Transactional Integrity: Ensures that metadata persistence and indexing occur atomically.
 * - Metadata Discovery: Provides a quick check for the existence of cached data for a given path.
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
        int imageId = imageRepository.getIdByPath(path);
        return imageId != -1 && imageMetadataRepository.hasMetadata(imageId);
    }

    public void cacheMetadata(int imageId, Map<String, String> meta) {
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
