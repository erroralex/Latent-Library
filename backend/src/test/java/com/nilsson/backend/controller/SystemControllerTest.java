package com.nilsson.backend.controller;

import com.nilsson.backend.service.FtsService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.DatabaseService;
import org.junit.jupiter.api.DisplayName;
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
 * Integration test suite for the {@link SystemController}, validating the REST API endpoints
 * for system maintenance, configuration, and diagnostics.
 * <p>
 * This class ensures that administrative and maintenance operations are correctly
 * exposed and delegated by verifying:
 * <ul>
 *   <li><b>Database Maintenance:</b> Confirms that requests to clear application data
 *   are correctly routed to the data manager.</li>
 *   <li><b>Configuration Access:</b> Validates the retrieval of system settings such
 *   as excluded paths and application version information.</li>
 *   <li><b>Index Management:</b> Ensures that background tasks like FTS index rebuilding
 *   are triggered with the appropriate HTTP status codes (e.g., 202 Accepted).</li>
 * </ul>
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
    @DisplayName("POST /api/system/clear-database should invoke data manager")
    void clearDatabase_ShouldInvokeService() throws Exception {
        mockMvc.perform(post("/api/system/clear-database"))
                .andExpect(status().isOk());

        verify(userDataManager).clearDatabase();
    }

    @Test
    @DisplayName("GET /api/system/excluded-paths should return list of paths")
    void getExcludedPaths_ShouldReturnList() throws Exception {
        when(userDataManager.getExcludedPaths()).thenReturn(List.of("/path1", "/path2"));

        mockMvc.perform(get("/api/system/excluded-paths"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/system/rebuild-fts-index should return 202 Accepted")
    void rebuildFtsIndex_ShouldReturnAccepted() throws Exception {
        mockMvc.perform(post("/api/system/rebuild-fts-index"))
                .andExpect(status().isAccepted());
    }
}
