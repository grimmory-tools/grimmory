package org.booklore.grimmlink.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkMetadataBatchResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataPullResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncRequest;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncResponse;
import org.booklore.grimmlink.service.GrimmlinkMetadataService;
import org.booklore.grimmlink.service.GrimmlinkProgressService;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/syncs")
public class GrimmlinkV1SyncController {

    private final GrimmlinkProgressService progressService;
    private final GrimmlinkMetadataService metadataService;

    @GetMapping(value = "/progress/{bookHash}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderProgress> getProgress(@PathVariable String bookHash) {
        return ResponseEntity.ok(progressService.getProgress(bookHash));
    }

    @PutMapping("/progress")
    public ResponseEntity<Map<String, String>> updateProgress(@Valid @RequestBody KoreaderProgress progress) {
        progressService.updateProgress(progress);
        return ResponseEntity.ok(Map.of("status", "progress updated"));
    }

    @PostMapping("/metadata")
    public ResponseEntity<GrimmlinkMetadataSyncResponse> syncMetadata(@RequestBody(required = false) GrimmlinkMetadataSyncRequest request) {
        return ResponseEntity.ok(metadataService.syncMetadata(request));
    }

    @GetMapping(value = "/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GrimmlinkMetadataPullResponse> pullMetadata(@RequestParam(required = false) Long bookId,
                                                                      @RequestParam(required = false) String bookHash,
                                                                      @RequestParam(required = false) Long bookFileId,
                                                                      @RequestParam(required = false) Instant since,
                                                                      @RequestParam(required = false) Instant cursor,
                                                                      @RequestParam(required = false) Integer limit,
                                                                      @RequestParam(required = false) String type) {
        return ResponseEntity.ok(metadataService.pullMetadata(
                bookId,
                bookHash,
                bookFileId,
                since,
                cursor,
                limit,
                type));
    }

    @PostMapping("/metadata/batch")
    public ResponseEntity<GrimmlinkMetadataBatchResponse> syncMetadataBatch(@RequestBody(required = false) GrimmlinkMetadataSyncRequest request) {
        return ResponseEntity.ok(metadataService.syncMetadataBatch(request));
    }
}
