package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookRecommendation;
import com.adityachandel.booklore.model.dto.BookViewerSettings;
import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.dto.request.ReadStatusUpdateRequest;
import com.adityachandel.booklore.model.dto.request.ShelvesAssignmentRequest;
import com.adityachandel.booklore.model.dto.response.BookDeletionResponse;
import com.adityachandel.booklore.model.enums.ResetProgressType;
import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.metadata.BookMetadataService;
import com.adityachandel.booklore.service.recommender.BookRecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RequestMapping("/api/v1/books")
@RestController
@AllArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookRecommendationService bookRecommendationService;
    private final BookMetadataService bookMetadataService;

    @GetMapping
    public ResponseEntity<List<Book>> getBooks(@RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBookDTOs(withDescription));
    }

    @GetMapping("/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Book> getBook(@PathVariable long bookId, @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBook(bookId, withDescription));
    }

    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    @DeleteMapping
    public ResponseEntity<BookDeletionResponse> deleteBooks(@RequestParam Set<Long> ids) {
        return bookService.deleteBooks(ids);
    }

    @GetMapping("/batch")
    public ResponseEntity<List<Book>> getBooksByIds(@RequestParam Set<Long> ids, @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBooksByIds(ids, withDescription));
    }

    @GetMapping("/{bookId}/cbx/metadata/comicinfo")
    public ResponseEntity<?> getComicInfoMetadata(@PathVariable long bookId) {
        return ResponseEntity.ok(bookMetadataService.getComicInfoMetadata(bookId));
    }


    @GetMapping("/{bookId}/content")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<ByteArrayResource> getBookContent(@PathVariable long bookId) throws IOException {
        return bookService.getBookContent(bookId);
    }

    @GetMapping("/{bookId}/download")
    @PreAuthorize("@securityUtil.canDownload() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> downloadBook(@PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @GetMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookViewerSettings> getBookViewerSettings(@PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookViewerSetting(bookId));
    }

    @PutMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> updateBookViewerSettings(@RequestBody BookViewerSettings bookViewerSettings, @PathVariable long bookId) {
        bookService.updateBookViewerSetting(bookId, bookViewerSettings);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shelves")
    public ResponseEntity<List<Book>> addBookToShelf(@RequestBody @Valid ShelvesAssignmentRequest request) {
        return ResponseEntity.ok(bookService.assignShelvesToBooks(request.getBookIds(), request.getShelvesToAssign(), request.getShelvesToUnassign()));
    }

    @PostMapping("/progress")
    public ResponseEntity<Void> addBookToProgress(@RequestBody @Valid ReadProgressRequest request) {
        bookService.updateReadProgress(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recommendations")
    @CheckBookAccess(bookIdParam = "id")
    public ResponseEntity<List<BookRecommendation>> getRecommendations(@PathVariable Long id, @RequestParam(defaultValue = "25") @Max(25) @Min(1) int limit) {
        return ResponseEntity.ok(bookRecommendationService.getRecommendations(id, limit));
    }

    @PutMapping("/read-status")
    public ResponseEntity<List<Book>> updateReadStatus(@RequestBody @Valid ReadStatusUpdateRequest request) {
        List<Book> updatedBooks = bookService.updateReadStatus(request.ids(), request.status());
        return ResponseEntity.ok(updatedBooks);
    }

    @PostMapping("/reset-progress")
    public ResponseEntity<List<Book>> resetProgress(@RequestBody List<Long> bookIds, @RequestParam ResetProgressType type) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("No book IDs provided");
        }
        List<Book> updatedBooks = bookService.resetProgress(bookIds, type);
        return ResponseEntity.ok(updatedBooks);
    }
}