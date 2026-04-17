package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.booklore.app.dto.*;
import org.booklore.app.service.AppBookService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/books")
@Tag(name = "App Books", description = "Endpoints for browsing and updating books in the app experience")
public class AppBookController {

    private final AppBookService mobileBookService;

    @Operation(
            summary = "List app books",
            description = "Retrieve paginated books for the app with optional filtering and sorting.",
            operationId = "appGetBooks"
    )
    @GetMapping
    public ResponseEntity<AppPageResponse<AppBookSummary>> getBooks(
            @ModelAttribute BookListRequest request) {
        return ResponseEntity.ok(mobileBookService.getBooks(request));
    }

    @Operation(
            summary = "Get app book details",
            description = "Retrieve detailed app-facing information for a single book.",
            operationId = "appGetBookDetail"
    )
    @GetMapping("/{bookId}")
    public ResponseEntity<AppBookDetail> getBookDetail(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {

        return ResponseEntity.ok(mobileBookService.getBookDetail(bookId));
    }

    @GetMapping("/{bookId}/progress")
    public ResponseEntity<AppBookProgressResponse> getBookProgress(
            @PathVariable Long bookId) {

        return ResponseEntity.ok(mobileBookService.getBookProgress(bookId));
    }

    @PutMapping("/{bookId}/progress")
    public ResponseEntity<Void> updateBookProgress(
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateProgressRequest request) {

        mobileBookService.updateBookProgress(bookId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Search app books",
            description = "Search books in the app catalog using a free-text query.",
            operationId = "appSearchBooks"
    )
    @GetMapping("/search")
    public ResponseEntity<AppPageResponse<AppBookSummary>> searchBooks(
            @Parameter(description = "Search query") @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(mobileBookService.searchBooks(q, page, size));
    }

    @Operation(
            summary = "Get continue reading books",
            description = "Retrieve books currently in progress for reading in the app.",
            operationId = "appGetContinueReading"
    )
    @GetMapping("/continue-reading")
    public ResponseEntity<List<AppBookSummary>> getContinueReading(
            @Parameter(description = "Limit") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getContinueReading(limit));
    }

    @Operation(
            summary = "Get continue listening books",
            description = "Retrieve audiobooks currently in progress for listening in the app.",
            operationId = "appGetContinueListening"
    )
    @GetMapping("/continue-listening")
    public ResponseEntity<List<AppBookSummary>> getContinueListening(
            @Parameter(description = "Limit") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getContinueListening(limit));
    }

    @Operation(
            summary = "Get recently added books",
            description = "Retrieve recently added books for the app home experience.",
            operationId = "appGetRecentlyAdded"
    )
    @GetMapping("/recently-added")
    public ResponseEntity<List<AppBookSummary>> getRecentlyAdded(
            @Parameter(description = "Limit") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getRecentlyAdded(limit));
    }

    @Operation(
            summary = "Get recently scanned books",
            description = "Retrieve recently scanned books for the app home experience.",
            operationId = "appGetRecentlyScanned"
    )
    @GetMapping("/recently-scanned")
    public ResponseEntity<List<AppBookSummary>> getRecentlyScanned(
            @Parameter(description = "Limit") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getRecentlyScanned(limit));
    }

    @Operation(
            summary = "Update app book read status",
            description = "Update the read status of a book from the app interface.",
            operationId = "appUpdateStatus"
    )
    @PutMapping("/{bookId}/status")
    public ResponseEntity<Void> updateStatus(
            @Parameter(description = "Book ID") @PathVariable Long bookId,
            @Valid @RequestBody UpdateStatusRequest request) {

        mobileBookService.updateReadStatus(bookId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Update app book rating",
            description = "Update the personal rating of a book from the app interface.",
            operationId = "appUpdateRating"
    )
    @PutMapping("/{bookId}/rating")
    public ResponseEntity<Void> updateRating(
            @Parameter(description = "Book ID") @PathVariable Long bookId,
            @Valid @RequestBody UpdateRatingRequest request) {

        mobileBookService.updatePersonalRating(bookId, request.getRating());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get random app books",
            description = "Retrieve a paginated random selection of books for discovery in the app.",
            operationId = "appGetRandomBooks"
    )
    @GetMapping("/random")
    public ResponseEntity<AppPageResponse<AppBookSummary>> getRandomBooks(
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "Library ID") @RequestParam(required = false) Long libraryId) {

        return ResponseEntity.ok(mobileBookService.getRandomBooks(page, size, libraryId));
    }
}
