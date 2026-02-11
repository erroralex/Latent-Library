package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Service responsible for background image indexing and real-time file system monitoring.
 *
 * <p>This service acts as the central orchestrator for synchronizing the local file system with
 * the application's database. It manages the lifecycle of image metadata and thumbnails by
 * performing initial directory scans, background indexing of metadata, and maintaining
 * consistency through real-time file system monitoring.
 *
 * <p>Key responsibilities include:
 * <ul>
 *   <li><b>Initial Indexing:</b> Scans user-defined folders to populate the database with image metadata and trigger thumbnail generation.</li>
 *   <li><b>Library Reconciliation:</b> Periodically verifies the database against the file system to remove "ghost" records of deleted files.</li>
 *   <li><b>Real-time Monitoring:</b> Utilizes the Java {@link java.nio.file.WatchService} to detect file creation, modification, and deletion events, ensuring the application state remains current.</li>
 *   <li><b>Concurrency Management:</b> Leverages Java 21+ Virtual Threads via {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()} to handle I/O-bound indexing tasks efficiently without blocking platform threads.</li>
 *   <li><b>Event Debouncing:</b> Implements a debouncing mechanism for file system events to prevent redundant processing during rapid file operations.</li>
 * </ul>
 *
 * <p>The service coordinates with {@link MetadataService} for extraction, {@link ThumbnailService}
 * for image processing, and {@link UserDataManager} for persistence.
 */
@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);
    private static final int BATCH_SIZE = 20;
    private static final long DEBOUNCE_DELAY_MS = 500;

    private final ImageRepository imageRepo;
    private final MetadataService metaService;
    private final UserDataManager dataManager;
    private final PathService pathService;
    private final ThumbnailService thumbnailService;
    private final ExecutorService executor;
    private final FtsService ftsService; // Added FtsService dependency

    private WatchService watchService;
    private Thread watchThread;

    private final Map<String, Long> pendingEvents = new ConcurrentHashMap<>();
    private final ReentrantLock dbLock = new ReentrantLock();

    public IndexingService(ImageRepository imageRepo,
                           MetadataService metaService,
                           UserDataManager dataManager,
                           PathService pathService,
                           ThumbnailService thumbnailService,
                           FtsService ftsService) { // Inject FtsService
        this.imageRepo = imageRepo;
        this.metaService = metaService;
        this.dataManager = dataManager;
        this.pathService = pathService;
        this.thumbnailService = thumbnailService;
        this.ftsService = ftsService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void cancel() {
        stopWatching();
    }

    public void reconcileLibrary() {
        File lastFolder = dataManager.getLastFolder();
        if (lastFolder != null && lastFolder.exists() && lastFolder.isDirectory()) {
            logger.info("[Reconcile] Fast-scanning last folder: {}", lastFolder.getName());
            indexFolder(lastFolder);
        }

        Runnable ghostCleanupTask = () -> {
            logger.info("[Reconcile] Starting background ghost record cleanup...");
            long start = System.currentTimeMillis();
            AtomicInteger removedCount = new AtomicInteger(0);

            imageRepo.forEachFilePath(path -> {
                File file = null;
                try {
                    file = pathService.resolve(path);
                } catch (InvalidPathException e) {
                    logger.warn("[Reconcile] Invalid path found in DB: {}. Deleting record.", path);
                    imageRepo.deleteByPath(path);
                    removedCount.incrementAndGet();
                    return;
                }

                if (file != null && !file.exists()) {
                    logger.info("[Reconcile] File missing: {}. Deleting record from DB.", path);
                    imageRepo.deleteByPath(path);
                    removedCount.incrementAndGet();
                }
            });

            if (removedCount.get() > 0) {
                logger.info("[Reconcile] Background cleanup finished. Removed {} ghost records in {}ms",
                        removedCount.get(), (System.currentTimeMillis() - start));
            } else {
                logger.debug("[Reconcile] Background cleanup finished. Library is consistent.");
            }
        };

        executor.submit(ghostCleanupTask);
    }

    /**
     * Scans a folder for images, triggering parallel thumbnail generation and metadata indexing.
     */
    public void indexFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;

        logger.info("Indexing folder: {}", folder.getName());

        try (Stream<Path> stream = Files.list(folder.toPath())) {
            List<File> batch = new ArrayList<>(BATCH_SIZE);
            
            stream.filter(path -> isImageFile(path.toFile()))
                  .forEach(path -> {
                      batch.add(path.toFile());
                      if (batch.size() >= BATCH_SIZE) {
                          processAndClearBatch(new ArrayList<>(batch));
                          batch.clear();
                      }
                  });

            // Process remaining files
            if (!batch.isEmpty()) {
                processAndClearBatch(batch);
            }
        } catch (IOException e) {
            logger.error("Failed to stream files from folder: {}", folder.getAbsolutePath(), e);
        }
    }

    private void processAndClearBatch(List<File> files) {
        if (files.isEmpty()) return;

        Thread.ofVirtual().start(() -> thumbnailService.preloadCache(files));

        startIndexing(files, null);
    }

    public void startIndexing(List<File> files, Consumer<BatchResult> onBatchResult) {
        if (files == null || files.isEmpty()) return;

        Runnable task = () -> {
            int total = files.size();
            for (int i = 0; i < total; i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, total);
                List<File> batch = files.subList(i, end);

                BatchResult result = processBatch(batch);

                if (onBatchResult != null) {
                    onBatchResult.accept(result);
                }

                Thread.yield();
            }
        };
        executor.submit(task);
    }

    public void startWatching(File directory, Consumer<FileChangeEvent> listener) {
        stopWatching();

        if (directory == null || !directory.exists() || !directory.isDirectory()) return;

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path path = directory.toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

            watchThread = new Thread(() -> watchLoop(path, listener));
            watchThread.setDaemon(true);
            watchThread.setName("Folder-Watcher-" + directory.getName());
            watchThread.start();

            logger.info("Started watching directory: {}", directory);

        } catch (IOException e) {
            logger.error("Failed to start WatchService for {}", directory, e);
        }
    }

    public void stopWatching() {
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
        pendingEvents.clear();
    }

    private void watchLoop(Path monitoredPath, Consumer<FileChangeEvent> listener) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException x) {
                    break;
                }

                long now = System.currentTimeMillis();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path fileName = (Path) event.context();
                    File file = monitoredPath.resolve(fileName).toFile();

                    if (!isImageFile(file)) continue;

                    String eventKey = file.getAbsolutePath() + "_" + kind.name();

                    if (pendingEvents.containsKey(eventKey)) {
                        long lastTime = pendingEvents.get(eventKey);
                        if (now - lastTime < DEBOUNCE_DELAY_MS) {
                            continue;
                        }
                    }

                    pendingEvents.put(eventKey, now);

                    executor.submit(() -> {
                        try {
                            Thread.sleep(DEBOUNCE_DELAY_MS);
                            if (pendingEvents.getOrDefault(eventKey, 0L) == now) {
                                pendingEvents.remove(eventKey);
                                processFileSystemEvent(kind, file, listener);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }

                boolean valid = key.reset();
                if (!valid) break;
            }
        } catch (Exception e) {
            logger.debug("WatchService loop ended: {}", e.getMessage());
        }
    }

    private void processFileSystemEvent(WatchEvent.Kind<?> kind, File file, Consumer<FileChangeEvent> listener) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            if (file.exists() && file.canRead() && file.length() > 0) {
                handleFileCreation(file);
                if (listener != null) listener.accept(new FileChangeEvent(file, ChangeType.CREATED));
            }
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            handleFileDeletion(file);
            if (listener != null) listener.accept(new FileChangeEvent(file, ChangeType.DELETED));
        }
    }

    private void handleFileCreation(File file) {
        try {
            Map<String, String> meta = metaService.getExtractedData(file);
            
            dbLock.lock();
            try {
                dataManager.cacheMetadata(file, meta);
            } finally {
                dbLock.unlock();
            }
            
            thumbnailService.getThumbnail(file);
            logger.debug("Indexed new/modified file: {}", file.getName());
        } catch (Exception e) {
            logger.error("Error processing new file: {}", file.getName(), e);
        }
    }

    private void handleFileDeletion(File file) {
        try {
            String path = pathService.getNormalizedAbsolutePath(file);
            
            dbLock.lock();
            try {
                imageRepo.deleteByPath(path);
            } finally {
                dbLock.unlock();
            }
            
            logger.debug("Deleted file from disk and DB: {}", file.getName());
        } catch (Exception e) {
            logger.error("Error processing deleted file: {}", file.getName(), e);
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private BatchResult processBatch(List<File> batch) {
        Map<File, Map<String, String>> metadataMap = new HashMap<>();
        Map<File, Integer> ratingMap = new HashMap<>();

        for (File file : batch) {
            try {
                Map<String, String> meta = metaService.getExtractedData(file);
                
                dbLock.lock();
                try {
                    dataManager.cacheMetadata(file, meta);
                    int rating = dataManager.getRating(file);
                    ratingMap.put(file, rating);
                } finally {
                    dbLock.unlock();
                }

                metadataMap.put(file, meta);
                thumbnailService.getThumbnail(file);
            } catch (Exception e) {
                logger.error("Failed to index file: {}", file.getAbsolutePath(), e);
            }
        }

        return new BatchResult(metadataMap, ratingMap);
    }

    public record BatchResult(Map<File, Map<String, String>> metadata, Map<File, Integer> ratings) {
    }

    public enum ChangeType {
        CREATED, DELETED
    }

    public record FileChangeEvent(File file, ChangeType type) {
    }
}