package com.nilsson.backend.service;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for the {@link ImageMetadataService}, validating the coordination between
 * metadata extraction, database caching, and search indexing.
 * <p>
 * This class ensures the efficiency and consistency of the metadata layer by verifying:
 * <ul>
 *   <li><b>Cache Prioritization:</b> Confirms that the service retrieves metadata from the
 *   database when available to minimize expensive file system I/O.</li>
 *   <li><b>Fallback Extraction:</b> Validates that the service seamlessly falls back to
 *   physical extraction and perceptual hashing when cache misses occur.</li>
 *   <li><b>Search Synchronization:</b> Ensures that the Full-Text Search (FTS) index is
 *   automatically updated whenever new metadata is cached.</li>
 *   <li><b>Error Handling:</b> Verifies that attempts to retrieve metadata for non-existent
 *   files correctly trigger {@link ResourceNotFoundException}.</li>
 * </ul>
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
