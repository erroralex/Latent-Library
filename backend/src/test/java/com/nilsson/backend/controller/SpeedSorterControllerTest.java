package com.nilsson.backend.controller;

import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for the {@link SpeedSorterController}, validating the REST API endpoints
 * for the rapid image organization utility.
 * <p>
 * This class ensures the functionality of the Speed Sorter tool by verifying:
 * <ul>
 *   <li><b>Configuration Management:</b> Confirms that tool-specific settings are correctly
 *   retrieved and updated via the data manager.</li>
 *   <li><b>Input Orchestration:</b> Validates the setting of active input folders for
 *   rapid sorting sessions.</li>
 *   <li><b>File Operations:</b> Ensures that destructive operations (e.g., deletion)
 *   triggered from the Speed Sorter UI are correctly delegated to the batch deletion engine.</li>
 * </ul>
 */
@WebMvcTest(SpeedSorterController.class)
@ActiveProfiles("test")
class SpeedSorterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager userDataManager;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /api/speedsorter/config should return current settings")
    void getConfig_ShouldReturnSettings() throws Exception {
        when(userDataManager.getSettings()).thenReturn(new AppSettings());

        mockMvc.perform(get("/api/speedsorter/config"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/speedsorter/config/input should update active folder")
    void setInputFolder_ShouldUpdateSetting() throws Exception {
        String path = System.getProperty("java.io.tmpdir");

        mockMvc.perform(post("/api/speedsorter/config/input")
                        .param("path", path))
                .andExpect(status().isOk());

        verify(userDataManager).updateSettings(any());
    }

    @Test
    @DisplayName("POST /api/speedsorter/delete should invoke batch deletion")
    void deleteFile_ShouldInvokeBatchDelete() throws Exception {
        mockMvc.perform(post("/api/speedsorter/delete")
                        .param("path", "/to-delete.png"))
                .andExpect(status().isOk());

        verify(userDataManager).batchDeleteFiles(List.of("/to-delete.png"));
    }
}
