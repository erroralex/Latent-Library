package com.nilsson.backend.controller;

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

    @PostMapping
    public ResponseEntity<Void> createCollection(@RequestParam String name) {
        dataManager.createCollection(name);
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

    @GetMapping("/{name}/images")
    public ResponseEntity<List<String>> getCollectionImages(@PathVariable String name) {
        return ResponseEntity.ok(dataManager.getFilesFromCollection(name).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()));
    }
}