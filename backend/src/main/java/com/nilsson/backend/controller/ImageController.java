package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ImageProcessingException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.model.ImageDTO;
import com.nilsson.backend.model.UpdateMetadataRequest;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.ThumbnailService;
import com.nilsson.backend.service.UserDataManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller for managing image-specific operations, metadata retrieval, and content serving.
 * <p>
 * This controller provides the primary API for interacting with individual images within the library.
 * It handles complex search queries with multiple filters, retrieves detailed metadata (including
 * AI-generated tags and user-defined overrides), manages user ratings, and facilitates file
 * operations like renaming and batch deletion. Additionally, it serves both full-resolution
 * image content and optimized thumbnails with appropriate cache control headers.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Advanced Search:</b> Executes filtered searches across the image library, supporting
 *   pagination and multi-criteria filtering (model, sampler, lora, rating, etc.).</li>
 *   <li><b>Metadata Retrieval:</b> Aggregates cached metadata, AI tags, and user-defined
 *   notes/overrides for a specific image path.</li>
 *   <li><b>User Interactions:</b> Provides endpoints for updating image ratings, renaming files,
 *   and modifying custom metadata fields.</li>
 *   <li><b>Batch Operations:</b> Supports the deletion of multiple images in a single request.</li>
 *   <li><b>Content Serving:</b> Streams image files and thumbnails to the client, implementing
 *   aggressive caching for performance.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final UserDataManager dataManager;
    private final PathService pathService;
    private final ThumbnailService thumbnailService;
    private final JdbcClient jdbcClient;

    public ImageController(UserDataManager dataManager, PathService pathService, ThumbnailService thumbnailService, DataSource dataSource) {
        this.dataManager = dataManager;
        this.pathService = pathService;
        this.thumbnailService = thumbnailService;
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ImageDTO>> searchImages(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String sampler,
            @RequestParam(required = false) String lora,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) Boolean includeAiTags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Map<String, String> filters = new HashMap<>();
        if (StringUtils.hasText(model) && !"All".equals(model)) filters.put("Model", model);
        if (StringUtils.hasText(sampler) && !"All".equals(sampler)) filters.put("Sampler", sampler);
        if (StringUtils.hasText(lora) && !"All".equals(lora)) filters.put("Loras", lora);
        if (StringUtils.hasText(rating)) filters.put("Rating", rating);
        if (StringUtils.hasText(collection)) filters.put("Collection", collection);
        if (includeAiTags != null) filters.put("includeAiTags", String.valueOf(includeAiTags));

        int offset = page * size;
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

        Map<String, Object> customData = jdbcClient.sql("SELECT id, ai_tags, user_notes, custom_prompt, custom_negative_prompt, custom_model FROM images WHERE file_path = ?")
                .param(pathService.getNormalizedAbsolutePath(file))
                .query((rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("ai_tags", rs.getString("ai_tags") != null ? rs.getString("ai_tags") : "");
                    map.put("user_notes", rs.getString("user_notes") != null ? rs.getString("user_notes") : "");
                    map.put("custom_prompt", rs.getString("custom_prompt") != null ? rs.getString("custom_prompt") : "");
                    map.put("custom_negative_prompt", rs.getString("custom_negative_prompt") != null ? rs.getString("custom_negative_prompt") : "");
                    map.put("custom_model", rs.getString("custom_model") != null ? rs.getString("custom_model") : "");
                    return map;
                }).optional().orElse(Map.of());

        response.putAll(customData);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/metadata")
    public ResponseEntity<Void> updateMetadata(@PathVariable int id, @RequestBody UpdateMetadataRequest request) {
        dataManager.updateCustomMetadata(id, request);
        return ResponseEntity.ok().build();
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

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        try {
            Resource resource = new UrlResource(file.toURI());

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
            throw new ValidationException("Invalid path format: " + path);
        } catch (IOException e) {
            throw new ImageProcessingException("Failed to read image file: " + path, e);
        }
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@RequestParam String path) {
        validatePath(path);
        File file = pathService.resolve(path);

        if (!file.exists()) {
            throw new ResourceNotFoundException("Image", path);
        }

        File thumbnail = thumbnailService.getThumbnail(file);

        try {
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
            throw new ValidationException("Invalid path format for thumbnail: " + path);
        }
    }

    private void validatePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ValidationException("Path parameter cannot be empty.");
        }
    }
}
