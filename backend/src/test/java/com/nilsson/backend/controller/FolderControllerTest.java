package com.nilsson.backend.controller;

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
 * FolderControllerTest provides integration tests for the FolderController, verifying the
 * REST API endpoints for file system navigation and folder pinning. It ensures that
 * the application can correctly list system root drives, retrieve subdirectories
 * for a given path, and manage user-pinned folders. The tests use MockMvc to
 * simulate HTTP requests and verify the controller's response status and
 * JSON payload structure.
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
    @DisplayName("POST /api/folders/pin should call dataManager")
    void pinFolder_ShouldInvokeService() throws Exception {
        File mockFolder = mock(File.class);
        when(pathService.resolve("/test/path")).thenReturn(mockFolder);
        when(mockFolder.exists()).thenReturn(true);
        when(mockFolder.isDirectory()).thenReturn(true);

        mockMvc.perform(post("/api/folders/pin").param("path", "/test/path"))
                .andExpect(status().isOk());

        verify(dataManager).addPinnedFolder(mockFolder);
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
