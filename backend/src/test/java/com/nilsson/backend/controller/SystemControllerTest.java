package com.nilsson.backend.controller;

import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.DatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SystemControllerTest provides unit tests for the SystemController, focusing on the
 * REST API endpoints for system-level maintenance and configuration. It verifies
 * that administrative tasks such as clearing the database, retrieving excluded
 * paths, and triggering a rebuild of the Full-Text Search (FTS) index are
 * correctly delegated to the appropriate services. The tests use MockMvc
 * to simulate HTTP requests and verify the controller's response status.
 */
@WebMvcTest(SystemController.class)
@ActiveProfiles("test")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager userDataManager;

    @MockBean
    private FtsService ftsService;

    @MockBean
    private PathService pathService;

    @MockBean
    private IndexingService indexingService;

    @MockBean
    private DatabaseService databaseService;

    @MockBean
    private DataSource dataSource;

    @Test
    void clearDatabase_ShouldInvokeService() throws Exception {
        mockMvc.perform(post("/api/system/clear-database"))
                .andExpect(status().isOk());

        verify(userDataManager).clearDatabase();
    }

    @Test
    void getExcludedPaths_ShouldReturnList() throws Exception {
        when(userDataManager.getExcludedPaths()).thenReturn(List.of("/path1", "/path2"));

        mockMvc.perform(get("/api/system/excluded-paths"))
                .andExpect(status().isOk());
    }

    @Test
    void rebuildFtsIndex_ShouldReturnAccepted() throws Exception {
        mockMvc.perform(post("/api/system/rebuild-fts-index"))
                .andExpect(status().isAccepted());
    }
}
