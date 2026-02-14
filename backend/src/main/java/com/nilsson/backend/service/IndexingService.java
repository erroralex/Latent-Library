package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.repository.ImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Service responsible for background image indexing, library reconciliation, and real-time file system monitoring.
 * <p>
 * This service acts as the central orchestrator for synchronizing the local file system with the application's
 * database. It manages the lifecycle of image metadata and thumbnails by performing initial directory scans,
 * background indexing of metadata, and maintaining consistency through real-time file system monitoring.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Initial Indexing:</b> Scans user-defined folders to populate the database with image metadata
 *   and trigger thumbnail generation, respecting exclusion rules.</li>
 *   <li><b>Library Reconciliation:</b> Periodically verifies the database against the file system to
 *   identify and remove "ghost" records of files that have been deleted or moved externally.</li>
 *   <li><b>Real-time Monitoring:</b> Utilizes the Java {@link WatchService} to detect file creation,
 *   modification, and deletion events, ensuring the application state remains current without manual refreshes.</li>
 *   <li><b>Concurrency Management:</b> Leverages Java 21+ Virtual Threads to handle I/O-bound indexing
 *   tasks efficiently, allowing for high-throughput processing without blocking platform threads.</li>
 *   <li><b>Event Debouncing:</b> Implements a sophisticated debouncing mechanism for file system events
 *   to prevent redundant processing during rapid file operations (e.g., saving a file multiple times).</li>
 * </ul>
 */
@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);
    private static final int BATCH_SIZE = 20;
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long WATCH_RETRY_DELAY_MS = 5000;

    private final ImageRepository imageRepo;
    private final MetadataService metaService;
    private final UserDataManager dataManager;
    private final PathService pathService;
    private final ThumbnailService thumbnailService;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final FtsService ftsService;
    private final DHashService dHashService;

    private WatchService watchService;
    private Thread watchThread;

    private final Map<String, Long> pendingEvents = new ConcurrentHashMap<>();

    public IndexingService(ImageRepository imageRepo,
                           MetadataService metaService,
                           UserDataManager dataManager,
                           PathService pathService,
                           ThumbnailService thumbnailService,
                           FtsService ftsService,
                           DHashService dHashService) {
        this.imageRepo = imageRepo;
        this.metaService = metaService;
        this.dataManager = dataManager;
        this.pathService = pathService;
        this.thumbnailService = thumbnailService;
        this.ftsService = ftsService;
        this.dHashService = dHashService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void cancel() {
        stopWatching();
        scheduler.shutdownNow();
    }

    public void reconcileLibrary() {
        File lastFolder = dataManager.getLastFolder();
        if (lastFolder != null && lastFolder.exists() && lastFolder.isDirectory()) {
            logger.info("[Reconcile] Fast-scanning last folder: {}", lastFolder.getName());
            indexFolder(lastFolder);
        }

        Runnable lazyReconcileTask = () -> {
            logger.info("[Reconcile] Starting background library reconciliation...");
            long start = System.currentTimeMillis();
            AtomicInteger removedCount = new AtomicInteger(0);
            AtomicInteger markedMissingCount = new AtomicInteger(0);

            Set<String> checkedDirs = new HashSet<>();

            imageRepo.forEachFilePath(path -> {
                try {
                    File file = pathService.resolve(path);
                    File parentDir = file.getParentFile();

                    if (parentDir != null) {
                        String parentPath = parentDir.getAbsolutePath();
                        if (!checkedDirs.contains(parentPath)) {
                            if (!parentDir.exists()) {
                                imageRepo.setMissing(path, true);
                                markedMissingCount.incrementAndGet();
                                checkedDirs.add(parentPath);
                                return;
                            }
                            checkedDirs.add(parentPath);
                        }
                    }

                    if (!file.exists()) {
                        logger.info("[Reconcile] File missing from disk: {}. Deleting record.", path);
                        imageRepo.deleteByPath(path);
                        
                        File thumbnail = thumbnailService.getThumbnail(file);
                        if (thumbnail != null && thumbnail.exists()) {
                            thumbnail.delete();
                        }
                        
                        removedCount.incrementAndGet();
                    } else {
                        if (imageRepo.isMissing(path)) {
                            imageRepo.setMissing(path, false);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("[Reconcile] Error checking path: {}. Skipping.", path);
                }
            });

            logger.info("[Reconcile] Background reconciliation finished. Removed: {}, Marked Missing: {} in {}ms",
                    removedCount.get(), markedMissingCount.get(), (System.currentTimeMillis() - start));
        };

        executor.submit(lazyReconcileTask);
    }

    public void indexFolder(File folder) {
        if (folder == null) {
            throw new ValidationException("Folder parameter cannot be null.");
        }
        if (!folder.isDirectory()) {
            throw new ValidationException("Provided path is not a directory: " + folder.getAbsolutePath());
        }

        String folderPath = pathService.getNormalizedAbsolutePath(folder);
        List<String> excludedPaths = dataManager.getExcludedPaths();
        for (String excluded : excludedPaths) {
            String normalizedExcluded = excluded.replace("\\", "/");
            if (folderPath.startsWith(normalizedExcluded)) {
                logger.info("Skipping excluded folder: {}", folderPath);
                return;
            }
        }

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

            if (!batch.isEmpty()) {
                processAndClearBatch(batch);
            }
        } catch (IOException e) {
            logger.error("Failed to stream files from folder: {}", folder.getAbsolutePath(), e);
            throw new ApplicationException("I/O error during folder indexing.", e);
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

        String folderPath = pathService.getNormalizedAbsolutePath(directory);
        List<String> excludedPaths = dataManager.getExcludedPaths();
        for (String excluded : excludedPaths) {
            String normalizedExcluded = excluded.replace("\\", "/");
            if (folderPath.startsWith(normalizedExcluded)) {
                logger.info("Skipping watch for excluded folder: {}", folderPath);
                return;
            }
        }

        watchThread = new Thread(() -> watchLoop(directory, listener));
        watchThread.setDaemon(true);
        watchThread.setName("Folder-Watcher-" + directory.getName());
        watchThread.start();

        logger.info("Started watching directory: {}", directory);
    }

    public void stopWatching() {
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        closeWatchService();
        pendingEvents.clear();
    }

    private void closeWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
    }

    private void watchLoop(File directory, Consumer<FileChangeEvent> listener) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (watchService == null) {
                    this.watchService = FileSystems.getDefault().newWatchService();
                    Path path = directory.toPath();
                    path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                }

                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                long now = System.currentTimeMillis();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path fileName = (Path) event.context();
                    File file = directory.toPath().resolve(fileName).toFile();

                    if (!isImageFile(file)) continue;

                    String eventKey = file.getAbsolutePath() + "_" + kind.name();

                    if (pendingEvents.containsKey(eventKey)) {
                        long lastTime = pendingEvents.get(eventKey);
                        if (now - lastTime < DEBOUNCE_DELAY_MS) {
                            continue;
                        }
                    }

                    pendingEvents.put(eventKey, now);

                    scheduler.schedule(() -> {
                        if (pendingEvents.getOrDefault(eventKey, 0L) == now) {
                            pendingEvents.remove(eventKey);
                            processFileSystemEvent(kind, file, listener);
                        }
                    }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
                }

                boolean valid = key.reset();
                if (!valid) {
                    logger.warn("WatchKey invalid. Directory may have been deleted or inaccessible: {}", directory);
                    break;
                }

            } catch (Exception e) {
                logger.error("WatchService error for {}: {}. Retrying in {}ms...", directory, e.getMessage(), WATCH_RETRY_DELAY_MS);
                closeWatchService();
                try {
                    Thread.sleep(WATCH_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        closeWatchService();
        logger.info("Watch loop terminated for {}", directory);
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
            long dHash = dHashService.calculateDHash(file);

            dataManager.cacheMetadata(file, meta, dHash);

            thumbnailService.getThumbnail(file);
            logger.debug("Indexed new/modified file: {}", file.getName());
        } catch (Exception e) {
            logger.error("Error processing new file: {}", file.getName(), e);
        }
    }

    private void handleFileDeletion(File file) {
        try {
            String path = pathService.getNormalizedAbsolutePath(file);

            imageRepo.deleteByPath(path);
            
            File thumbnail = thumbnailService.getThumbnail(file);
            if (thumbnail != null && thumbnail.exists()) {
                if (thumbnail.delete()) {
                    logger.debug("Deleted orphaned thumbnail for: {}", file.getName());
                }
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
                long dHash = dHashService.calculateDHash(file);

                dataManager.cacheMetadata(file, meta, dHash);
                int rating = dataManager.getRating(file);
                ratingMap.put(file, rating);

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
