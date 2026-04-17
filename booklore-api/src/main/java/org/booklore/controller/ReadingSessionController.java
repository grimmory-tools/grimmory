package org.booklore.controller;

import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.booklore.service.ReadingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/reading-sessions")
@Tag(name = "Reading Sessions", description = "Endpoints for recording and retrieving user reading sessions")
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
            @Parameter(description = "Book ID") @PathVariable Long bookId, 
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        Page<ReadingSessionResponse> sessions = readingSessionService.getReadingSessionsForBook(bookId, page, size);
        return ResponseEntity.ok(sessions);
    }
}
