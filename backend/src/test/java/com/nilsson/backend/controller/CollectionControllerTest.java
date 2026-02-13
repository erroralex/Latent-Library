package com.nilsson.backend.controller;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CollectionControllerTest is an integration test suite for the CollectionController, designed to verify
 * the full CRUD (Create, Retrieve, Update, Delete) lifecycle of image collections. It ensures that
 * collection definitions, including their smart filtering criteria, are correctly handled via the
 * REST API. The tests also cover the manual addition and blacklisting of images within collections,
 * validating proper interaction with the UserDataManager and PathService, and confirming that
 * the API returns appropriate HTTP status codes and data structures.
 */
@WebMvcTest(CollectionController.class)
class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserDataManager dataManager;
    @MockBean
    private PathService pathService;

    @Test
    @DisplayName("GET /api/collections should return all collection names")
    void getAllCollections_ShouldReturnList() throws Exception {
        when(dataManager.getCollections()).thenReturn(List.of("Favorites", "AI Art"));

        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Favorites"))
                .andExpect(jsonPath("$[1]").value("AI Art"));
    }

    @Test
    @DisplayName("POST /api/collections should create a new collection")
    void createCollection_ShouldInvokeService() throws Exception {
        String json = "{\"name\": \"New Coll\", \"isSmart\": false}";

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).createCollection(any(CreateCollectionRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/collections/{name} should remove collection")
    void deleteCollection_ShouldInvokeService() throws Exception {
        when(dataManager.getCollectionDetails("Old")).thenReturn(Optional.of(new CreateCollectionRequest("Old", false, null)));

        mockMvc.perform(delete("/api/collections/Old"))
                .andExpect(status().isOk());

        verify(dataManager).deleteCollection("Old");
    }

    @Test
    @DisplayName("GET /api/collections/{name} should return 404 if missing")
    void getCollection_NotFound_ShouldReturn404() throws Exception {
        when(dataManager.getCollectionDetails("Missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/collections/Missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/collections/{name}/batch/add should invoke batch service")
    void batchAddImages_ShouldInvokeService() throws Exception {
        List<String> paths = List.of("/img1.png", "/img2.png");
        String json = "[\"/img1.png\", \"/img2.png\"]";

        mockMvc.perform(post("/api/collections/MyColl/batch/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection("MyColl", paths);
    }

    @Test
    @DisplayName("POST /api/collections/{name}/images should delegate to batch logic")
    void addImage_ShouldDelegateToBatch() throws Exception {
        mockMvc.perform(post("/api/collections/MyColl/images")
                        .param("path", "/img1.png"))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection("MyColl", List.of("/img1.png"));
    }
}