package com.nilsson.backend.controller;

import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SpeedSorterControllerTest is an integration test suite for the SpeedSorterController, focusing on the
 * configuration and execution of rapid file sorting operations. It verifies that input and target
 * directories can be correctly configured and retrieved from the application's settings.
 * Additionally, the tests validate the logic for moving files to target slots and the
 * system-level trash operation, ensuring that the Speed Sorter utility correctly
 * interfaces with the UserDataManager and the underlying file system.
 */
@WebMvcTest(SpeedSorterController.class)
class SpeedSorterControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserDataManager dataManager;

    @Test
    @DisplayName("GET /api/speedsorter/config should return current settings")
    void getConfig_ShouldReturnSettings() throws Exception {
        AppSettings settings = new AppSettings();
        settings.getSpeedSorter().setInputDir("/input");
        settings.getSpeedSorter().setTargets(List.of("/target1", "/target2"));
        
        when(dataManager.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/speedsorter/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputDir").value("/input"))
                .andExpect(jsonPath("$.targets").isArray())
                .andExpect(jsonPath("$.targets[0].path").value("/target1"));
    }

    @Test
    @DisplayName("POST /api/speedsorter/config/input should update setting")
    void setInputFolder_ShouldUpdateSetting() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        String path = tempDir.getAbsolutePath();

        mockMvc.perform(post("/api/speedsorter/config/input").param("path", path))
                .andExpect(status().isOk());

        verify(dataManager).updateSettings(any());
    }

    @Test
    @DisplayName("POST /api/speedsorter/delete should move file to trash")
    void deleteFile_ShouldInvokeTrash() throws Exception {
        File tempFile = File.createTempFile("to-delete", ".png");
        tempFile.deleteOnExit();

        when(dataManager.moveFileToTrash(any(File.class))).thenReturn(true);

        mockMvc.perform(post("/api/speedsorter/delete").param("path", tempFile.getAbsolutePath()))
                .andExpect(status().isOk());

        verify(dataManager).moveFileToTrash(any(File.class));
    }
}
