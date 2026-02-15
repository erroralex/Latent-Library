package com.nilsson.backend.controller;

import com.nilsson.backend.model.AppSettings;
import com.nilsson.backend.service.UserDataManager;
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
 * SpeedSorterControllerTest provides unit tests for the SpeedSorterController, focusing on the
 * REST API endpoints for the application's rapid image organization tool. It verifies
 * that configuration settings for the speed sorter are correctly retrieved and
 * updated, and that file deletion requests are properly delegated to the
 * UserDataManager. The tests use MockMvc to simulate HTTP requests and
 * verify the controller's response status.
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
    void getConfig_ShouldReturnSettings() throws Exception {
        when(userDataManager.getSettings()).thenReturn(new AppSettings());

        mockMvc.perform(get("/api/speedsorter/config"))
                .andExpect(status().isOk());
    }

    @Test
    void setInputFolder_ShouldUpdateSetting() throws Exception {
        String path = System.getProperty("java.io.tmpdir");

        mockMvc.perform(post("/api/speedsorter/config/input")
                        .param("path", path))
                .andExpect(status().isOk());

        verify(userDataManager).updateSettings(any());
    }

    @Test
    void deleteFile_ShouldInvokeBatchDelete() throws Exception {
        mockMvc.perform(post("/api/speedsorter/delete")
                        .param("path", "/to-delete.png"))
                .andExpect(status().isOk());

        verify(userDataManager).batchDeleteFiles(List.of("/to-delete.png"));
    }
}
