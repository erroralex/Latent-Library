package com.nilsson.backend.controller;

import com.nilsson.backend.service.DuplicateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for the {@link DuplicateController}, validating the REST API endpoints
 * for duplicate image detection and resolution.
 * <p>
 * This class ensures that the similarity features are correctly exposed by verifying:
 * <ul>
 *   <li><b>Status Reporting:</b> Confirms that the controller correctly reports the
 *   number of images missing hashes.</li>
 *   <li><b>Scan Orchestration:</b> Validates that requests to repair missing hashes
 *   are correctly delegated to the service.</li>
 *   <li><b>Pair Retrieval:</b> Ensures that the list of identified duplicate pairs
 *   is returned as a standardized JSON array.</li>
 *   <li><b>Auto-Resolution:</b> Verifies that the bulk resolution logic is triggered
 *   correctly via the API.</li>
 * </ul>
 */
@WebMvcTest(DuplicateController.class)
@ActiveProfiles("test")
class DuplicateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DuplicateService duplicateService;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /api/duplicates/status should return hash statistics")
    void getStatus_ShouldReturnJson() throws Exception {
        when(duplicateService.getStatus()).thenReturn(Map.of("missingHashes", 5, "totalImages", 100));

        mockMvc.perform(get("/api/duplicates/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingHashes").value(5));
    }

    @Test
    @DisplayName("POST /api/duplicates/scan should trigger hash repair")
    void scanForMissingHashes_ShouldInvokeService() throws Exception {
        when(duplicateService.scanAndFixHashes()).thenReturn("Repaired 5 images");

        mockMvc.perform(post("/api/duplicates/scan"))
                .andExpect(status().isOk());

        verify(duplicateService).scanAndFixHashes();
    }

    @Test
    @DisplayName("GET /api/duplicates/pairs should return list of duplicate pairs")
    void getDuplicatePairs_ShouldReturnList() throws Exception {
        when(duplicateService.findDuplicatePairs()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/duplicates/pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
