package com.nilsson.backend.service;

import com.nilsson.backend.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service for managing image tags and ensuring search index consistency.
 * <p>
 * This service provides a high-level API for tagging operations. It wraps the underlying
 * repository calls in transactions and automatically triggers search index updates
 * whenever tags are added or removed, ensuring that new tags are immediately searchable.
 * <p>
 * Key functionalities:
 * - Atomic Tagging: Adds or removes tags within a transactional context.
 * - Index Synchronization: Coordinates with {@code FtsService} to refresh the search index.
 * - Validation: Ensures only non-blank, valid tags are processed.
 */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final FtsService ftsService;

    public TagService(TagRepository tagRepository, FtsService ftsService) {
        this.tagRepository = tagRepository;
        this.ftsService = ftsService;
    }

    @Transactional
    public void addTag(int imageId, String tag) {
        if (imageId > 0 && tag != null && !tag.isBlank()) {
            tagRepository.addTag(imageId, tag.trim());
            ftsService.updateFtsIndex(imageId);
        }
    }

    @Transactional
    public void removeTag(int imageId, String tag) {
        if (imageId > 0 && tag != null && !tag.isBlank()) {
            tagRepository.removeTag(imageId, tag.trim());
            ftsService.updateFtsIndex(imageId);
        }
    }

    public Set<String> getTags(int imageId) {
        if (imageId > 0) {
            return tagRepository.getTags(imageId);
        }
        return Set.of();
    }
}
