package com.nilsson.backend.controller;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing image collections.
 * <p>
 * This controller provides a full CRUD API for image collections, supporting both static (manual)
 * and smart (dynamic) groupings. It handles the persistence of collection definitions and the
 * association of images with those collections. For smart collections, it dynamically executes
 * metadata-based searches to resolve the current set of matching images.
 * <p>
 * Key functionalities:
 * - Collection CRUD: Create, read, update, and delete collection definitions.
 * - Image Association: Manually add images to static collections.
 * - Smart Resolution: Dynamically fetches images for smart collections based on stored filters.
 * - Performance Optimization: Returns {@code ImageDTO}s to minimize frontend network overhead.
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
    public ResponseEntity<List<String>> getAllCollections() {
        return ResponseEntity.ok(dataManager.getCollections());
    }

    @GetMapping("/{name}")
    public ResponseEntity<CreateCollectionRequest> getCollectionDetails(@PathVariable String name) {
        return dataManager.getCollectionDetails(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Void> createCollection(@RequestBody CreateCollectionRequest request) {
        dataManager.createCollection(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{name}")
    public ResponseEntity<Void> updateCollection(@PathVariable String name, @RequestBody CreateCollectionRequest request) {
        dataManager.updateCollection(request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteCollection(@PathVariable String name) {
        dataManager.deleteCollection(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/images")
    public ResponseEntity<Void> addImageToCollection(@PathVariable String name, @RequestParam String path) {
        try {
            File file = pathService.resolve(path);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            dataManager.addImageToCollection(name, file);
            return ResponseEntity.ok().build();
        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/static-images")
    public ResponseEntity<List<String>> getStaticCollectionImages(@RequestParam String name) {
        return ResponseEntity.ok(dataManager.getFilesFromCollection(name).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()));
    }

    @PostMapping("/images")
    public ResponseEntity<List<ImageDTO>> getSmartCollectionImages(@RequestBody Map<String, String> requestBody) {
        String name = requestBody.get("name");
        if (name == null) {
            return ResponseEntity.badRequest().build();
        }

        return dataManager.getCollectionDetails(name)
                .map(details -> {
                    if (details.isSmart() && details.filters() != null) {
                        Map<String, String> filtersMap = new HashMap<>();
                        if (details.filters().models() != null && !details.filters().models().isEmpty()) {
                            filtersMap.put("Model", details.filters().models().get(0));
                        }
                        if (details.filters().loras() != null && !details.filters().loras().isEmpty()) {
                            filtersMap.put("Loras", details.filters().loras().get(0));
                        }
                        if (details.filters().samplers() != null && !details.filters().samplers().isEmpty()) {
                            filtersMap.put("Sampler", details.filters().samplers().get(0));
                        }
                        if (details.filters().rating() != null && !details.filters().rating().isBlank()) {
                            filtersMap.put("Rating", details.filters().rating());
                        }

                        String prompt = details.filters().prompt() != null ? String.join(" ", details.filters().prompt()) : "";

                        return ResponseEntity.ok(dataManager.findFilesWithFilters(prompt, filtersMap, 0, 2000).join());
                    } else {
                        List<ImageDTO> result = dataManager.getFilesFromCollection(name).stream()
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
                        return ResponseEntity.ok(result);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
