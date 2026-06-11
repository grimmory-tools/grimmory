package org.booklore.grimmlink.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchRequest;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchResponse;
import org.booklore.grimmlink.service.GrimmlinkReadingSessionService;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/reading-sessions")
public class GrimmlinkV1ReadingSessionController {

    private final GrimmlinkReadingSessionService readingSessionService;

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
