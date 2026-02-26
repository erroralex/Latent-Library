package com.nilsson.backend.service;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.repository.CollectionRepository;
import com.nilsson.backend.repository.ImageRepository;
import com.nilsson.backend.repository.SearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for managing image collections, including both static and dynamic "smart" collections.
 * <p>
 * This service provides the business logic for creating, updating, and deleting collections. It handles
 * the orchestration between the collection repository and the search repository to facilitate
 * "Smart Collections"—dynamic groupings of images based on metadata filters (e.g., specific models,
 * samplers, or ratings).
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Collection Lifecycle:</b> Manages the persistence and retrieval of collection definitions.</li>
 *   <li><b>Smart Population:</b> Executes complex search queries to automatically populate collections
 *   based on user-defined criteria.</li>
 *   <li><b>Manual Overrides:</b> Supports explicit addition, removal, and blacklisting of images
 *   within collections, allowing for fine-grained user control.</li>
 *   <li><b>Atomic Updates:</b> Ensures database consistency during collection modifications using
 *   Spring's transactional management.</li>
 * </ul>
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
        if (name == null || name.isBlank()) {
            throw new ValidationException("Collection name cannot be empty.");
        }
        return collectionRepository.get(name);
    }

    @Transactional
    public void createCollection(CreateCollectionRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Invalid collection creation request.");
        }
        collectionRepository.create(request.name(), request.isSmart(), request.filters());
        populateSmartCollection(request);
    }

    @Transactional
    public void updateCollection(String oldName, CreateCollectionRequest request) {
        if (oldName == null || oldName.isBlank()) {
            throw new ValidationException("Original collection name is required for update.");
        }
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Updated collection request is invalid.");
        }

        if (collectionRepository.get(oldName).isEmpty()) {
            throw new ResourceNotFoundException("Collection", oldName);
        }

        collectionRepository.update(oldName, request.name(), request.isSmart(), request.filters());
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

            List<String> matchingPaths = searchRepository.findPaths(query, searchFilters, null, 0, 2000);

            for (String path : matchingPaths) {
                int id = imageRepository.getIdByPath(path);
                if (id > 0) {
                    collectionRepository.addSmartImage(request.name(), id);
                }
            }
        }
    }

    public void deleteCollection(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Collection name is required for deletion.");
        }
        if (collectionRepository.get(name).isEmpty()) {
            throw new ResourceNotFoundException("Collection", name);
        }
        collectionRepository.delete(name);
    }

    public void addImageToCollection(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID.");
        }

        collectionRepository.addImage(collectionName, imageId);
        collectionRepository.removeExclusion(collectionName, imageId);
    }

    @Transactional
    public void addImagesToCollection(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        collectionRepository.addImages(collectionName, imageIds);
        collectionRepository.removeExclusions(collectionName, imageIds);
    }

    @Transactional
    public void removeImagesFromCollection(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        collectionRepository.removeImages(collectionName, imageIds);
    }

    public void blacklistImageFromCollection(String collectionName, int imageId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID.");
        }

        collectionRepository.addExclusion(collectionName, imageId);
    }

    @Transactional
    public void blacklistImagesFromCollection(String collectionName, List<Integer> imageIds) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        collectionRepository.addExclusions(collectionName, imageIds);
    }

    @Transactional
    public List<String> getFilePathsFromCollection(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }

        Optional<CreateCollectionRequest> details = collectionRepository.get(collectionName);
        if (details.isEmpty()) {
            throw new ResourceNotFoundException("Collection", collectionName);
        }

        if (details.get().isSmart()) {
            collectionRepository.removeAllImages(collectionName);
            populateSmartCollection(details.get());
        }
        return collectionRepository.getFilePaths(collectionName);
    }
}
