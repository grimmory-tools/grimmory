package org.booklore.app.controller;

import org.booklore.app.dto.*;
import org.booklore.app.service.AppBookService;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/books")
public class AppBookController {

    private final AppBookService mobileBookService;

    @GetMapping
    public ResponseEntity<AppPageResponse<AppBookSummary>> getBooks(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @RequestParam(required = false, defaultValue = "addedOn") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            @RequestParam(required = false) Long libraryId,
            @RequestParam(required = false) Long shelfId,
            @RequestParam(required = false) ReadStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BookFileType fileType,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(required = false) String authors,
            @RequestParam(required = false) String language) {

        return ResponseEntity.ok(mobileBookService.getBooks(
                page, size, sort, dir, libraryId, shelfId, status, search,
                fileType, minRating, maxRating, authors, language));
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<AppBookDetail> getBookDetail(
            @PathVariable Long bookId) {

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
            @Valid @RequestBody ReadProgressRequest request) {

        mobileBookService.updateBookProgress(bookId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<AppPageResponse<AppBookSummary>> searchBooks(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(mobileBookService.searchBooks(q, page, size));
    }

    @GetMapping("/continue-reading")
    public ResponseEntity<List<AppBookSummary>> getContinueReading(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getContinueReading(limit));
    }

    @GetMapping("/continue-listening")
    public ResponseEntity<List<AppBookSummary>> getContinueListening(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getContinueListening(limit));
    }

    @GetMapping("/recently-added")
    public ResponseEntity<List<AppBookSummary>> getRecentlyAdded(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getRecentlyAdded(limit));
    }

    @GetMapping("/recently-scanned")
    public ResponseEntity<List<AppBookSummary>> getRecentlyScanned(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        return ResponseEntity.ok(mobileBookService.getRecentlyScanned(limit));
    }

    @PutMapping("/{bookId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateStatusRequest request) {

        mobileBookService.updateReadStatus(bookId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{bookId}/rating")
    public ResponseEntity<Void> updateRating(
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateRatingRequest request) {

        mobileBookService.updatePersonalRating(bookId, request.getRating());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/random")
    public ResponseEntity<AppPageResponse<AppBookSummary>> getRandomBooks(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) Long libraryId) {

        return ResponseEntity.ok(mobileBookService.getRandomBooks(page, size, libraryId));
    }
}
