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
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ImageControllerTest performs integration testing for the ImageController using Spring's MockMvc.
 * It verifies the REST API endpoints for image searching, metadata retrieval, rating updates,
 * and filter generation. The tests ensure that the controller correctly handles HTTP requests,
 * interacts with the service layer, and returns the expected JSON responses or status codes,
 * including error handling for non-existent resources.
 */
@WebMvcTest(ImageController.class)
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager dataManager;

    @MockBean
    private PathService pathService;

    @MockBean
    private ThumbnailService thumbnailService;

    @Test
    void searchImages_ShouldReturnJsonList() throws Exception {
        ImageDTO mockImage = new ImageDTO("/tmp/img.png", 5, "hash123");

        when(dataManager.findFilesWithFilters(any(), any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(mockImage)));

        mockMvc.perform(get("/api/images/search")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("/tmp/img.png"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].model").value("hash123"));
    }

    @Test
    void getFilters_ShouldReturnFilterMap() throws Exception {
        when(dataManager.getDistinctMetadataValues("Model")).thenReturn(List.of("Flux", "SDXL"));

        mockMvc.perform(get("/api/images/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value("Flux"));
    }

    @Test
    void setRating_ShouldUpdateRating() throws Exception {
        File temp = File.createTempFile("test-rating", ".png");
        temp.deleteOnExit();
        when(pathService.resolve("test-rating.png")).thenReturn(temp);

        mockMvc.perform(post("/api/images/rating")
                        .param("path", "test-rating.png")
                        .param("rating", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void getMetadata_NotFound_ShouldReturn404() throws Exception {
        File nonExistent = new File("/does/not/exist.png");
        when(pathService.resolve(anyString())).thenReturn(nonExistent);

        mockMvc.perform(get("/api/images/metadata").param("path", "missing.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchDeleteImages_ShouldInvokeService() throws Exception {
        List<String> paths = List.of("/path/1.png", "/path/2.png");
        String json = "[\"/path/1.png\", \"/path/2.png\"]";

        mockMvc.perform(post("/api/images/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).batchDeleteFiles(paths);
    }
}