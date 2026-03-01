package com.nilsson.backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe component for tracking the real-time progress of background indexing operations.
 * <p>
 * This tracker provides a centralized state for the current indexing job, allowing the frontend
 * to poll for progress updates. It uses atomic primitives to ensure non-blocking, thread-safe access.
 * It also implements a "Scan ID" mechanism to prevent race conditions where "zombie" threads from
 * a cancelled scan might incorrectly update the progress of a new, active scan.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>State Management:</b> Maintains the active status, total file count, and processed count of the current scan.</li>
 *   <li><b>Concurrency Safety:</b> Uses {@link AtomicInteger} and {@link AtomicBoolean} to prevent race conditions without locking.</li>
 *   <li><b>Session Isolation:</b> Uses a unique scan ID to ensure updates are only applied to the active job.</li>
 * </ul>
 */
@Component
public class IndexingStatusTracker {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final AtomicInteger totalFilesToScan = new AtomicInteger(0);
    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private final AtomicLong currentScanId = new AtomicLong(0);

    /**
     * Resets the tracker and marks the start of a new indexing job.
     *
     * @param totalFiles The total number of files identified for scanning.
     * @return The unique ID assigned to this scan session.
     */
    public long startNewScan(int totalFiles) {
        totalFilesToScan.set(totalFiles);
        filesProcessed.set(0);
        isIndexing.set(true);
        return currentScanId.incrementAndGet();
    }

    /**
     * Incrementally updates the processed file count if the scan ID matches the active session.
     *
     * @param scanId The ID of the scan session requesting the update.
     */
    public void incrementProcessed(long scanId) {
        if (scanId == currentScanId.get()) {
            filesProcessed.incrementAndGet();
        }
    }

    /**
     * Marks the current indexing job as complete.
     */
    public void finishScan() {
        isIndexing.set(false);
    }

    /**
     * Retrieves the current status snapshot.
     *
     * @return An immutable {@link IndexingStatus} record containing the current state.
     */
    public IndexingStatus getStatus() {
        return new IndexingStatus(
                isIndexing.get(),
                totalFilesToScan.get(),
                filesProcessed.get()
        );
    }

    /**
     * DTO record for transferring indexing status to the client.
     */
    public record IndexingStatus(boolean isIndexing, int totalFiles, int processedFiles) {
    }
}
