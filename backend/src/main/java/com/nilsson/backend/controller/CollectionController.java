package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.CollectionDTO;
import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing image collections, supporting both static and dynamic groupings.
 * <p>
 * This controller provides the API for the application's collection management system. It
 * supports the full CRUD lifecycle for collections, including the definition of "Smart
 * Collections" via metadata filters. It also handles the association of images with
 * collections, batch additions, and the management of blacklisted exclusions.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Collection Lifecycle:</b> Manages the creation, retrieval, update, and deletion
 *   of collection entities.</li>
 *   <li><b>Membership Management:</b> Facilitates adding individual or batches of images
 *   to static collections.</li>
 *   <li><b>Smart Population:</b> Provides endpoints for retrieving images that match a
 *   collection's dynamic filter criteria.</li>
 *   <li><b>Exclusion Handling:</b> Allows for the blacklisting of specific images from
 *   appearing in a collection.</li>
 *   <li><b>Relational Mapping:</b> Transforms database records into enriched {@link ImageDTO}
 *   objects for frontend display.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final UserDataManager dataManager;
    private final PathService pathService;

    public CollectionController(UserDataManager dataManager, PathService pathService) {
        this.dataManager = dataManager;
        this.pathService = pathService;
    }

    @GetMapping
    public ResponseEntity<List<CollectionDTO>> getAllCollections() {
        List<String> names = dataManager.getCollections();
        List<CollectionDTO> dtos = names.stream().map(name -> {
            CreateCollectionRequest details = dataManager.getCollectionDetails(name).orElseThrow();
            List<String> previews = dataManager.getFilesFromCollection(name).stream()
                    .limit(4)
                    .map(pathService::getNormalizedAbsolutePath)
                    .collect(Collectors.toList());
            return new CollectionDTO(name, details.isSmart(), previews);
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{name}")
    public ResponseEntity<CreateCollectionRequest> getCollectionDetails(@PathVariable String name) {
        CreateCollectionRequest collection = dataManager.getCollectionDetails(name)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", name));
        return ResponseEntity.ok(collection);
    }

    @PostMapping
    public ResponseEntity<Void> createCollection(@RequestBody CreateCollectionRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Collection name cannot be empty.");
        }
        dataManager.createCollection(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{name}")
    public ResponseEntity<Void> updateCollection(@PathVariable String name, @RequestBody CreateCollectionRequest request) {
        if (!dataManager.getCollectionDetails(name).isPresent()) {
            throw new ResourceNotFoundException("Collection", name);
        }
        dataManager.updateCollection(name, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteCollection(@PathVariable String name) {
        if (!dataManager.getCollectionDetails(name).isPresent()) {
            throw new ResourceNotFoundException("Collection", name);
        }
        dataManager.deleteCollection(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/images")
    public ResponseEntity<Void> addImageToCollection(@PathVariable String name, @RequestParam String path) {
        if (path == null || path.isBlank()) {
            throw new ValidationException("Path cannot be empty.");
        }
        dataManager.addImagesToCollection(name, List.of(path));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/batch/add")
    public ResponseEntity<Void> batchAddImagesToCollection(@PathVariable String name, @RequestBody List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ValidationException("Path list cannot be empty.");
        }
        dataManager.addImagesToCollection(name, paths);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/batch/remove")
    public ResponseEntity<Void> batchRemoveImagesFromCollection(@PathVariable String name, @RequestBody List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ValidationException("Path list cannot be empty.");
        }
        dataManager.removeImagesFromCollection(name, paths);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/blacklist")
    public ResponseEntity<Void> blacklistImageFromCollection(@PathVariable String name, @RequestParam String path) {
        try {
            File file = pathService.resolve(path);
            if (!file.exists()) {
                throw new ResourceNotFoundException("Image", path);
            }
            dataManager.blacklistImageFromCollection(name, file);
            return ResponseEntity.ok().build();
        } catch (InvalidPathException e) {
            throw new ValidationException("Invalid path format: " + path);
        }
    }

    @PostMapping("/{name}/batch/blacklist")
    public ResponseEntity<Void> batchBlacklistImagesFromCollection(@PathVariable String name, @RequestBody List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ValidationException("Path list cannot be empty.");
        }
        dataManager.blacklistImagesFromCollection(name, paths);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/static-images")
    public ResponseEntity<List<String>> getStaticCollectionImages(@RequestParam String name) {
        if (!dataManager.getCollectionDetails(name).isPresent()) {
            throw new ResourceNotFoundException("Collection", name);
        }
        return ResponseEntity.ok(dataManager.getFilesFromCollection(name).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()));
    }

    @PostMapping("/images")
    public ResponseEntity<List<ImageDTO>> getSmartCollectionImages(@RequestBody Map<String, String> requestBody) {
        String name = requestBody.get("name");
        if (name == null || name.isBlank()) {
            throw new ValidationException("Collection name is required.");
        }

        List<File> files = dataManager.getFilesFromCollection(name);

        List<ImageDTO> dtos = files.stream()
                .map(file -> {
                    int rating = dataManager.getRating(file);
                    String model = "";
                    if (dataManager.hasCachedMetadata(file)) {
                        Map<String, String> meta = dataManager.getCachedMetadata(file);
                        model = meta.getOrDefault("Model", "");
                    }
                    return new ImageDTO(pathService.getNormalizedAbsolutePath(file), rating, model);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
