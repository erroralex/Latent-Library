package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.repository.ImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Service responsible for background image indexing, library reconciliation, and real-time file system monitoring.
 * <p>
 * This service manages the discovery and synchronization of image files within the user's library. It
 * performs deep indexing of folders, extracting metadata and generating thumbnails in parallel using
 * virtual threads. It also implements a robust file system watcher to detect and react to changes
 * (creations, deletions, modifications) in the currently active directory.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Library Reconciliation:</b> Synchronizes the database state with the physical file system,
 *   identifying missing or deleted files.</li>
 *   <li><b>Parallel Indexing:</b> Utilizes Java 21 Virtual Threads to process large batches of images
 *   concurrently, extracting metadata and calculating perceptual hashes.</li>
 *   <li><b>File System Monitoring:</b> Implements a debounced {@link WatchService} to provide real-time
 *   updates to the library as files are added or removed.</li>
 *   <li><b>Resource Management:</b> Employs a {@link Semaphore} to prevent system saturation during
 *   intensive recursive scans.</li>
 * </ul>
 */
@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final int batchSize;
    private final long debounceDelayMs;
    private final long watchRetryDelayMs;

    private final ImageRepository imageRepo;
    private final MetadataService metaService;
    private final UserDataManager dataManager;
    private final PathService pathService;
    private final ThumbnailService thumbnailService;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final DHashService dHashService;
    
    private WatchService watchService;
    private Thread watchThread;
    private final Map<String, Long> pendingEvents = new ConcurrentHashMap<>();

    private final Semaphore processingPermits;

    public IndexingService(ImageRepository imageRepo,
                           MetadataService metaService,
                           UserDataManager dataManager,
                           PathService pathService,
                           ThumbnailService thumbnailService,
                           DHashService dHashService,
                           @Value("${app.indexing.batch-size:20}") int batchSize,
                           @Value("${app.indexing.debounce-ms:500}") long debounceDelayMs,
                           @Value("${app.indexing.watch-retry-ms:5000}") long watchRetryDelayMs,
                           @Value("${app.indexing.max-concurrent:10}") int maxConcurrent) {
        this.imageRepo = imageRepo;
        this.metaService = metaService;
        this.dataManager = dataManager;
        this.pathService = pathService;
        this.thumbnailService = thumbnailService;
        this.dHashService = dHashService;
        this.batchSize = batchSize;
        this.debounceDelayMs = debounceDelayMs;
        this.watchRetryDelayMs = watchRetryDelayMs;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.processingPermits = new Semaphore(maxConcurrent);
    }

    public void cancel() {
        stopWatching();
        scheduler.shutdownNow();
    }

    public void reconcileLibrary() {
        File lastFolder = dataManager.getLastFolder();
        if (lastFolder != null && lastFolder.exists() && lastFolder.isDirectory()) {
            indexFolder(lastFolder, false);
        }

        Runnable lazyReconcileTask = () -> {
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
                        imageRepo.deleteByPath(path);
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
        indexFolder(folder, false);
    }

    public void indexFolder(File folder, boolean recursive) {
        if (folder == null || !folder.isDirectory()) return;

        String folderPath = pathService.getNormalizedAbsolutePath(folder);
        List<String> excludedPaths = dataManager.getExcludedPaths();
        for (String excluded : excludedPaths) {
            if (folderPath.startsWith(excluded.replace("\\", "/"))) return;
        }

        logger.info("Indexing folder: {} (Recursive: {})", folder.getName(), recursive);

        Runnable scanTask = () -> {
            try {
                List<File> batch = new ArrayList<>(batchSize);

                if (recursive) {
                    Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (isImageFile(file.toFile())) {
                                batch.add(file.toFile());
                                if (batch.size() >= batchSize) {
                                    startIndexing(new ArrayList<>(batch), null);
                                    batch.clear();
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String dirPath = pathService.getNormalizedAbsolutePath(dir.toFile());
                            for (String excluded : excludedPaths) {
                                if (dirPath.startsWith(excluded.replace("\\", "/"))) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    try (Stream<Path> stream = Files.list(folder.toPath())) {
                        stream.filter(path -> isImageFile(path.toFile()))
                                .forEach(path -> {
                                    batch.add(path.toFile());
                                    if (batch.size() >= batchSize) {
                                        startIndexing(new ArrayList<>(batch), null);
                                        batch.clear();
                                    }
                                });
                    }
                }

                if (!batch.isEmpty()) {
                    startIndexing(batch, null);
                }
            } catch (IOException e) {
                logger.error("Failed to scan folder: {}", folder.getAbsolutePath(), e);
            }
        };

        executor.submit(scanTask);
    }

    public void startIndexing(List<File> files, Consumer<BatchResult> onBatchResult) {
        if (files == null || files.isEmpty()) return;

        thumbnailService.preloadCache(files);

        Runnable task = () -> {
            int total = files.size();
            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<File> batch = files.subList(i, end);

                try {
                    processingPermits.acquire();
                    try {
                        BatchResult result = processBatch(batch);
                        if (onBatchResult != null) {
                            onBatchResult.accept(result);
                        }
                    } finally {
                        processingPermits.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
        executor.submit(task);
    }

    public void startWatching(File directory, Consumer<FileChangeEvent> listener) {
        stopWatching();
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;

        watchThread = Thread.ofVirtual()
                .name("Folder-Watcher-" + directory.getName())
                .start(() -> watchLoop(directory, listener));
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
                    directory.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                }

                WatchKey key = watchService.take();
                long now = System.currentTimeMillis();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path fileName = (Path) event.context();
                    File file = directory.toPath().resolve(fileName).toFile();
                    if (!isImageFile(file)) continue;

                    String eventKey = file.getAbsolutePath() + "_" + event.kind().name();
                    if (pendingEvents.containsKey(eventKey) && (now - pendingEvents.get(eventKey) < debounceDelayMs))
                        continue;

                    pendingEvents.put(eventKey, now);
                    scheduler.schedule(() -> {
                        if (pendingEvents.getOrDefault(eventKey, 0L) == now) {
                            pendingEvents.remove(eventKey);
                            processFileSystemEvent(event.kind(), file, listener);
                        }
                    }, debounceDelayMs, TimeUnit.MILLISECONDS);
                }
                if (!key.reset()) break;
            } catch (Exception e) {
                closeWatchService();
                try {
                    Thread.sleep(watchRetryDelayMs);
                } catch (InterruptedException ie) {
                    break;
                }
            }
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
            long dHash = dHashService.calculateDHash(file);
            dataManager.cacheMetadata(file, meta, dHash);
            thumbnailService.getThumbnail(file);
        } catch (Exception e) {
            logger.error("Error processing new file: {}", file.getName(), e);
        }
    }

    private void handleFileDeletion(File file) {
        try {
            imageRepo.deleteByPath(pathService.getNormalizedAbsolutePath(file));
        } catch (Exception e) {
            logger.error("Error processing deleted file: {}", file.getName(), e);
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    public List<File> getImagesInFolder(File folder, boolean recursive, List<String> excludedPaths) {
        List<File> files = new ArrayList<>();
        String folderPath = pathService.getNormalizedAbsolutePath(folder);

        if (recursive) {
            try (Stream<Path> stream = Files.walk(folder.toPath())) {
                stream.filter(p -> !Files.isDirectory(p))
                      .filter(p -> {
                          String pStr = pathService.getNormalizedAbsolutePath(p.toFile());
                          for (String excluded : excludedPaths) {
                              if (pStr.startsWith(excluded.replace("\\", "/"))) return false;
                          }
                          return true;
                      })
                      .map(Path::toFile)
                      .filter(this::isImageFile)
                      .forEach(files::add);
            } catch (IOException e) {
                throw new ApplicationException("Failed to walk directory recursively: " + folderPath, e);
            }
        } else {
            File[] directFiles = folder.listFiles((dir, name) -> isImageFile(new File(dir, name)));
            if (directFiles != null) {
                files.addAll(Arrays.asList(directFiles));
            }
        }

        files.sort(Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private BatchResult processBatch(List<File> batch) {
        Map<File, Map<String, String>> metadataMap = new HashMap<>();
        Map<File, Integer> ratingMap = new HashMap<>();

        for (File file : batch) {
            try {
                Map<String, String> meta = metaService.getExtractedData(file);
                long dHash = dHashService.calculateDHash(file);
                dataManager.cacheMetadata(file, meta, dHash);
                ratingMap.put(file, dataManager.getRating(file));
                metadataMap.put(file, meta);
            } catch (Exception e) {
                logger.debug("Failed to index file: {} - {}", file.getName(), e.getMessage());
            }
        }
        return new BatchResult(metadataMap, ratingMap);
    }

    public record BatchResult(Map<File, Map<String, String>> metadata, Map<File, Integer> ratings) {
    }

    public enum ChangeType {CREATED, DELETED}

    public record FileChangeEvent(File file, ChangeType type) {
    }
}
