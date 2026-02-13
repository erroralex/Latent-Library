package com.nilsson.backend.controller;

import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LibraryControllerTest is an integration test suite for the LibraryController, focusing on the
 * orchestration of library-wide management tasks and folder scanning operations. It verifies
 * that requests to scan specific directories correctly trigger the IndexingService and
 * update the application's state regarding the user's last-accessed folder. The tests
 * ensure that the controller respects exclusion rules and handles directory validation,
 * confirming that the library indexing pipeline is correctly initiated via the REST API.
 */
@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private IndexingService indexingService;
    @MockBean
    private UserDataManager userDataManager;
    @MockBean
    private PathService pathService;

    @Test
    @DisplayName("POST /api/library/scan should trigger indexing if not excluded")
    void scanFolder_ShouldTriggerIndexing() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        String path = tempDir.getAbsolutePath();

        // Mock PathService to return a valid file object
        when(pathService.resolve(path)).thenReturn(tempDir);
        when(userDataManager.getExcludedPaths()).thenReturn(List.of());
        when(pathService.getNormalizedAbsolutePath(any(File.class))).thenReturn(path);

        mockMvc.perform(post("/api/library/scan").param("path", path))
                .andExpect(status().isOk());

        verify(userDataManager).setLastFolder(any(File.class));
        verify(indexingService).indexFolder(any(File.class));
    }
}