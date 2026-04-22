package org.booklore.controller;

import org.booklore.model.dto.BookReview;
import org.booklore.service.book.BookReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@AllArgsConstructor
@Tag(name = "Book Reviews", description = "Endpoints for managing book reviews")
public class BookReviewController {

    private final BookReviewService bookReviewService;

    @Operation(summary = "List reviews for a book", description = "Retrieve all reviews for a specific book.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reviews returned successfully"),
        @ApiResponse(responseCode = "204", description = "No reviews found")
    })
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<BookReview>> listByBook(
            @Parameter(description = "ID of the book") @PathVariable @Positive(message = "Book ID must be positive") Long bookId) {
        List<BookReview> reviews = bookReviewService.getByBookId(bookId);
        if (reviews.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Refresh reviews for a book", description = "Refresh and retrieve reviews for a specific book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Reviews refreshed successfully")
    @PostMapping("/book/{bookId}/refresh")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public List<BookReview> refreshReviews(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return bookReviewService.refreshReviews(bookId);
    }

    @Operation(summary = "Delete a review", description = "Delete a specific review by its ID. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Review deleted successfully")
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID of the review to delete") @PathVariable Long id) {
        bookReviewService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete all reviews for a book", description = "Delete all reviews for a specific book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "All reviews deleted successfully")
    @DeleteMapping("/book/{bookId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteAllByBookId(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookReviewService.deleteAllByBookId(bookId);
        return ResponseEntity.noContent().build();
    }
}