package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link FileSystemService}, validating low-level OS file operations
 * and application-level fallback mechanisms.
 * <p>
 * This class ensures the reliability of file management by verifying:
 * <ul>
 *   <li><b>Physical Renaming:</b> Confirms that files are correctly renamed on disk and
 *   that name collisions are prevented via {@link ValidationException}.</li>
 *   <li><b>Fallback Deletion:</b> Validates the application-specific trash mechanism
 *   ({@code data/trash}) which acts as a safety net when system-native trash integration
 *   is unavailable or fails.</li>
 *   <li><b>Error Resilience:</b> Ensures that attempts to operate on non-existent files
 *   or invalid names are caught and reported with appropriate custom exceptions.</li>
 * </ul>
 * The tests utilize JUnit 5's {@code @TempDir} to provide a safe, isolated environment
 * for physical file manipulation.
 */
class FileSystemServiceTest {

    private FileSystemService fileSystemService;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService(tempDir.toString());
    }

    @Test
    @DisplayName("renameFile should successfully rename a physical file")
    void testRenameFileSuccess() throws IOException {
        Path sourcePath = tempDir.resolve("original.txt");
        Files.writeString(sourcePath, "content");
        File sourceFile = sourcePath.toFile();

        File renamedFile = fileSystemService.renameFile(sourceFile, "renamed.txt");

        assertNotNull(renamedFile);
        assertTrue(renamedFile.exists());
        assertEquals("renamed.txt", renamedFile.getName());
        assertFalse(sourceFile.exists());
    }

    @Test
    @DisplayName("renameFile should throw ValidationException if destination exists")
    void testRenameFileCollision() throws IOException {
        Path sourcePath = tempDir.resolve("source.txt");
        Path targetPath = tempDir.resolve("target.txt");
        Files.writeString(sourcePath, "content1");
        Files.writeString(targetPath, "content2");

        assertThrows(ValidationException.class, () -> 
            fileSystemService.renameFile(sourcePath.toFile(), "target.txt")
        );
    }

    @Test
    @DisplayName("renameFile should throw ResourceNotFoundException for missing file")
    void testRenameMissingFile() {
        File missingFile = new File(tempDir.toFile(), "ghost.txt");
        assertThrows(ResourceNotFoundException.class, () -> 
            fileSystemService.renameFile(missingFile, "new.txt")
        );
    }

    /**
     * Verifies that moveFileToTrash successfully removes the file from its original location.
     * Note: In environments where OS-native trash is supported (like Windows Desktop), 
     * the file may be moved to the system bin instead of the application fallback folder.
     */
    @Test
    @DisplayName("moveFileToTrash should successfully remove the file from disk")
    void testMoveToTrash() throws IOException {
        Path filePath = tempDir.resolve("to_delete.txt");
        Files.writeString(filePath, "delete me");
        File file = filePath.toFile();

        boolean result = fileSystemService.moveFileToTrash(file);

        assertTrue(result);
        assertFalse(file.exists(), "File should be removed from original location");
        
        // Check if fallback was used (common in headless/CI environments)
        Path trashDir = tempDir.resolve("data/trash");
        if (Files.exists(trashDir)) {
            long trashFileCount = Files.list(trashDir).count();
            assertTrue(trashFileCount >= 1, "If fallback was used, file should be in app trash");
        }
    }

    @Test
    @DisplayName("moveFileToTrash should throw ResourceNotFoundException for null or missing file")
    void testMoveToTrashInvalid() {
        assertThrows(ResourceNotFoundException.class, () -> fileSystemService.moveFileToTrash(null));
        
        File missingFile = new File(tempDir.toFile(), "not_here.txt");
        assertThrows(ResourceNotFoundException.class, () -> fileSystemService.moveFileToTrash(missingFile));
    }
}
