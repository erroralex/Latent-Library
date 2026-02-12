package com.nilsson.backend.service;

import com.nilsson.backend.exception.ValidationException;
import com.nilsson.backend.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service for managing image tags and ensuring search index consistency.
 * <p>
 * This service provides a high-level API for tagging operations, abstracting the underlying
 * repository interactions. It ensures that all tagging actions are performed within a
 * transactional context and coordinates with the {@link FtsService} to keep the search
 * index synchronized with the latest tag data.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Atomic Tagging:</b> Facilitates the addition and removal of tags, ensuring that
 *   database changes are committed only if the entire operation succeeds.</li>
 *   <li><b>Index Synchronization:</b> Automatically triggers updates to the SQLite FTS5 index
 *   whenever tags are modified, making new tags immediately searchable.</li>
 *   <li><b>Validation & Sanitization:</b> Ensures that only valid, non-blank tags are processed
 *   and that whitespace is consistently trimmed.</li>
 * </ul>
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
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for tagging.");
        }
        if (tag == null || tag.isBlank()) {
            throw new ValidationException("Tag content cannot be empty.");
        }

        tagRepository.addTag(imageId, tag.trim());
        ftsService.updateFtsIndex(imageId);
    }

    @Transactional
    public void removeTag(int imageId, String tag) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for tag removal.");
        }
        if (tag == null || tag.isBlank()) {
            throw new ValidationException("Tag content cannot be empty for removal.");
        }

        tagRepository.removeTag(imageId, tag.trim());
        ftsService.updateFtsIndex(imageId);
    }

    public Set<String> getTags(int imageId) {
        if (imageId <= 0) {
            throw new ValidationException("Invalid image ID provided for tag retrieval.");
        }
        return tagRepository.getTags(imageId);
    }
}