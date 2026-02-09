package com.nilsson.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Controller for the Metadata Scrubber tool.
 * Handles image uploads, preview generation, and metadata stripping (scrubbing).
 */
@RestController
@RequestMapping("/api/scrub")
public class ScrubController {

    private final Path tempDir = Paths.get("data", "temp");

    public ScrubController() {
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize temp storage", e);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path targetPath = tempDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath);
            return ResponseEntity.ok(filename);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to upload file: " + e.getMessage());
        }
    }

    @GetMapping("/preview/{filename}")
    public ResponseEntity<Resource> getPreview(@PathVariable String filename) {
        try {
            Path file = tempDir.resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Resource> processImage(@RequestParam("filename") String filename) {
        try {
            Path sourcePath = tempDir.resolve(filename);
            if (!Files.exists(sourcePath)) {
                return ResponseEntity.notFound().build();
            }

            BufferedImage image = ImageIO.read(sourcePath.toFile());

            String cleanFilename = "clean_" + filename;
            Path targetPath = tempDir.resolve(cleanFilename);
            
            String format = "png";
            if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                format = "jpg";
            }

            ImageIO.write(image, format, targetPath.toFile());

            Resource resource = new UrlResource(targetPath.toUri());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cleanFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
