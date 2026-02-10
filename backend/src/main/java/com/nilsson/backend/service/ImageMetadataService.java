package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Map;

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
    private void saveMetadataAndIndex(int imageId, Map<String, String> meta) {
        if (imageId > 0 && meta != null && !meta.isEmpty()) {
            imageMetadataRepository.saveMetadata(imageId, meta);
            ftsService.updateFtsIndex(imageId);
        }
    }
}
