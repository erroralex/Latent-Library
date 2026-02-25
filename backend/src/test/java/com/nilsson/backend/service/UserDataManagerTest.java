package com.nilsson.backend.service;

import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private JsonSettingsService settingsService;
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
    private FileSystemService fileSystemService;
    @Mock
    private DHashService dHashService;

    private UserDataManager userDataManager;

    @BeforeEach
    void setUp() {
        // Manually construct the UserDataManager with all mocked dependencies
        userDataManager = new UserDataManager(
                db,
                settingsService,
                imageRepo,
                imageMetadataRepository,
                pinnedFolderRepository,
                collectionService,
                imageMetadataService,
                tagService,
                pathService,
                searchRepository,
                fileSystemService,
                dHashService,
                64 // Default value for hashChunkSizeKb
        );
    }

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
    @DisplayName("getExcludedPaths should return list from settings service")
    void testGetExcludedPaths() {
        AppSettings settings = new AppSettings();
        settings.setExcludedPaths(List.of("/path1", "/path2"));
        when(settingsService.get()).thenReturn(settings);

        List<String> results = userDataManager.getExcludedPaths();

        assertEquals(2, results.size());
        assertEquals("/path1", results.get(0));
        assertEquals("/path2", results.get(1));
    }

    @Test
    @DisplayName("addExcludedPath should update settings service")
    void testAddExcludedPath() {
        userDataManager.addExcludedPath("/path2");
        verify(settingsService).update(any());
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

    @Test
    @DisplayName("batchDeleteFiles should resolve paths and delegate to repository")
    void testBatchDeleteFiles() {
        String path1 = "/img1.png";
        String path2 = "/img2.png";
        File file1 = mock(File.class);
        File file2 = mock(File.class);

        when(pathService.resolve(path1)).thenReturn(file1);
        when(pathService.resolve(path2)).thenReturn(file2);
        when(file1.exists()).thenReturn(true);
        when(file2.exists()).thenReturn(true);
        
        // Mock normalization to return the paths
        when(pathService.getNormalizedAbsolutePath(file1)).thenReturn(path1);
        when(pathService.getNormalizedAbsolutePath(file2)).thenReturn(path2);

        when(fileSystemService.moveFileToTrash(any())).thenReturn(true);

        userDataManager.batchDeleteFiles(List.of(path1, path2));

        verify(imageRepo).deleteByPaths(argThat(list -> list.contains(path1) && list.contains(path2)));
    }

    @Test
    @DisplayName("addImagesToCollection should resolve IDs and delegate to collection service")
    void testAddImagesToCollection() {
        String path1 = "/img1.png";
        File file1 = mock(File.class);
        when(pathService.resolve(path1)).thenReturn(file1);
        when(file1.exists()).thenReturn(true);
        when(pathService.getNormalizedAbsolutePath(file1)).thenReturn(path1);
        when(imageRepo.getIdByPath(path1)).thenReturn(101);

        userDataManager.addImagesToCollection("MyColl", List.of(path1));

        verify(collectionService).addImagesToCollection(eq("MyColl"), argThat(list -> list.contains(101)));
    }
}
