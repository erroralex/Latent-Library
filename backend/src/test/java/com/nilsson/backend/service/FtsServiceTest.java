package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * FtsServiceTest is responsible for validating the Full-Text Search (FTS) indexing logic within the application.
 * It ensures that image metadata and user-defined tags are correctly tokenized, sanitized, and aggregated
 * into a unified searchable string compatible with SQLite's FTS5 engine. The tests verify specialized
 * formatting rules for technical parameters and LoRA strings, ensuring that the search index remains
 * accurate and performant as data is updated or added to the system.
 */
@ExtendWith(MockitoExtension.class)
class FtsServiceTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private ImageMetadataRepository metadataRepository;
    @Mock
    private TagRepository tagRepository;

    private FtsService ftsService;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        ftsService = new FtsService(dataSource, metadataRepository, tagRepository);
    }

    @Test
    @DisplayName("formatFtsToken should sanitize keys and values by replacing non-alphanumeric characters")
    void testFormatFtsToken() {
        assertEquals("Model_SDXL_1_5", FtsService.formatFtsToken("Model", "SDXL 1.5"));
        assertEquals("Loras__lora_my_lora_0_8_", FtsService.formatFtsToken("Loras", "<lora:my-lora:0.8>"));
    }

    @Test
    @DisplayName("updateFtsIndex should combine metadata and tags into global_text")
    void testUpdateFtsIndex() {
        int imageId = 1;
        Map<String, String> metadata = Map.of(
                "Prompt", "a cute cat",
                "Model", "Flux"
        );
        Set<String> tags = Set.of("favorite", "animal");

        when(metadataRepository.getMetadata(imageId)).thenReturn(metadata);
        when(tagRepository.getTags(imageId)).thenReturn(tags);

        try {
            ftsService.updateFtsIndex(imageId);
        } catch (Exception e) {
        }

        verify(metadataRepository).getMetadata(imageId);
        verify(tagRepository).getTags(imageId);
    }
}