package com.nilsson.backend.service;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ImageMetadataServiceTest is responsible for validating the coordination logic between image metadata extraction,
 * database caching, and search indexing. It ensures that the service correctly prioritizes cached metadata
 * from the database to minimize file system I/O, while seamlessly falling back to extraction when necessary.
 * The tests also verify that any new metadata is properly persisted and synchronized with the Full-Text
 * Search (FTS) index, maintaining data consistency across the application's storage layers.
 */
@ExtendWith(MockitoExtension.class)
class ImageMetadataServiceTest {

    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ImageMetadataRepository metadataRepository;
    @Mock
    private MetadataService metadataService;
    @Mock
    private FtsService ftsService;
    @Mock
    private DHashService dHashService;

    @InjectMocks
    private ImageMetadataService imageMetadataService;

    @Test
    @DisplayName("getCachedMetadata should return from DB if present")
    void getCachedMetadata_ShouldReturnFromCache() throws IOException {
        String path = "/test/img.png";
        File file = mock(File.class);
        when(file.exists()).thenReturn(true);

        int imageId = 1;
        Map<String, String> cachedMeta = Map.of("Prompt", "cached");

        when(imageRepository.getIdByPath(path)).thenReturn(imageId);
        when(metadataRepository.hasMetadata(imageId)).thenReturn(true);
        when(metadataRepository.getMetadata(imageId)).thenReturn(cachedMeta);

        Map<String, String> result = imageMetadataService.getCachedMetadata(file, path);

        assertEquals("cached", result.get("Prompt"));
        verifyNoInteractions(metadataService);
    }

    @Test
    @DisplayName("getCachedMetadata should extract and save if not in DB")
    void getCachedMetadata_ShouldExtractAndSave() {
        String path = "/test/img.png";
        File file = mock(File.class);
        when(file.exists()).thenReturn(true);

        int imageId = 1;
        Map<String, String> extractedMeta = Map.of("Prompt", "extracted");
        long dHash = 12345L;

        when(imageRepository.getIdByPath(path)).thenReturn(imageId);
        when(metadataRepository.hasMetadata(imageId)).thenReturn(false);
        when(metadataService.getExtractedData(file)).thenReturn(extractedMeta);
        when(dHashService.calculateDHash(file)).thenReturn(dHash);

        Map<String, String> result = imageMetadataService.getCachedMetadata(file, path);

        assertEquals("extracted", result.get("Prompt"));
        verify(metadataRepository).saveMetadata(imageId, extractedMeta);
        verify(metadataRepository).saveDHash(imageId, dHash);
        verify(ftsService).updateFtsIndex(imageId);
    }

    @Test
    @DisplayName("getCachedMetadata should throw exception if file missing")
    void getCachedMetadata_ShouldThrowIfFileMissing() {
        File missingFile = new File("/missing.png");
        assertThrows(ResourceNotFoundException.class, () ->
                imageMetadataService.getCachedMetadata(missingFile, "/missing.png"));
    }

    @Test
    @DisplayName("cacheMetadata should save and update FTS")
    void cacheMetadata_ShouldSaveAndIndex() {
        int imageId = 1;
        Map<String, String> meta = Map.of("Model", "SDXL");
        long dHash = 12345L;

        imageMetadataService.cacheMetadata(imageId, meta, dHash);

        verify(metadataRepository).saveMetadata(imageId, meta);
        verify(metadataRepository).saveDHash(imageId, dHash);
        verify(ftsService).updateFtsIndex(imageId);
    }
}
