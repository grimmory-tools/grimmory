package org.booklore.grimmlink.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchRequest;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchResponse;
import org.booklore.grimmlink.service.GrimmlinkReadingSessionService;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/reading-sessions")
public class GrimmlinkV1ReadingSessionController {

    private final GrimmlinkReadingSessionService readingSessionService;

    @GetMapping
    public ResponseEntity<List<ReadingSessionResponse>> getSessions(
            @RequestParam Long bookId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(readingSessionService.getReadingSessions(bookId, limit));
    }

    @PostMapping
    public ResponseEntity<Void> recordSession(@RequestBody @Valid ReadingSessionRequest request) {
        readingSessionService.recordReadingSession(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/batch")
    public ResponseEntity<GrimmlinkReadingSessionBatchResponse> recordBatchSessions(
            @RequestBody @Valid GrimmlinkReadingSessionBatchRequest request) {
        return ResponseEntity.ok(readingSessionService.recordReadingSessionsBatch(request));
    }
}
