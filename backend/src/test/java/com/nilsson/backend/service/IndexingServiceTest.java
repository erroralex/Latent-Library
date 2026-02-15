package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IndexingServiceTest provides unit and integration tests for the IndexingService.
 * It verifies the logic for scanning directories, extracting metadata from images,
 * generating thumbnails, and synchronizing the database state with the physical file system.
 * The tests utilize Mockito for dependency isolation and JUnit 5's TempDir for file system
 * simulation, ensuring that the indexing pipeline correctly handles both new file additions
 * and the removal of obsolete records.
 */
@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private ImageRepository imageRepo;
    @Mock
    private MetadataService metaService;
    @Mock
    private UserDataManager dataManager;
    @Mock
    private PathService pathService;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private FtsService ftsService;
    @Mock
    private DHashService dHashService;

    private IndexingService indexingService;

    @BeforeEach
    void setUp() {
        indexingService = new IndexingService(
                imageRepo, metaService, dataManager, pathService, thumbnailService, ftsService, dHashService
        );
    }

    @Test
    void indexFolder_ShouldProcessImages(@TempDir Path tempDir) throws IOException, InterruptedException {
        File imageFile = tempDir.resolve("test.jpg").toFile();
        assertTrue(imageFile.createNewFile());

        when(metaService.getExtractedData(any())).thenReturn(Map.of("Model", "TestModel"));
        when(dHashService.calculateDHash(any())).thenReturn(12345L);

        CountDownLatch latch = new CountDownLatch(1);

        indexingService.startIndexing(List.of(imageFile), (batchResult) -> {
            latch.countDown();
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Indexing task did not complete in time");

        verify(dataManager, atLeastOnce()).cacheMetadata(eq(imageFile), any(), anyLong());
        // Updated verification: startIndexing now calls preloadCache instead of getThumbnail directly
        verify(thumbnailService, atLeastOnce()).preloadCache(anyList());
    }

    @Test
    void reconcileLibrary_ShouldRemoveGhostRecords(@TempDir Path tempDir) {
        File existingDir = tempDir.toFile();
        String ghostPath = new File(existingDir, "ghost.png").getAbsolutePath().replace("\\", "/");
        
        when(dataManager.getLastFolder()).thenReturn(null);

        doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept(ghostPath);
            return null;
        }).when(imageRepo).forEachFilePath(any());

        when(pathService.resolve(ghostPath)).thenReturn(new File(ghostPath));

        indexingService.reconcileLibrary();

        try {
            Thread.sleep(200); // Wait for virtual thread
        } catch (InterruptedException e) {
        }

        verify(imageRepo).deleteByPath(ghostPath);
    }

    @Test
    void reconcileLibrary_ShouldMarkAsMissingIfFolderIsGone() {
        String missingFolderPath = "/missing/drive/img.png";
        when(dataManager.getLastFolder()).thenReturn(null);

        doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept(missingFolderPath);
            return null;
        }).when(imageRepo).forEachFilePath(any());

        when(pathService.resolve(missingFolderPath)).thenReturn(new File(missingFolderPath));

        indexingService.reconcileLibrary();

        try {
            Thread.sleep(200); // Wait for virtual thread
        } catch (InterruptedException e) {
        }

        verify(imageRepo).setMissing(missingFolderPath, true);
        verify(imageRepo, never()).deleteByPath(anyString());
    }
}
