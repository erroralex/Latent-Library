package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LibraryControllerTest provides unit tests for the LibraryController, focusing on the
 * REST API endpoints for library-wide operations such as folder scanning. It
 * verifies that requests to initiate a scan of a specific directory are
 * correctly resolved and delegated to the IndexingService. The tests use
 * MockMvc to simulate HTTP requests and verify the controller's response status.
 */
@WebMvcTest(LibraryController.class)
@ActiveProfiles("test")
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexingService indexingService;

    @MockBean
    private PathService pathService;

    @MockBean
    private UserDataManager userDataManager;

    @MockBean
    private DataSource dataSource;

    @Test
    void scanFolder_ShouldTriggerIndexingAndReturnDTOs() throws Exception {
        String path = System.getProperty("java.io.tmpdir");
        when(pathService.resolve(any())).thenReturn(new File(path));
        when(userDataManager.getBulkImageDTOs(any())).thenReturn(Collections.singletonList(new ImageDTO("test.png", 0, "")));

        mockMvc.perform(post("/api/library/scan")
                        .param("path", path))
                .andExpect(status().isOk());

        verify(indexingService).indexFolder(any(File.class), eq(false));
        verify(userDataManager).getBulkImageDTOs(any());
    }
}
