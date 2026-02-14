package com.nilsson.backend.controller;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.service.ImageTaggerService;
import com.nilsson.backend.service.PathService;
import com.nilsson.backend.service.TaggerModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * REST Controller for AI Auto-Tagging operations.
 * <p>
 * This controller manages the lifecycle and execution of AI-based image interrogation. It
 * provides endpoints for monitoring the status of the WD14 tagging model, initiating model
 * downloads, and triggering background tagging tasks for individual images or entire
 * directories. It ensures that predicted tags are persisted to both the relational
 * database and the FTS search index.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Model Status:</b> Reports whether the tagging model is ready, downloading, or
 *   requires acquisition.</li>
 *   <li><b>Model Management:</b> Orchestrates the download and cleanup of ONNX model
 *   files and their associated tag mappings.</li>
 *   <li><b>Asynchronous Tagging:</b> Launches background tasks using virtual threads to
 *   process images without blocking the main application flow.</li>
 *   <li><b>Index Synchronization:</b> Updates the SQLite FTS5 index with newly generated
 *   AI tags to ensure they are immediately searchable.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tagger")
public class TaggerController {

    private static final Logger logger = LoggerFactory.getLogger(TaggerController.class);

    private final TaggerModelService modelService;
    private final ImageTaggerService taggerService;
    private final PathService pathService;
    private final JdbcClient jdbcClient;
    private final String appDataDir;

    public TaggerController(TaggerModelService modelService,
                            ImageTaggerService taggerService,
                            PathService pathService,
                            DataSource dataSource,
                            @Value("${app.data.dir:.}") String appDataDir) {
        this.modelService = modelService;
        this.taggerService = taggerService;
        this.pathService = pathService;
        this.jdbcClient = JdbcClient.create(dataSource);
        this.appDataDir = appDataDir;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "ready", modelService.isModelReady(),
                "downloading", modelService.isDownloading(),
                "progress", modelService.getDownloadProgress()
        ));
    }

    @PostMapping("/download")
    public ResponseEntity<String> downloadModel() {
        modelService.downloadModel();
        return ResponseEntity.accepted().body("Download started");
    }

    @PostMapping("/clear-models")
    public ResponseEntity<String> clearModels() {
        Path modelsDir = Paths.get(appDataDir).resolve("data/models").toAbsolutePath().normalize();
        if (Files.exists(modelsDir)) {
            try (Stream<Path> walk = Files.walk(modelsDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(modelsDir))
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new ApplicationException("Failed to clear tag models: " + e.getMessage());
            }
        }
        return ResponseEntity.ok("Tag models cleared.");
    }

    @PostMapping("/tag-folder")
    public ResponseEntity<String> tagFolder(@RequestParam String path, @RequestParam(defaultValue = "0.35") float threshold) {
        File target = pathService.resolve(path);
        if (!target.exists()) {
            return ResponseEntity.badRequest().body("Invalid path");
        }

        Thread.ofVirtual().start(() -> {
            if (target.isDirectory()) {
                File[] files = target.listFiles((d, name) -> name.toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$"));
                if (files != null) {
                    for (File file : files) {
                        processFile(file, threshold);
                    }
                }
            } else {
                processFile(target, threshold);
            }
        });

        return ResponseEntity.accepted().body("Tagging started for " + target.getName());
    }

    private void processFile(File file, float threshold) {
        try {
            List<String> tags = taggerService.tagImage(file, threshold);
            String tagsStr = String.join(", ", tags);

            String filePath = pathService.getNormalizedAbsolutePath(file);
            jdbcClient.sql("UPDATE images SET ai_tags = ? WHERE file_path = ?")
                    .param(tagsStr)
                    .param(filePath)
                    .update();

            Integer id = jdbcClient.sql("SELECT id FROM images WHERE file_path = ?")
                    .param(filePath)
                    .query(Integer.class)
                    .optional()
                    .orElse(null);

            if (id != null) {
                jdbcClient.sql("UPDATE metadata_fts SET ai_tags = ? WHERE image_id = ?")
                        .param(tagsStr)
                        .param(id)
                        .update();
            }

        } catch (Exception e) {
            logger.error("Background tagging failed for file: {}", file.getName(), e);
        }
    }
}
