package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.TagRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the Full-Text Search (FTS) index.
 * <p>
 * This service orchestrates the synchronization between relational image data (metadata and tags)
 * and the SQLite FTS5 virtual table. It implements specialized tokenization logic to ensure
 * that complex AI metadata (like LoRAs with strengths) is searchable via clean, predictable tokens.
 * <p>
 * Key functionalities:
 * - Incremental Indexing: Updates the FTS entry for a single image when its metadata or tags change.
 * - Specialized Tokenization: Formats metadata key-value pairs into searchable tokens (e.g., "Model_SDXL").
 * - LoRA Parsing: Implements deep cleaning of LoRA strings to index the base name independently of strength values.
 * - Index Reconstruction: Provides a mechanism to rebuild the entire FTS index from the ground up.
 */
@Service
public class FtsService {

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

                    // Index prompts as raw text to allow natural language search
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

        jdbcClient.sql("INSERT OR REPLACE INTO metadata_fts(image_id, global_text) VALUES (?, ?)")
                .param(imageId)
                .param(globalText)
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
        List<Integer> imageIds = jdbcClient.sql("SELECT id FROM images").query(Integer.class).list();
        for (int imageId : imageIds) {
            updateFtsIndex(imageId);
        }
    }

    public static String formatFtsToken(String key, String value) {
        String sanitizedKey = NON_ALPHANUMERIC.matcher(key).replaceAll("_");
        String sanitizedValue = NON_ALPHANUMERIC.matcher(value).replaceAll("_");
        return sanitizedKey + "_" + sanitizedValue;
    }
}
