package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.ThumbnailService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ImageControllerTest provides unit tests for the ImageController, focusing on the
 * REST API endpoints for image searching, metadata retrieval, and management.
 * It utilizes MockMvc to simulate HTTP requests and verify the controller's
 * response status and interaction with the underlying services. The tests
 * cover scenarios such as searching for images with filters, retrieving
 * distinct metadata values for UI filters, updating image ratings, and
 * handling requests for non-existent files.
 */
@WebMvcTest(ImageController.class)
@ActiveProfiles("test")
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager dataManager;

    @MockBean
    private PathService pathService;

    @MockBean
    private ThumbnailService thumbnailService;

    @MockBean
    private DataSource dataSource;

    @Test
    void searchImages_ShouldReturnJsonList() throws Exception {
        when(dataManager.findFilesWithFilters(any(), any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(new ImageDTO("/path.png", 0, ""))));

        mockMvc.perform(get("/api/images/search")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getFilters_ShouldReturnFilterMap() throws Exception {
        when(dataManager.getDistinctMetadataValues(anyString())).thenReturn(List.of("Val1", "Val2"));

        mockMvc.perform(get("/api/images/filters"))
                .andExpect(status().isOk());
    }

    @Test
    void setRating_ShouldUpdateRating() throws Exception {
        Path tempFile = Files.createTempFile("test-rating", ".png");
        try {
            when(pathService.resolve(anyString())).thenReturn(tempFile.toFile());

            mockMvc.perform(post("/api/images/rating")
                            .param("path", "test-rating.png")
                            .param("rating", "5"))
                    .andExpect(status().isOk());

            verify(dataManager).setRating(any(File.class), eq(5));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void getMetadata_NotFound_ShouldReturn404() throws Exception {
        File missingFile = new File("non-existent-file.png");
        when(pathService.resolve(anyString())).thenReturn(missingFile);

        mockMvc.perform(get("/api/images/metadata")
                        .param("path", "missing.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchDeleteImages_ShouldInvokeService() throws Exception {
        String json = "[\"/path/1.png\", \"/path/2.png\"]";

        mockMvc.perform(post("/api/images/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).batchDeleteFiles(anyList());
    }
}
