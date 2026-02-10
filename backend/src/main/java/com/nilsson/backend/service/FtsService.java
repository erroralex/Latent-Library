package com.nilsson.backend.service;

import com.nilsson.backend.repository.ImageMetadataRepository;
import com.nilsson.backend.repository.TagRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
                .map(entry -> formatFtsToken(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.joining(" "));

        String tagsText = tagRepository.getTags(imageId).stream()
                .map(tag -> formatFtsToken("tag", tag))
                .collect(java.util.stream.Collectors.joining(" "));

        String globalText = (metadataText + " " + tagsText).trim();

        jdbcClient.sql("INSERT OR REPLACE INTO metadata_fts(image_id, global_text) VALUES (?, ?)")
                .param(imageId)
                .param(globalText)
                .update();
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
