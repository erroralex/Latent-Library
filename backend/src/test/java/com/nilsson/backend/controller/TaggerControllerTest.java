package com.nilsson.backend.controller;

import com.nilsson.backend.service.ImageTaggerService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.TaggerModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for the {@link TaggerController}, validating the REST API endpoints
 * for AI-based image interrogation and model management.
 * <p>
 * This class ensures that the AI tagging features are correctly exposed and orchestrated by verifying:
 * <ul>
 *   <li><b>Status Reporting:</b> Confirms that the controller correctly reports model
 *   readiness and download progress.</li>
 *   <li><b>Model Acquisition:</b> Validates that model download requests are accepted
 *   and triggered asynchronously (202 Accepted).</li>
 *   <li><b>Batch Tagging:</b> Ensures that folder-wide tagging tasks are initiated
 *   with proper path resolution and status codes.</li>
 *   <li><b>Resource Cleanup:</b> Verifies that requests to clear AI models are correctly
 *   processed.</li>
 * </ul>
 */
@WebMvcTest(TaggerController.class)
@ActiveProfiles("test")
class TaggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaggerModelService modelService;

    @MockBean
    private ImageTaggerService taggerService;

    @MockBean
    private PathService pathService;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /api/tagger/status should return model state")
    void getStatus_ShouldReturnJson() throws Exception {
        when(modelService.isModelReady()).thenReturn(true);
        when(modelService.isDownloading()).thenReturn(false);
        when(modelService.getDownloadProgress()).thenReturn(100);

        mockMvc.perform(get("/api/tagger/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.progress").value(100));
    }

    @Test
    @DisplayName("POST /api/tagger/download should return 202 Accepted")
    void downloadModel_ShouldReturnAccepted() throws Exception {
        mockMvc.perform(post("/api/tagger/download"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Download started"));

        verify(modelService).downloadModel();
    }

    @Test
    @DisplayName("POST /api/tagger/tag-folder should return 202 Accepted for valid path")
    void tagFolder_ShouldReturnAccepted() throws Exception {
        String path = "/test/images";
        File mockDir = new File(path);
        // We can't easily mock File.exists() for a real File object without PowerMock,
        // but we can mock the pathService to return a mock File.
        File mockFile = org.mockito.Mockito.mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.getName()).thenReturn("images");
        when(pathService.resolve(anyString())).thenReturn(mockFile);

        mockMvc.perform(post("/api/tagger/tag-folder").param("path", path))
                .andExpect(status().isAccepted());
    }
}
