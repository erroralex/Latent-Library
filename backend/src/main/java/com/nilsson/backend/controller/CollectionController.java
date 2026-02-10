package com.nilsson.backend.controller;

import com.nilsson.backend.model.CreateCollectionRequest;
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

@RestController
@RequestMapping("/api/collections")
@CrossOrigin(origins = "http://localhost:5173")
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
    public ResponseEntity<List<String>> getSmartCollectionImages(@RequestBody Map<String, String> requestBody) {
        String name = requestBody.get("name");
        if (name == null) {
            return ResponseEntity.badRequest().build();
        }

        return dataManager.getCollectionDetails(name)
                .map(details -> {
                    if (details.isSmart() && details.filters() != null) {
                        // Adapt to the existing UserDataManager API which expects Map<String, String>
                        Map<String, String> filtersMap = new HashMap<>();
                        if (details.filters().models() != null && !details.filters().models().isEmpty()) {
                            filtersMap.put("Model", details.filters().models().get(0)); // Use only the first value
                        }
                        if (details.filters().loras() != null && !details.filters().loras().isEmpty()) {
                            filtersMap.put("Loras", details.filters().loras().get(0)); // Use only the first value
                        }
                        if (details.filters().samplers() != null && !details.filters().samplers().isEmpty()) {
                            filtersMap.put("Sampler", details.filters().samplers().get(0)); // Use only the first value
                        }
                        if (details.filters().rating() != null && !details.filters().rating().isBlank()) {
                            filtersMap.put("Rating", details.filters().rating());
                        }

                        String prompt = details.filters().prompt() != null ? String.join(" ", details.filters().prompt()) : "";

                        // Use default offset 0 and limit 2000 for smart collections for now
                        List<String> result = dataManager.findFilesWithFilters(prompt, filtersMap, 0, 2000).join().stream()
                                .map(File::getAbsolutePath)
                                .collect(Collectors.toList());
                        return ResponseEntity.ok(result);
                    } else {
                        // Fallback for non-smart collections
                        List<String> result = dataManager.getFilesFromCollection(name).stream()
                                .map(File::getAbsolutePath)
                                .collect(Collectors.toList());
                        return ResponseEntity.ok(result);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
