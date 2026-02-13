package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ImageProcessingException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.service.PathService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * REST Controller for the Metadata Scrubber utility.
 */
@RestController
@RequestMapping("/api/scrub")
public class ScrubController {

    private final Path tempDir;
    private final PathService pathService;

    public ScrubController(@Value("${app.data.dir:.}") String appDataDir, PathService pathService) {
        this.pathService = pathService;
        this.tempDir = Paths.get(appDataDir).resolve("data/temp").toAbsolutePath().normalize();
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new ApplicationException("Could not initialize temp storage: " + e.getMessage());
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
            throw new ApplicationException("Failed to upload file: " + e.getMessage());
        }
    }

    @GetMapping("/preview/{filename}")
    public ResponseEntity<Resource> getPreview(@PathVariable String filename) {
        try {
            // Validate filename to prevent traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                throw new ValidationException("Invalid filename");
            }
            
            Path file = tempDir.resolve(filename).normalize();
            if (!file.startsWith(tempDir)) {
                 throw new ValidationException("Access denied");
            }

            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                throw new ResourceNotFoundException("Preview image", filename);
            }
        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid filename format: " + filename);
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Resource> processImage(@RequestParam("filename") String filename) {
        // Validate filename
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ValidationException("Invalid filename");
        }

        Path sourcePath = tempDir.resolve(filename).normalize();
        if (!sourcePath.startsWith(tempDir)) {
             throw new ValidationException("Access denied");
        }

        if (!Files.exists(sourcePath)) {
            throw new ResourceNotFoundException("Source image", filename);
        }

        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image == null) {
                throw new ImageProcessingException("Failed to decode image. File may be corrupted.");
            }

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
            throw new ImageProcessingException("Error scrubbing metadata from image: " + e.getMessage(), e);
        }
    }
}
