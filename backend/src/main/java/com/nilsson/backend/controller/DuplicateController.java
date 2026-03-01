package com.nilsson.backend.controller;

import com.nilsson.backend.model.DuplicatePair;
import com.nilsson.backend.service.DuplicateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing duplicate image detection and resolution.
 * <p>
 * This controller delegates business logic to {@link DuplicateService}.
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    private final DuplicateService duplicateService;

    public DuplicateController(DuplicateService duplicateService) {
        this.duplicateService = duplicateService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(duplicateService.getStatus());
    }

    @PostMapping("/scan")
    public ResponseEntity<String> scanForMissingHashes() {
        String result = duplicateService.scanAndFixHashes();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/pairs")
    public ResponseEntity<List<DuplicatePair>> getDuplicatePairs() {
        List<DuplicatePair> pairs = duplicateService.findDuplicatePairs();
        return ResponseEntity.ok(pairs);
    }

    @PostMapping("/resolve-all")
    public ResponseEntity<String> resolveAllDuplicates(@RequestParam(defaultValue = "LATEST_SCANNED") String strategy) {
        String result = duplicateService.autoResolveDuplicates(strategy);
        return ResponseEntity.ok(result);
    }
}
