package com.nilsson.backend.model;

import java.util.Map;

public record DuplicateImageInfo(String path, int rating, Map<String, String> metadata) {
}
