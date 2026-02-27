package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for the {@link FolderController}, validating the REST API endpoints
 * for file system navigation, directory traversal, and folder bookmark management.
 * <p>
 * This class utilizes {@link MockMvc} to simulate HTTP interactions and verify:
 * <ul>
 *   <li><b>System Discovery:</b> Ensures that root drives and subdirectories are correctly
 *   listed and mapped to DTOs.</li>
 *   <li><b>Bookmark Management:</b> Validates the pinning and unpinning of folders, including
 *   strict input validation for file vs. directory paths.</li>
 *   <li><b>Error Handling:</b> Confirms that the controller returns appropriate HTTP status
 *   codes (400, 404) when encountering invalid or missing paths.</li>
 * </ul>
 * The tests are executed against a mocked service layer to isolate the controller's
 * request mapping and response formatting logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager dataManager;

    @MockBean
    private PathService pathService;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /api/folders/roots should return system root drives")
    void getRoots_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/folders/roots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/folders/children should list subdirectories")
    void getChildren_ShouldReturnSubfolders() throws Exception {
        File mockFolder = mock(File.class);
        File subFolder = mock(File.class);

        when(pathService.resolve(anyString())).thenReturn(mockFolder);
        when(mockFolder.exists()).thenReturn(true);
        when(mockFolder.isDirectory()).thenReturn(true);
        when(mockFolder.listFiles()).thenReturn(new File[]{subFolder});

        when(subFolder.isHidden()).thenReturn(false);
        when(subFolder.isDirectory()).thenReturn(true);
        when(subFolder.getName()).thenReturn("SubDir");
        when(pathService.getNormalizedAbsolutePath(subFolder)).thenReturn("/parent/SubDir");

        mockMvc.perform(get("/api/folders/children").param("path", "/parent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("SubDir"))
                .andExpect(jsonPath("$[0].path").value("/parent/SubDir"));
    }

    @Test
    @DisplayName("POST /api/folders/pin should call dataManager for valid directory")
    void pinFolder_ShouldInvokeService() throws Exception {
        File mockFolder = mock(File.class);
        when(pathService.resolve("/test/path")).thenReturn(mockFolder);
        when(mockFolder.exists()).thenReturn(true);
        when(mockFolder.isDirectory()).thenReturn(true);

        mockMvc.perform(post("/api/folders/pin").param("path", "/test/path"))
                .andExpect(status().isOk());

        verify(dataManager).addPinnedFolder(mockFolder);
    }

    /**
     * Verifies that the system rejects attempts to pin a file rather than a directory.
     * This ensures the integrity of the navigation tree and prevents I/O errors
     * during recursive scanning.
     */
    @Test
    @DisplayName("POST /api/folders/pin should reject file paths")
    void pinFolder_ShouldRejectFile() throws Exception {
        File mockFile = mock(File.class);
        when(pathService.resolve("/test/image.png")).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.isDirectory()).thenReturn(false);

        mockMvc.perform(post("/api/folders/pin").param("path", "/test/image.png"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that the system returns a 404 status when attempting to pin
     * a path that does not exist on the physical file system.
     */
    @Test
    @DisplayName("POST /api/folders/pin should return 404 for missing paths")
    void pinFolder_ShouldReturn404ForMissingPath() throws Exception {
        when(pathService.resolve("/ghost/path")).thenThrow(new ResourceNotFoundException("Folder", "/ghost/path"));

        mockMvc.perform(post("/api/folders/pin").param("path", "/ghost/path"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/folders/pinned should return pinned folders")
    void getPinnedFolders_ShouldReturnList() throws Exception {
        File pinned = mock(File.class);
        when(pinned.getName()).thenReturn("Pinned");
        when(pathService.getNormalizedAbsolutePath(pinned)).thenReturn("/pinned");
        when(dataManager.getPinnedFolders()).thenReturn(List.of(pinned));

        mockMvc.perform(get("/api/folders/pinned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pinned"));
    }
}
