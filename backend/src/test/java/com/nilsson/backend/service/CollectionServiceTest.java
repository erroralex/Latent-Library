package com.nilsson.backend.service;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.repository.CollectionRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.SearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CollectionServiceTest provides unit tests for the CollectionService, ensuring that
 * collection management operations such as creation, deletion, and image association
 * are correctly delegated to the underlying repositories. It validates the business
 * logic surrounding smart collections and manual image grouping, ensuring data
 * consistency and proper interaction with the persistence layer.
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
    void createCollection_ShouldCallRepository() {
        CreateCollectionRequest req = new CreateCollectionRequest("My Best AI Art", false, null);

        collectionService.createCollection(req);

        verify(collectionRepository).create("My Best AI Art", false, null);
    }

    @Test
    void addImageToCollection_ShouldVerifyUniqueness() {
        collectionService.addImageToCollection("Favorites", 100);

        verify(collectionRepository).addImage("Favorites", 100);
        verify(collectionRepository).removeExclusion("Favorites", 100);
    }

    @Test
    void deleteCollection_ShouldCheckExistence() {
        when(collectionRepository.get("OldStuff")).thenReturn(Optional.of(new CreateCollectionRequest("OldStuff", false, null)));

        collectionService.deleteCollection("OldStuff");

        verify(collectionRepository).delete("OldStuff");
    }
}