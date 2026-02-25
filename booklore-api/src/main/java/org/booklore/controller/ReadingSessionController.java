package org.booklore.controller;

import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.booklore.service.ReadingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/reading-sessions")
public class ReadingSessionController {

    private final ReadingSessionService readingSessionService;

    @Operation(summary = "Record a reading session", description = "Receive telemetry from the reader client and persist or log the session.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reading session accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid payload")
    })
    @PostMapping
    public ResponseEntity<Void> recordReadingSession(@RequestBody @Valid ReadingSessionRequest request) {
        readingSessionService.recordSession(request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Get reading sessions for a book", description = "Returns paginated reading sessions for a specific book for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/book/{bookId}")
    public ResponseEntity<Page<ReadingSessionResponse>> getReadingSessionsForBook(
            @PathVariable Long bookId, 
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        Page<ReadingSessionResponse> sessions = readingSessionService.getReadingSessionsForBook(bookId, page, size);
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "Record multiple reading sessions", description = "Batch insert reading sessions for a book")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading sessions recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PostMapping("/batch")
    public ResponseEntity<ReadingSessionBatchResponse> recordBatchSessions(
            @Valid @RequestBody ReadingSessionBatchRequest request) {
        ReadingSessionBatchResponse response = readingSessionService.recordSessionsBatch(request);
        return ResponseEntity.ok(response);
    }
}
