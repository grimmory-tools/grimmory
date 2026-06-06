package org.booklore.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.request.ReadiumSyncRequest;
import org.booklore.model.dto.response.ReadiumProgressResponse;
import org.booklore.service.progress.ReadingProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/readium")
public class ReadiumSyncController {

    private final ReadingProgressService readingProgressService;

    @GetMapping("/syncs/progress/{bookFileId}")
    public ResponseEntity<ReadiumProgressResponse> getProgress(@PathVariable Long bookFileId) {
        ReadiumProgressResponse response = readingProgressService.getReadiumProgress(bookFileId);
        if (response == null) {
            throw ApiError.GENERIC_NOT_FOUND.createException("No progress found for bookFileId " + bookFileId);
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/syncs/progress")
    public ResponseEntity<Map<String, String>> updateProgress(@Valid @RequestBody ReadiumSyncRequest request) {
        readingProgressService.updateReadProgress(request.toReadProgressRequest());
        return ResponseEntity.ok(Map.of("status", "progress updated"));
    }
}
