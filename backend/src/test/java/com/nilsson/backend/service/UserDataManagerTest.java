package com.nilsson.backend.service;

import com.nilsson.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserDataManagerTest is a high-level integration test suite for the UserDataManager facade.
 * It verifies the orchestration of complex business logic across multiple repositories and services,
 * including metadata normalization, file tracking via hashing, and system-level operations.
 * The tests ensure that the facade correctly aggregates data from specialized components
 * to provide a unified and consistent API for the application's controller layer.
 * It specifically validates the normalization of metadata values for UI display, the management
 * of application settings like excluded paths, and the coordination of image-specific
 * actions such as rating updates.
 */
@ExtendWith(MockitoExtension.class)
class UserDataManagerTest {

    @Mock
    private DatabaseService db;
    @Mock
    private SettingsRepository settingsRepo;
    @Mock
    private ImageRepository imageRepo;
    @Mock
    private ImageMetadataRepository imageMetadataRepository;
    @Mock
    private PinnedFolderRepository pinnedFolderRepository;
    @Mock
    private CollectionService collectionService;
    @Mock
    private ImageMetadataService imageMetadataService;
    @Mock
    private TagService tagService;
    @Mock
    private PathService pathService;
    @Mock
    private SearchRepository searchRepository;
    @Mock
    private FtsService ftsService;

    @InjectMocks
    private UserDataManager userDataManager;

    @Test
    @DisplayName("getDistinctMetadataValues should clean and normalize LoRA names")
    void testDistinctMetadataNormalization() {
        when(imageMetadataRepository.getDistinctValues("Loras")).thenReturn(List.of("<lora:my_lora:0.8>, <lora:other:1.0>"));

        List<String> results = userDataManager.getDistinctMetadataValues("Loras");

        assertEquals(2, results.size());
        assertTrue(results.contains("my_lora"));
        assertTrue(results.contains("other"));
    }

    @Test
    @DisplayName("getExcludedPaths should parse semicolon-separated settings")
    void testGetExcludedPaths() {
        when(settingsRepo.get("excluded_paths", "")).thenReturn("/path1;/path2");

        List<String> results = userDataManager.getExcludedPaths();

        assertEquals(2, results.size());
        assertEquals("/path1", results.get(0));
        assertEquals("/path2", results.get(1));
    }

    @Test
    @DisplayName("addExcludedPath should append new path to settings")
    void testAddExcludedPath() {
        when(settingsRepo.get("excluded_paths", "")).thenReturn("/path1");

        userDataManager.addExcludedPath("/path2");

        verify(settingsRepo).set("excluded_paths", "/path1;/path2");
    }

    @Test
    @DisplayName("setRating should delegate to imageRepo after resolving ID")
    void testSetRating() {
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(pathService.getNormalizedAbsolutePath(mockFile)).thenReturn("/img.png");
        when(imageRepo.getIdByPath("/img.png")).thenReturn(123);

        userDataManager.setRating(mockFile, 5);

        verify(imageRepo).setRating(123, 5);
    }
}