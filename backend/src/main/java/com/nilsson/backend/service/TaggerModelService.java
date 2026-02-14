package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing the lifecycle and retrieval of the WD14 tagging model.
 * <p>
 * This service handles the downloading of the ONNX model file and its associated tags CSV
 * from HuggingFace. It provides status updates on the download progress and ensures that
 * the model files are correctly placed in the application's data directory.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Model Acquisition:</b> Downloads the WD14 ONNX model and tag mapping CSV from
 *   remote repositories if not present locally.</li>
 *   <li><b>Progress Tracking:</b> Maintains an atomic progress indicator to provide
 *   real-time feedback to the UI during large model downloads.</li>
 *   <li><b>Storage Management:</b> Ensures the model directory exists and manages the
 *   physical file paths for the model and its metadata.</li>
 *   <li><b>Asynchronous Execution:</b> Utilizes virtual threads to perform I/O-heavy
 *   downloads without blocking the main application flow.</li>
 * </ul>
 */
@Service
public class TaggerModelService {

    private static final Logger logger = LoggerFactory.getLogger(TaggerModelService.class);
    private static final String MODEL_URL = "https://huggingface.co/SmilingWolf/wd-v1-4-convnext-tagger-v2/resolve/main/model.onnx";
    private static final String TAGS_URL = "https://huggingface.co/SmilingWolf/wd-v1-4-convnext-tagger-v2/resolve/main/selected_tags.csv";
    private static final String MODEL_DIR = "data/models";
    private static final String MODEL_FILE = "wd14-v2.onnx";
    private static final String TAGS_FILE = "tags.csv";

    private final Path modelPath;
    private final Path tagsPath;
    private final AtomicInteger downloadProgress = new AtomicInteger(0);
    private volatile boolean isDownloading = false;

    public TaggerModelService(@Value("${app.data.dir:.}") String appDataDir) {
        Path modelsDir = Paths.get(appDataDir).resolve(MODEL_DIR).toAbsolutePath().normalize();
        try {
            if (!Files.exists(modelsDir)) {
                Files.createDirectories(modelsDir);
            }
        } catch (IOException e) {
            throw new ApplicationException("Failed to create models directory", e);
        }
        this.modelPath = modelsDir.resolve(MODEL_FILE);
        this.tagsPath = modelsDir.resolve(TAGS_FILE);
    }

    public boolean isModelReady() {
        return Files.exists(modelPath) && Files.exists(tagsPath);
    }

    public int getDownloadProgress() {
        return downloadProgress.get();
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public Path getModelPath() {
        return modelPath;
    }

    public Path getTagsPath() {
        return tagsPath;
    }

    public void downloadModel() {
        if (isModelReady() || isDownloading) return;

        isDownloading = true;
        downloadProgress.set(0);

        Thread.ofVirtual().start(() -> {
            try {
                logger.info("Starting WD14 model download...");
                downloadFile(MODEL_URL, modelPath);
                downloadProgress.set(90);

                logger.info("Downloading tags CSV...");
                downloadFile(TAGS_URL, tagsPath);
                downloadProgress.set(100);

                logger.info("WD14 model download completed.");
            } catch (Exception e) {
                logger.error("Failed to download tagging model", e);
                try {
                    Files.deleteIfExists(modelPath);
                    Files.deleteIfExists(tagsPath);
                } catch (IOException ignored) {
                }
            } finally {
                isDownloading = false;
            }
        });
    }

    private void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new ApplicationException("Failed to download file: HTTP " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
