package com.nilsson.backend.controller;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
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
 * CollectionControllerTest provides unit tests for the CollectionController, focusing on the
 * REST API endpoints for managing image collections. It verifies the full CRUD lifecycle
 * of collections, including creation, retrieval of details, and deletion. The tests
 * also cover membership management, such as adding images to collections in batches
 * or individually, ensuring that the controller correctly interacts with the
 * UserDataManager. MockMvc is used to simulate HTTP requests and verify
 * response statuses.
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
    void getAllCollections_ShouldReturnList() throws Exception {
        when(dataManager.getCollections()).thenReturn(List.of("Coll1", "Coll2"));
        when(dataManager.getCollectionDetails(any())).thenReturn(Optional.of(new CreateCollectionRequest("test", false, null)));

        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk());
    }

    @Test
    void createCollection_ShouldInvokeService() throws Exception {
        String json = "{\"name\": \"New Coll\", \"isSmart\": false}";

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).createCollection(any(CreateCollectionRequest.class));
    }

    @Test
    void deleteCollection_ShouldInvokeService() throws Exception {
        when(dataManager.getCollectionDetails("Old")).thenReturn(Optional.of(new CreateCollectionRequest("Old", false, null)));

        mockMvc.perform(delete("/api/collections/Old"))
                .andExpect(status().isOk());

        verify(dataManager).deleteCollection("Old");
    }

    @Test
    void getCollection_NotFound_ShouldReturn404() throws Exception {
        when(dataManager.getCollectionDetails("Missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/collections/Missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchAddImages_ShouldInvokeService() throws Exception {
        String json = "[\"/img1.png\", \"/img2.png\"]";

        mockMvc.perform(post("/api/collections/MyColl/batch/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection(eq("MyColl"), any());
    }

    @Test
    void addImage_ShouldDelegateToBatch() throws Exception {
        mockMvc.perform(post("/api/collections/MyColl/images")
                        .param("path", "/img1.png"))
                .andExpect(status().isOk());

        verify(dataManager).addImagesToCollection(eq("MyColl"), any());
    }
}
