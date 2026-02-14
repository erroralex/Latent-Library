package com.nilsson.backend.service;

import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the Full-Text Search (FTS) index using SQLite's FTS5 extension.
 * <p>
 * This service orchestrates the synchronization between relational image data (metadata and tags)
 * and the FTS virtual table. It implements specialized tokenization logic to ensure that complex
 * AI metadata, such as LoRAs with strength values and technical generation parameters, is
 * searchable via clean, predictable tokens.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Incremental Indexing:</b> Updates the FTS entry for a specific image whenever its
 *   metadata or tags are modified, ensuring real-time search accuracy.</li>
 *   <li><b>Specialized Tokenization:</b> Formats metadata key-value pairs into searchable tokens
 *   (e.g., "Model_SDXL") to allow for precise field-based filtering within the global text index.</li>
 *   <li><b>LoRA Parsing:</b> Implements deep cleaning of LoRA strings to index the base name
 *   independently of strength values (e.g., {@code <lora:name:0.8>} becomes {@code Loras_name}).</li>
 *   <li><b>Index Reconstruction:</b> Provides a comprehensive mechanism to rebuild the entire
 *   FTS index from the ground up, typically used for maintenance or after schema changes.</li>
 * </ul>
 */
@Service
public class FtsService {

    private static final Logger logger = LoggerFactory.getLogger(FtsService.class);
    private final JdbcClient jdbcClient;
    private final ImageMetadataRepository metadataRepository;
    private final TagRepository tagRepository;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");

    public FtsService(DataSource dataSource, ImageMetadataRepository metadataRepository, TagRepository tagRepository) {
        this.jdbcClient = JdbcClient.create(dataSource);
        this.metadataRepository = metadataRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public void updateFtsIndex(int imageId) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for FTS indexing.");
        }

        String metadataText = metadataRepository.getMetadata(imageId).entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if ("Loras".equals(key)) {
                        return Arrays.stream(value.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(this::cleanLoraName)
                                .map(loraName -> formatFtsToken("Loras", loraName))
                                .collect(Collectors.joining(" "));
                    }

                    if ("Prompt".equals(key) || "Negative".equals(key)) {
                        return value;
                    }

                    return formatFtsToken(key, value);
                })
                .collect(Collectors.joining(" "));

        String tagsText = tagRepository.getTags(imageId).stream()
                .map(tag -> formatFtsToken("tag", tag))
                .collect(Collectors.joining(" "));

        String globalText = (metadataText + " " + tagsText).trim();

        String aiTags = jdbcClient.sql("SELECT ai_tags FROM images WHERE id = ?")
                .param(imageId)
                .query(String.class)
                .optional()
                .orElse("");

        jdbcClient.sql("INSERT OR REPLACE INTO metadata_fts(image_id, global_text, ai_tags) VALUES (?, ?, ?)")
                .param(imageId)
                .param(globalText)
                .param(aiTags)
                .update();
    }

    private String cleanLoraName(String raw) {
        if (raw.toLowerCase().startsWith("<lora:")) raw = raw.substring(6);
        if (raw.endsWith(">")) raw = raw.substring(0, raw.length() - 1);
        int lastColon = raw.lastIndexOf(':');
        if (lastColon > 0 && raw.substring(lastColon + 1).matches("[\\d.]+")) {
            raw = raw.substring(0, lastColon);
        }
        lastColon = raw.lastIndexOf(':');
        if (lastColon > 0 && raw.substring(lastColon + 1).matches("[\\d.]+")) {
            raw = raw.substring(0, lastColon);
        }

        return raw.trim();
    }

    @Transactional
    public void rebuildFtsIndex() {
        logger.info("Starting full FTS index rebuild...");
        try {
            List<Integer> imageIds = jdbcClient.sql("SELECT id FROM images").query(Integer.class).list();
            for (int imageId : imageIds) {
                updateFtsIndex(imageId);
            }
            logger.info("FTS index rebuild completed successfully.");
        } catch (Exception e) {
            logger.error("Failed to rebuild FTS index.", e);
            throw new ApplicationException("System failed to rebuild the search index.", e);
        }
    }

    public static String formatFtsToken(String key, String value) {
        String sanitizedKey = NON_ALPHANUMERIC.matcher(key).replaceAll("_");
        String sanitizedValue = NON_ALPHANUMERIC.matcher(value).replaceAll("_");
        return sanitizedKey + "_" + sanitizedValue;
    }
}
