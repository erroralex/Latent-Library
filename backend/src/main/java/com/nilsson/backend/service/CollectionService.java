package com.nilsson.backend.service;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.repository.CollectionRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing image collections and their dynamic population.
 * <p>
 * This service handles the business logic for both static and smart collections. For smart
 * collections, it orchestrates the resolution of metadata filters into a concrete set of
 * image associations. It ensures that smart collections are refreshed when their
 * definitions change or when their contents are accessed.
 * <p>
 * Key functionalities:
 * - Collection Lifecycle: Manages the creation, update, and deletion of collection entities.
 * - Smart Population: Translates high-level metadata filters into database-level image associations.
 * - Dynamic Refresh: Automatically re-populates smart collections to ensure they reflect the latest library state.
 * - Membership Management: Handles manual image additions to static collections.
 */
@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final ImageRepository imageRepository;
    private final SearchRepository searchRepository;

    public CollectionService(CollectionRepository collectionRepository, ImageRepository imageRepository, SearchRepository searchRepository) {
        this.collectionRepository = collectionRepository;
        this.imageRepository = imageRepository;
        this.searchRepository = searchRepository;
    }

    public List<String> getCollections() {
        return collectionRepository.getAllNames();
    }

    public Optional<CreateCollectionRequest> getCollectionDetails(String name) {
        return collectionRepository.get(name);
    }

    public void createCollection(CreateCollectionRequest request) {
        collectionRepository.create(request.name(), request.isSmart(), request.filters());
        populateSmartCollection(request);
    }

    public void updateCollection(CreateCollectionRequest request) {
        collectionRepository.update(request.name(), request.isSmart(), request.filters());
        if (request.isSmart()) {
            collectionRepository.removeAllImages(request.name());
            populateSmartCollection(request);
        }
    }

    private void populateSmartCollection(CreateCollectionRequest request) {
        if (request.isSmart() && request.filters() != null) {
            Map<String, List<String>> searchFilters = new HashMap<>();
            CreateCollectionRequest.CollectionFilters f = request.filters();

            if (f.models() != null && !f.models().isEmpty()) {
                searchFilters.put("Model", f.models());
            }
            if (f.samplers() != null && !f.samplers().isEmpty()) {
                searchFilters.put("Sampler", f.samplers());
            }
            if (f.loras() != null && !f.loras().isEmpty()) {
                searchFilters.put("Loras", f.loras());
            }
            if (f.rating() != null && !f.rating().isBlank()) {
                searchFilters.put("Rating", List.of(f.rating()));
            }

            String query = null;
            if (f.prompt() != null && !f.prompt().isEmpty()) {
                query = String.join(" ", f.prompt());
            }

            List<String> matchingPaths = searchRepository.findPaths(query, searchFilters, 0, 2000);

            for (String path : matchingPaths) {
                int id = imageRepository.getIdByPath(path);
                if (id > 0) {
                    collectionRepository.addImage(request.name(), id);
                }
            }
        }
    }

    public void deleteCollection(String name) {
        collectionRepository.delete(name);
    }

    public void addImageToCollection(String collectionName, int imageId) {
        if (collectionName != null && imageId > 0) {
            collectionRepository.addImage(collectionName, imageId);
        }
    }

    public List<String> getFilePathsFromCollection(String collectionName) {
        Optional<CreateCollectionRequest> details = collectionRepository.get(collectionName);
        if (details.isPresent() && details.get().isSmart()) {
            collectionRepository.removeAllImages(collectionName);
            populateSmartCollection(details.get());
        }
        return collectionRepository.getFilePaths(collectionName);
    }
}
