package com.nilsson.backend.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ResourceNotFoundException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.strategy.ComfyUIStrategy;
import com.nilsson.backend.strategy.MetadataStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Service for extracting, parsing, and interpreting technical metadata embedded within AI-generated images.
 * <p>
 * This service implements a multi-stage extraction pipeline designed to handle the diverse and
 * often non-standard ways AI generation tools store metadata. It supports standard EXIF/IPTC
 * data as well as custom PNG chunks and User Comments used by tools like Automatic1111,
 * ComfyUI, InvokeAI, NovelAI, and SwarmUI.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Format Agnostic Extraction:</b> Utilizes {@code metadata-extractor} to find technical
 *   data in various image formats (PNG, JPG, WebP).</li>
 *   <li><b>Heuristic Scoring:</b> Implements a scoring mechanism to identify the most relevant
 *   metadata "chunk" when multiple exist (e.g., both EXIF and custom PNG chunks).</li>
 *   <li><b>Strategy-Based Parsing:</b> Employs a suite of {@link MetadataStrategy} implementations
 *   to parse tool-specific JSON or text formats into a unified key-value map.</li>
 *   <li><b>Physical Attribute Extraction:</b> Resolves image dimensions and file sizes using
 *   low-level I/O and metadata headers.</li>
 *   <li><b>Recursive JSON Traversal:</b> Deep-scans complex JSON structures (like ComfyUI workflows)
 *   to extract generation parameters even when nested deep within the graph.</li>
 * </ul>
 */
@Service
public class MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    private final List<MetadataStrategy> jsonStrategies;

    public MetadataService(List<MetadataStrategy> jsonStrategies) {
        this.jsonStrategies = jsonStrategies;
    }

    public String getRawMetadata(File file) {
        return findBestMetadataChunk(file);
    }

    public Map<String, String> getExtractedData(File file) {
        if (file == null || !file.exists()) {
            throw new ResourceNotFoundException("Image file", file != null ? file.getAbsolutePath() : "null");
        }

        Map<String, String> results = new HashMap<>();

        extractPhysicalDimensions(file, results);
        extractFileSize(file, results);
        String rawData = findBestMetadataChunk(file);
        results.putAll(processRawMetadata(rawData));

        return results;
    }

    public Map<String, String> processRawMetadata(String rawData) {
        Map<String, String> results = new HashMap<>();

        if (rawData == null || rawData.isEmpty()) {
            results.put("Prompt", "No metadata found in this image.");
            return results;
        }

        results.put("Raw", rawData);
        String trimmed = rawData.trim();

        if (trimmed.startsWith("{") ||
                (trimmed.startsWith("\"") && trimmed.contains("\"prompt\""))) {
            parseJsonMetadata(trimmed, results);
        } else if (rawData.contains("Steps:") &&
                (rawData.contains("Sampler:") || rawData.contains("Schedule type:"))) {
            results.putAll(TextParamsParser.parse(rawData));
            results.put("Software", "A1111 / Forge");
        } else {
            results.put("Prompt", rawData);
            results.put("Software", "Unknown");
        }

        return results;
    }

    private void extractFileSize(File file, Map<String, String> results) {
        if (file == null || !file.exists()) return;
        long bytes = file.length();
        String sizeStr;
        if (bytes < 1024) {
            sizeStr = bytes + " B";
        } else if (bytes < 1024 * 1024) {
            sizeStr = new DecimalFormat("#.##").format(bytes / 1024.0) + " KB";
        } else {
            sizeStr = new DecimalFormat("#.##").format(bytes / (1024.0 * 1024.0)) + " MB";
        }
        results.put("FileSize", sizeStr);
    }

    private void extractPhysicalDimensions(File file, Map<String, String> results) {
        int width = 0;
        int height = 0;

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String name = tag.getTagName().toLowerCase();
                    String desc = tag.getDescription();
                    if (desc == null || desc.isEmpty()) continue;
                    if (name.contains("thumbnail") || name.contains("resolution")) continue;

                    String valStr = desc.split(" ")[0];
                    if (!valStr.matches("\\d+")) continue;

                    int val = Integer.parseInt(valStr);
                    if (val <= 0) continue;

                    if (name.contains("width") && val > width) width = val;
                    if (name.contains("height") && val > height) height = val;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to read metadata using ImageMetadataReader for {}: {}", file.getName(), e.getMessage());
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            logger.debug("Failed to read dimensions using ImageIO for {}: {}", file.getName(), e.getMessage());
        }

        if (width > 0 && height > 0) {
            results.put("Resolution", width + "x" + height);
        }
    }

    private String findBestMetadataChunk(File file) {
        if (file == null) {
            throw new ValidationException("File parameter cannot be null.");
        }

        List<String> candidates = new ArrayList<>();

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String desc = tag.getDescription();
                    if (desc == null) continue;

                    String tagName = tag.getTagName().toLowerCase();

                    if (tagName.contains("parameters") ||
                            tagName.contains("user comment") ||
                            desc.contains("Steps:")) {
                        candidates.add(desc);
                    } else if (desc.contains("{")) {
                        int braceIndex = desc.indexOf("{");
                        if (braceIndex != -1) {
                            candidates.add(desc.substring(braceIndex).trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract metadata chunks for {}: {}", file.getName(), e.getMessage());
            throw new ApplicationException("System error extracting raw metadata chunks.", e);
        }

        String bestChunk = null;
        int bestScore = -1;

        for (String chunk : candidates) {
            int score = scoreChunk(chunk);
            if (score > bestScore) {
                bestScore = score;
                bestChunk = chunk;
            }
        }

        return bestChunk;
    }

    private int scoreChunk(String chunk) {
        if (chunk == null) return 0;
        if (chunk.contains("sui_image_params")) return 100;
        if (chunk.matches("(?s).*\\{\\s*\"\\d+\"\\s*:\\s*\\{.*")) return 90;
        if (chunk.contains("Steps:") && chunk.contains("Sampler:")) return 80;
        if (chunk.contains("\"nodes\"") && chunk.contains("\"links\"")) return 10;
        return 0;
    }

    private void parseJsonMetadata(String json, Map<String, String> results) {
        try {
            String cleanJson = json;

            int lastBrace = cleanJson.lastIndexOf("}");
            if (lastBrace != -1 && lastBrace < cleanJson.length() - 1) {
                cleanJson = cleanJson.substring(0, lastBrace + 1);
            }

            if (cleanJson.startsWith("\"")) {
                cleanJson = cleanJson
                        .substring(1, cleanJson.length() - 1)
                        .replace("\\\"", "\"");
            }

            JsonNode root = mapper.readTree(cleanJson);

            String software = "Unknown";
            if (root.has("sui_image_params")) software = "SwarmUI";
            else if (root.has("meta") && root.get("meta").has("invokeai_metadata")) software = "InvokeAI";
            else if (root.has("uc")) software = "NovelAI";
            else {
                if (root.has("prompt") && root.get("prompt").isObject()) {
                    JsonNode promptNode = root.get("prompt");
                    Iterator<String> promptKeys = promptNode.fieldNames();
                    if (promptKeys.hasNext()) {
                        String firstPk = promptKeys.next();
                        if (firstPk.matches("\\d+") && promptNode.get(firstPk).has("class_type")) {
                            software = "ComfyUI";
                        }
                    }
                }

                if ("Unknown".equals(software)) {
                    Iterator<String> keys = root.fieldNames();
                    if (keys.hasNext()) {
                        String firstKey = keys.next();
                        if (firstKey.matches("\\d+") && root.get(firstKey).has("class_type")) {
                            software = "ComfyUI";
                        } else if (root.has("nodes") && root.has("links")) {
                            software = "ComfyUI (Workflow)";
                        }
                    }
                }
            }

            results.put("Software", software);

            findKeysRecursively(root, results, software);

            if (!results.containsKey("Prompt") || results.get("Prompt").isEmpty()) {
                results.put("Prompt", findLongestText(root));
            }
        } catch (Exception e) {
            logger.warn("JSON parsing error for provided metadata: {}", e.getMessage());
            results.put("Prompt", "Error parsing JSON: " + e.getMessage());
        }
    }

    private void findKeysRecursively(JsonNode node, Map<String, String> results, String software) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();

                for (MetadataStrategy strategy : jsonStrategies) {
                    if (software.contains("ComfyUI") && !(strategy instanceof ComfyUIStrategy)) {
                        continue;
                    }
                    strategy.extract(
                            entry.getKey().toLowerCase(),
                            entry.getValue(),
                            node,
                            results
                    );
                }

                findKeysRecursively(entry.getValue(), results, software);
            }
        } else if (!node.isObject() && node.isArray()) {
            for (JsonNode child : node) {
                findKeysRecursively(child, results, software);
            }
        }
    }

    private String findLongestText(JsonNode node) {
        final String[] longest = {"No descriptive prompt found"};
        node.findValues("text").forEach(v -> {
            String val = v.asText();
            if (val.length() > longest[0].length() && !val.contains("{")) {
                longest[0] = val;
            }
        });
        return longest[0];
    }
}