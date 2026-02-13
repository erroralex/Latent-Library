package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ImageProcessingException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.ThumbnailService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        if (StringUtils.hasText(model) && !"All".equals(model)) filters.put("Model", model);
        if (StringUtils.hasText(sampler) && !"All".equals(sampler)) filters.put("Sampler", sampler);
        if (StringUtils.hasText(lora) && !"All".equals(lora)) filters.put("Loras", lora);
        if (StringUtils.hasText(rating)) filters.put("Rating", rating);
        if (StringUtils.hasText(collection)) filters.put("Collection", collection);

        int offset = page * size;
        // If dataManager throws an error, the GlobalExceptionHandler will catch it.
        List<ImageDTO> results = dataManager.findFilesWithFilters(query, filters, offset, size).join();
        return ResponseEntity.ok(results);
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
        validatePath(path);
        File file = pathService.resolve(path);

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        Map<String, String> meta = dataManager.getCachedMetadata(file);
        int rating = dataManager.getRating(file);

        Map<String, Object> response = new HashMap<>(meta);
        response.put("rating", rating);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/rating")
    public ResponseEntity<Void> setRating(@RequestParam String path, @RequestParam int rating) {
        validatePath(path);
        File file = pathService.resolve(path);

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        dataManager.setRating(file, rating);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/rename")
    public ResponseEntity<Void> renameImage(@RequestParam String path, @RequestParam String newName) {
        validatePath(path);
        File file = pathService.resolve(path);

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        dataManager.renameFile(file, newName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/delete")
    public ResponseEntity<Void> batchDeleteImages(@RequestBody List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ValidationException("Path list cannot be empty.");
        }
        dataManager.batchDeleteFiles(paths);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/content")
    public ResponseEntity<Resource> getImageContent(@RequestParam String path) {
        validatePath(path);
        File file = pathService.resolve(path);

        // 1. Explicit Check (Throws 404 immediately if missing)
        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        try {
            Resource resource = new UrlResource(file.toURI());

            // 2. IO Operations (Checked Exceptions)
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentLength(file.length())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            // 400 Bad Request
            throw new ValidationException("Invalid path format: " + path);
        } catch (IOException e) {
            // 500 Internal Server Error (Wrapped)
            throw new ImageProcessingException("Failed to read image file: " + path, e);
        }
        // Do NOT catch generic Exception here, let RuntimeExceptions bubble up!
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@RequestParam String path) {
        validatePath(path);
        File file = pathService.resolve(path);

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        try {
            File thumbnail = thumbnailService.getThumbnail(file);

            // Fallback logic
            if (thumbnail == null || !thumbnail.exists()) {
                // Recursively call getImageContent (safe because we checked file.exists above)
                return getImageContent(path);
            }

            Resource resource = new UrlResource(thumbnail.toURI());

            return ResponseEntity.ok()
                    .contentLength(thumbnail.length())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);

        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid path format for thumbnail: " + path);
        }
    }

    private void validatePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ValidationException("Path parameter cannot be empty.");
        }
    }
}