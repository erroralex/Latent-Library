package com.nilsson.backend.service;

import com.nilsson.backend.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

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
