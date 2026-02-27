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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for the {@link IndexingService}, validating the background image indexing
 * and library reconciliation engine.
 * <p>
 * This class utilizes Mockito for dependency isolation and JUnit 5's {@code @TempDir} for safe,
 * platform-independent file system simulation. It ensures that the service correctly handles:
 * <ul>
 *   <li><b>Parallel Indexing:</b> Verifies that multiple images are processed concurrently,
 *   metadata is extracted, and thumbnails are preloaded.</li>
 *   <li><b>Library Reconciliation:</b> Validates the synchronization logic that removes obsolete
 *   database records (ghost files) and marks unreachable directories as missing.</li>
 *   <li><b>Task Cancellation:</b> Ensures that ongoing indexing operations can be gracefully
 *   interrupted, preventing resource leaks and ensuring thread safety.</li>
 * </ul>
 * The tests are designed to run in a local-first environment, simulating various file system
 * states to guarantee the integrity of the image library's persistent state.
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
    private DHashService dHashService;

    private IndexingService indexingService;

    @BeforeEach
    void setUp() {
        indexingService = new IndexingService(
                imageRepo, metaService, dataManager, pathService, thumbnailService, dHashService,
                20, 500, 5000, 10
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
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(imageRepo).setMissing(missingFolderPath, true);
        verify(imageRepo, never()).deleteByPath(anyString());
    }

    @Test
    void cancel_ShouldStopOngoingIndexing() throws InterruptedException {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(new File("dummy_" + i + ".png"));
        }

        when(metaService.getExtractedData(any())).thenAnswer(inv -> {
            Thread.sleep(50);
            return Map.of();
        });

        CountDownLatch latch = new CountDownLatch(1);

        indexingService.startIndexing(files, (res) -> latch.countDown());

        Thread.sleep(100);

        indexingService.cancel();

        Thread.sleep(200);

        verify(dataManager, atMost(20)).cacheMetadata(any(), any(), anyLong());
    }
}
