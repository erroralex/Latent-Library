package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.TagRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
                    if ("Loras".equals(entry.getKey())) {
                        // Special handling for Loras: split by comma and index each lora individually
                        // We want to index the CLEAN name so that searching for "Loras_MyLora" matches
                        // even if the stored value is "<lora:MyLora:0.8>"
                        return Arrays.stream(entry.getValue().split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(this::cleanLoraName) // Extract just the name for indexing
                                .map(loraName -> formatFtsToken("Loras", loraName))
                                .collect(Collectors.joining(" "));
                    }
                    return formatFtsToken(entry.getKey(), entry.getValue());
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
        // Check if the part after the last colon is numeric (strength)
        if (lastColon > 0 && raw.substring(lastColon + 1).matches("[\\d.]+")) {
            raw = raw.substring(0, lastColon);
        }
        // Handle cases like <lora:name:1.0:1.0> or similar if they exist, though standard is name:strength
        // If there's another colon, it might be another parameter, strip it too
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
