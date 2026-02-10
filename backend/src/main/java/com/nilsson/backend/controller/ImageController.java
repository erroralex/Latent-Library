package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for image-centric operations and content delivery.
 * <p>
 * This controller serves as the primary API for interacting with individual images and the indexed library.
 * It provides endpoints for high-performance searching (utilizing FTS5), metadata retrieval, and user
 * interactions such as star ratings. It also handles the streaming of raw image content to the frontend,
 * ensuring correct MIME types are detected and served.
 * <p>
 * Key functionalities:
 * - Advanced Search: Supports complex filtering by model, sampler, LoRA, and rating.
 * - Metadata Management: Provides access to both cached technical metadata and user-defined ratings.
 * - Content Streaming: Serves local image files as web-accessible resources.
 * - Filter Discovery: Exposes distinct metadata values to populate frontend filter menus.
 */
@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "http://localhost:5173")
public class ImageController {

    private final UserDataManager dataManager;
    private final PathService pathService;

    public ImageController(UserDataManager dataManager, PathService pathService) {
        this.dataManager = dataManager;
        this.pathService = pathService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ImageDTO>> searchImages(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sampler,
            @RequestParam(required = false) String lora,
            @RequestParam(required = false) String rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Map<String, String> filters = new HashMap<>();
        if (model != null && !model.isEmpty() && !"All".equals(model)) filters.put("Model", model);
        if (sampler != null && !sampler.isEmpty() && !"All".equals(sampler)) filters.put("Sampler", sampler);
        if (lora != null && !lora.isEmpty() && !"All".equals(lora)) filters.put("Loras", lora);

        if (rating != null && !rating.isEmpty()) {
            filters.put("Rating", rating);
        }

        int offset = page * size;

        return ResponseEntity.ok(dataManager.findFilesWithFilters(query, filters, offset, size).join());
    }

    @GetMapping("/filters")
    public ResponseEntity<Map<String, List<String>>> getFilters() {
        Map<String, List<String>> filters = new HashMap<>();
        filters.put("models", dataManager.getDistinctMetadataValues("Model"));
        filters.put("samplers", dataManager.getDistinctMetadataValues("Sampler"));
        filters.put("loras", dataManager.getDistinctMetadataValues("Loras"));
        return ResponseEntity.ok(filters);
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(@RequestParam String path) {
        File file = pathService.resolve(path);
        if (!file.exists()) return ResponseEntity.notFound().build();

        Map<String, String> meta = dataManager.getCachedMetadata(file);
        int rating = dataManager.getRating(file);

        Map<String, Object> response = new HashMap<>(meta);
        response.put("rating", rating);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/rating")
    public ResponseEntity<Void> setRating(@RequestParam String path, @RequestParam int rating) {
        File file = pathService.resolve(path);
        if (!file.exists()) return ResponseEntity.notFound().build();
        dataManager.setRating(file, rating);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/content")
    public ResponseEntity<Resource> getImageContent(@RequestParam String path) {
        try {
            File file = pathService.resolve(path);
            if (!file.exists()) return ResponseEntity.notFound().build();

            Resource resource = new UrlResource(file.toURI());
            String contentType = null;
            try {
                contentType = Files.probeContentType(file.toPath());
            } catch (Exception e) {
                // ignore
            }

            if (contentType == null) {
                contentType = "image/jpeg";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
