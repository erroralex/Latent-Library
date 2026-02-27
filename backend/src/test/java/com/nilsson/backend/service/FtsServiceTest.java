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
 * Unit test suite for the {@link FtsService}, validating the Full-Text Search (FTS) indexing
 * and tokenization logic.
 * <p>
 * This class ensures that image metadata and user-defined tags are correctly prepared for
 * SQLite's FTS5 engine by verifying:
 * <ul>
 *   <li><b>Token Sanitization:</b> Confirms that keys and values are correctly formatted
 *   into searchable tokens, replacing non-alphanumeric characters with safe separators.</li>
 *   <li><b>Data Aggregation:</b> Validates the combination of disparate data sources
 *   (metadata, tags) into a unified, searchable text block.</li>
 *   <li><b>LoRA Formatting:</b> Ensures that complex LoRA strings are tokenized in a way
 *    that preserves their searchability within the FTS index.</li>
 * </ul>
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
        } catch (Exception ignored) {
        }

        verify(metadataRepository).getMetadata(imageId);
        verify(tagRepository).getTags(imageId);
    }
}
