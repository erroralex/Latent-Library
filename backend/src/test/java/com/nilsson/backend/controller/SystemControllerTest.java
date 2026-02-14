package com.nilsson.backend.controller;

import com.nilsson.backend.service.DatabaseService;
import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SystemControllerTest is an integration test suite for the SystemController, focusing on system-level
 * administrative operations and configuration management.
 */
@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ConfigurableApplicationContext context;
    
    @MockBean
    private FtsService ftsService;
    
    @MockBean
    private PathService pathService;
    
    @MockBean
    private UserDataManager userDataManager;

    @MockBean
    private IndexingService indexingService;

    @MockBean
    private DatabaseService databaseService;

    @Test
    @DisplayName("POST /api/system/clear-database should invoke userDataManager")
    void clearDatabase_ShouldInvokeService() throws Exception {
        mockMvc.perform(post("/api/system/clear-database"))
                .andExpect(status().isOk())
                .andExpect(content().string("Database cleared."));

        verify(userDataManager).clearDatabase();
    }

    @Test
    @DisplayName("GET /api/system/excluded-paths should return list")
    void getExcludedPaths_ShouldReturnList() throws Exception {
        when(userDataManager.getExcludedPaths()).thenReturn(List.of("/exclude/me"));

        mockMvc.perform(get("/api/system/excluded-paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("/exclude/me"));
    }

    @Test
    @DisplayName("POST /api/system/rebuild-fts-index should return 202 Accepted")
    void rebuildFtsIndex_ShouldReturnAccepted() throws Exception {
        mockMvc.perform(post("/api/system/rebuild-fts-index"))
                .andExpect(status().isAccepted());
    }
}
