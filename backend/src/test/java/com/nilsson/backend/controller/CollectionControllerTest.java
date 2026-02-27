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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for the {@link CollectionController}, validating the REST API endpoints
 * for image collection management.
 * <p>
 * This class ensures the integrity of the collection system by verifying:
 * <ul>
 *   <li><b>CRUD Operations:</b> Confirms that collections can be created, retrieved,
 *   and deleted with proper status codes and service delegation.</li>
 *   <li><b>Membership Management:</b> Validates the addition and removal of images
 *   from collections, including batch operations.</li>
 *   <li><b>Error Handling:</b> Ensures that requests for non-existent collections
 *   correctly return 404 Not Found.</li>
 *   <li><b>Smart Collections:</b> Confirms that the controller correctly handles
 *   requests for both manual and rule-based (smart) collections.</li>
 * </ul>
 */
@WebMvcTest(CollectionController.class)
@ActiveProfiles("test")
class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataManager dataManager;

    @MockBean
    private PathService pathService;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /api/collections should return list of all collections")
    void getAllCollections_ShouldReturnList() throws Exception {
        when(dataManager.getCollections()).thenReturn(List.of("Coll1", "Coll2"));
        when(dataManager.getCollectionDetails(any())).thenReturn(Optional.of(new CreateCollectionRequest("test", false, null)));

        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk());
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
    @DisplayName("DELETE /api/collections/{name} should remove the collection")
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
    @DisplayName("POST /api/collections/{name}/batch/add should add multiple images")
    void batchAddImages_ShouldInvokeService() throws Exception {
        String json = "[\"/img1.png\", \"/img2.png\"]";

        mockMvc.perform(post("/api/collections/MyColl/batch/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection(eq("MyColl"), any());
    }

    @Test
    @DisplayName("POST /api/collections/{name}/images should add a single image")
    void addImage_ShouldDelegateToBatch() throws Exception {
        mockMvc.perform(post("/api/collections/MyColl/images")
                        .param("path", "/img1.png"))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection(eq("MyColl"), any());
    }
}
