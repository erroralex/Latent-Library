package com.nilsson.backend.controller;

import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.ThumbnailService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller for image-centric operations, metadata management, and high-performance content delivery.
 * <p>
 * This controller serves as the primary interface for interacting with individual images within the indexed library.
 * It leverages a combination of SQLite FTS5 for rapid searching and a dedicated metadata cache to provide
 * a responsive user experience. The controller handles complex filtering logic, user-driven ratings,
 * and the secure streaming of both raw image data and optimized thumbnails.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Advanced Search:</b> Provides a robust search API supporting full-text queries and structured filters
 *   for AI-specific metadata like Models, Samplers, and LoRAs.</li>
 *   <li><b>Metadata & Ratings:</b> Manages the retrieval of technical image metadata and allows users to
 *   persistently rate images, which are then integrated into the search index.</li>
 *   <li><b>Content Streaming:</b> Efficiently serves local image files as web resources, implementing
 *   aggressive HTTP caching (Immutable/Max-Age) to minimize redundant transfers.</li>
 *   <li><b>Thumbnail Management:</b> Interfaces with the {@link ThumbnailService} to deliver low-latency
 *   previews, falling back to original content only when necessary.</li>
 *   <li><b>Filter Discovery:</b> Exposes distinct metadata values present in the database to dynamically
 *   populate frontend UI components.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final UserDataManager dataManager;
    private final PathService pathService;
    private final ThumbnailService thumbnailService;

    public ImageController(UserDataManager dataManager, PathService pathService, ThumbnailService thumbnailService) {
        this.dataManager = dataManager;
        this.pathService = pathService;
        this.thumbnailService = thumbnailService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ImageDTO>> searchImages(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sampler,
            @RequestParam(required = false) String lora,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String collection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Map<String, String> filters = new HashMap<>();
        if (model != null && !model.isEmpty() && !"All".equals(model)) filters.put("Model", model);
        if (sampler != null && !sampler.isEmpty() && !"All".equals(sampler)) filters.put("Sampler", sampler);
        if (lora != null && !lora.isEmpty() && !"All".equals(lora)) filters.put("Loras", lora);

        if (rating != null && !rating.isEmpty()) {
            filters.put("Rating", rating);
        }
        
        if (collection != null && !collection.isEmpty()) {
            filters.put("Collection", collection);
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

            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "image/jpeg";
            }

            return ResponseEntity.ok()
                    .contentLength(file.length())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@RequestParam String path) {
        try {
            File file = pathService.resolve(path);
            if (!file.exists()) return ResponseEntity.notFound().build();

            File thumbnail = thumbnailService.getThumbnail(file);
            if (thumbnail == null || !thumbnail.exists()) {
                return getImageContent(path);
            }

            Resource resource = new UrlResource(thumbnail.toURI());

            return ResponseEntity.ok()
                    .contentLength(thumbnail.length())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
