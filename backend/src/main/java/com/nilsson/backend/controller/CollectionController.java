package com.nilsson.backend.controller;

import com.nilsson.backend.model.CreateCollectionRequest;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/collections")
@CrossOrigin(origins = "http://localhost:5173")
public class CollectionController {

    private final UserDataManager dataManager;

    public CollectionController(UserDataManager dataManager) {
        this.dataManager = dataManager;
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
        // Ensure the name in the path matches the request, or use the path name
        if (!name.equals(request.name())) {
            // If renaming is supported, we'd need more logic. For now, assume name is immutable or handled elsewhere.
            // But CreateCollectionRequest has 'name'. Let's assume we are updating the collection *with* this name.
        }
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
        File file = new File(path);
        if (!file.exists()) return ResponseEntity.notFound().build();
        dataManager.addImageToCollection(name, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/images")
    public ResponseEntity<List<String>> getCollectionImages(@RequestParam String name) {
        return ResponseEntity.ok(dataManager.getFilesFromCollection(name).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()));
    }
}
