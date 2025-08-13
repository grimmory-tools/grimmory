package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.service.BookReviewService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@AllArgsConstructor
public class BookReviewController {

    private final BookReviewService bookReviewService;

    @GetMapping("/book/{bookId}")
    public List<BookReview> listByBook(@PathVariable Long bookId) {
        return bookReviewService.getByBookId(bookId);
    }

    @PostMapping("/book/{bookId}/refresh")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public List<BookReview> refreshReviews(@PathVariable Long bookId) {
        return bookReviewService.refreshReviews(bookId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookReviewService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/book/{bookId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteAllByBookId(@PathVariable Long bookId) {
        bookReviewService.deleteAllByBookId(bookId);
        return ResponseEntity.noContent().build();
    }
}