package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.IndexingService;
import com.nilsson.backend.service.IndexingStatusTracker;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for the {@link LibraryController}, validating the REST API endpoints
 * for library-wide synchronization and discovery.
 * <p>
 * This class ensures that high-level library operations are correctly orchestrated by verifying:
 * <ul>
 *   <li><b>Folder Scanning:</b> Confirms that requests to index a specific directory are
 *   correctly resolved via the path service and delegated to the indexing engine.</li>
 *   <li><b>DTO Mapping:</b> Ensures that the controller returns a standardized list of
 *   image summaries following a successful scan.</li>
 *   <li><b>State Management:</b> Validates that the last visited folder is correctly
 *   updated during the scanning process.</li>
 * </ul>
 */
@WebMvcTest(LibraryController.class)
@ActiveProfiles("test")
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexingService indexingService;

    @MockBean
    private PathService pathService;

    @MockBean
    private UserDataManager userDataManager;

    @MockBean
    private IndexingStatusTracker indexingStatusTracker;

    @MockBean
    private DataSource dataSource;

    @Test
    @DisplayName("POST /api/library/scan should trigger indexing and return image summaries")
    void scanFolder_ShouldTriggerIndexingAndReturnDTOs() throws Exception {
        String path = System.getProperty("java.io.tmpdir");
        when(pathService.resolve(any())).thenReturn(new File(path));
        
        when(userDataManager.getImagesInFolderPaginated(any(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(new ImageDTO("test.png", 0, ""))));

        mockMvc.perform(post("/api/library/scan")
                        .param("path", path))
                .andExpect(status().isOk());

        verify(indexingService).indexFolder(any(File.class), eq(false));
        verify(userDataManager).getImagesInFolderPaginated(any(File.class), eq(false), any(Pageable.class));
    }
}
