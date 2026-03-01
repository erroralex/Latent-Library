package com.nilsson.backend.service;

import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * High-level integration test suite for the {@link UserDataManager} facade, validating the
 * orchestration of complex business logic across multiple repositories and services.
 * <p>
 * This class ensures the integrity of the application's data management layer by verifying:
 * <ul>
 *   <li><b>Intelligent File Tracking:</b> Validates the move-detection logic that uses file
 *   hashes to reconcile database records when files are renamed or moved on disk.</li>
 *   <li><b>Metadata Normalization:</b> Confirms that raw metadata values (e.g., LoRAs) are
 *   correctly cleaned and formatted for UI display.</li>
 *   <li><b>System Orchestration:</b> Ensures that batch operations like deletion and
 *   collection management are correctly delegated to specialized services.</li>
 *   <li><b>Settings Management:</b> Validates the persistence and retrieval of user
 *   preferences and exclusion rules.</li>
 * </ul>
 * The tests utilize Mockito to isolate the facade from physical I/O and database side effects.
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
    @Mock
    private FtsService ftsService;

    private UserDataManager userDataManager;

    @BeforeEach
    void setUp() {
        userDataManager = new UserDataManager(
                db, settingsService, imageRepo, imageMetadataRepository, pinnedFolderRepository,
                collectionService, imageMetadataService, tagService, pathService, searchRepository,
                fileSystemService, dHashService, ftsService, 64
        );
    }

    /**
     * Verifies the "Move Detection" logic. When a file is encountered at a new path but
     * shares a hash with a missing record, the system should update the existing record
     * instead of creating a duplicate.
     */
    @Test
    @DisplayName("getOrCreateImageIdInternal should detect file move via hash")
    void testFileMoveDetection(@TempDir Path tempDir) throws IOException {
        String oldPath = "/old/path/img.png";
        
        // Create a real file in the temp directory so RandomAccessFile doesn't NPE
        Path newFilePath = tempDir.resolve("new_img.png");
        Files.writeString(newFilePath, "dummy content for hashing");
        File newFile = newFilePath.toFile();
        String newPath = newFile.getAbsolutePath().replace("\\", "/");

        File oldFile = mock(File.class);

        when(pathService.getNormalizedAbsolutePath(newFile)).thenReturn(newPath);
        when(imageRepo.getIdByPath(newPath)).thenReturn(-1); // Not in DB at new path

        // Mock the repository finding the hash at an old location
        when(imageRepo.findPathsByHash(anyString())).thenReturn(List.of(oldPath));
        when(pathService.resolve(oldPath)).thenReturn(oldFile);
        when(oldFile.exists()).thenReturn(false); // Old file is gone!

        // Trigger the internal logic via a public method that calls it
        userDataManager.setRating(newFile, 5);

        // Verify that the system updated the path instead of inserting a new one
        verify(imageRepo).updatePath(oldPath, newPath);
    }

    /**
     * Verifies that if two files share a hash but both exist on disk, the system
     * treats them as distinct copies rather than a move.
     */
    @Test
    @DisplayName("getOrCreateImageIdInternal should treat identical existing files as copies")
    void testFileCopyDetection(@TempDir Path tempDir) throws IOException {
        String existingPath = "/path/copy1.png";
        
        Path newFilePath = tempDir.resolve("copy2.png");
        Files.writeString(newFilePath, "identical content");
        File newFile = newFilePath.toFile();
        String newPath = newFile.getAbsolutePath().replace("\\", "/");

        File existingFile = mock(File.class);

        when(pathService.getNormalizedAbsolutePath(newFile)).thenReturn(newPath);
        when(imageRepo.getIdByPath(newPath)).thenReturn(-1);

        when(imageRepo.findPathsByHash(anyString())).thenReturn(List.of(existingPath));
        when(pathService.resolve(existingPath)).thenReturn(existingFile);
        when(existingFile.exists()).thenReturn(true); // Existing file is still there!

        userDataManager.setRating(newFile, 5);

        // Verify that updatePath was NEVER called (it's a copy, not a move)
        verify(imageRepo, never()).updatePath(anyString(), anyString());
        // Verify it tried to create a new record
        verify(imageRepo).getOrCreateId(eq(newPath), anyString());
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
