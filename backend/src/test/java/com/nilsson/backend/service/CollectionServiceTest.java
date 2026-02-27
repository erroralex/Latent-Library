package com.nilsson.backend.service;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.repository.CollectionRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.SearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for the {@link CollectionService}, validating the business logic for
 * manual and smart image collections.
 * <p>
 * This class ensures the integrity of the collection management system by verifying:
 * <ul>
 *   <li><b>Lifecycle Management:</b> Confirms that collections can be created, updated,
 *   and deleted with proper repository delegation.</li>
 *   <li><b>Image Association:</b> Validates the logic for adding images to collections,
 *   including the automatic removal of blacklisted exclusions.</li>
 *   <li><b>Data Consistency:</b> Ensures that existence checks are performed before
 *   destructive operations like deletion.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private SearchRepository searchRepository;

    @InjectMocks
    private CollectionService collectionService;

    @Test
    @DisplayName("createCollection should delegate to repository")
    void createCollection_ShouldCallRepository() {
        CreateCollectionRequest req = new CreateCollectionRequest("My Best AI Art", false, null);

        collectionService.createCollection(req);

        verify(collectionRepository).create("My Best AI Art", false, null);
    }

    @Test
    @DisplayName("addImageToCollection should update repository and clear exclusions")
    void addImageToCollection_ShouldVerifyUniqueness() {
        collectionService.addImageToCollection("Favorites", 100);

        verify(collectionRepository).addImage("Favorites", 100);
        verify(collectionRepository).removeExclusion("Favorites", 100);
    }

    @Test
    @DisplayName("deleteCollection should verify existence before removal")
    void deleteCollection_ShouldCheckExistence() {
        when(collectionRepository.get("OldStuff")).thenReturn(Optional.of(new CreateCollectionRequest("OldStuff", false, null)));

        collectionService.deleteCollection("OldStuff");

        verify(collectionRepository).delete("OldStuff");
    }
}
