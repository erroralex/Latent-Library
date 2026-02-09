package com.nilsson.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @PostMapping("/open-folder")
    public ResponseEntity<String> openFolder(@RequestParam("path") String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.badRequest().body("Folder does not exist");
        }

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(folder);
                return ResponseEntity.ok("Opened");
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Failed to open folder: " + e.getMessage());
            }
        }
        return ResponseEntity.internalServerError().body("Desktop API not supported");
    }
}
