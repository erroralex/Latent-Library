package com.nilsson.backend.service;

import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for the {@link TagService}, validating the business logic and validation
 * rules for user-defined image tagging.
 * <p>
 * This class ensures the integrity of the tagging system by verifying:
 * <ul>
 *   <li><b>Input Sanitization:</b> Confirms that tags are correctly trimmed and validated
 *   before being persisted to the database.</li>
 *   <li><b>Search Integration:</b> Validates that the Full-Text Search (FTS) index is
 *   automatically updated whenever tags are added or removed.</li>
 *   <li><b>Validation Rules:</b> Ensures that invalid inputs (e.g., empty tags, negative
 *   image IDs) correctly trigger {@link ValidationException}.</li>
 *   <li><b>Repository Delegation:</b> Confirms that the service correctly interacts with
 *   the {@link TagRepository} for data retrieval and persistence.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private FtsService ftsService;

    @InjectMocks
    private TagService tagService;

    @Test
    @DisplayName("addTag should trim tag and update FTS")
    void addTag_ShouldSanitizeAndUpdate() {
        int imageId = 10;
        String rawTag = "  AI Art  ";

        tagService.addTag(imageId, rawTag);

        verify(tagRepository).addTag(imageId, "AI Art");
        verify(ftsService).updateFtsIndex(imageId);
    }

    @Test
    @DisplayName("addTag should throw exception for invalid input")
    void addTag_ShouldValidate() {
        assertThrows(ValidationException.class, () -> tagService.addTag(-1, "tag"));
        assertThrows(ValidationException.class, () -> tagService.addTag(1, ""));
        assertThrows(ValidationException.class, () -> tagService.addTag(1, null));
    }

    @Test
    @DisplayName("removeTag should update FTS")
    void removeTag_ShouldUpdateFTS() {
        int imageId = 10;
        String tag = "old_tag";

        tagService.removeTag(imageId, tag);

        verify(tagRepository).removeTag(imageId, tag);
        verify(ftsService).updateFtsIndex(imageId);
    }

    @Test
    @DisplayName("getTags should delegate to repository")
    void getTags_ShouldDelegate() {
        int imageId = 5;
        Set<String> expected = Set.of("tag1", "tag2");
        when(tagRepository.getTags(imageId)).thenReturn(expected);

        Set<String> actual = tagService.getTags(imageId);

        assertEquals(expected, actual);
        verify(tagRepository).getTags(imageId);
    }
}
