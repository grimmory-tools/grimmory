package org.booklore.controller;

import org.booklore.model.dto.BookMark;
import org.booklore.model.dto.CreateBookMarkRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.service.book.BookMarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookmarks")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Bookmarks", description = "Endpoints for managing book bookmarks")
public class BookMarkController {

    private final BookMarkService bookMarkService;

    @Operation(summary = "Get bookmarks for a book", description = "Retrieve all bookmarks for a specific book.")
    @ApiResponse(responseCode = "200", description = "Bookmarks returned successfully")
    @GetMapping("/book/{bookId}")
    public List<BookMark> getBookmarksForBook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return bookMarkService.getBookmarksForBook(bookId);
    }

    @Operation(summary = "Get a specific bookmark", description = "Retrieve a specific bookmark by its ID.")
    @ApiResponse(responseCode = "200", description = "Bookmark returned successfully")
    @GetMapping("/{bookmarkId}")
    public BookMark getBookmarkById(
            @Parameter(description = "ID of the bookmark") @PathVariable Long bookmarkId) {
        return bookMarkService.getBookmarkById(bookmarkId);
    }

    @Operation(summary = "Create a bookmark", description = "Create a new bookmark for a book.")
    @ApiResponse(responseCode = "200", description = "Bookmark created successfully")
    @PostMapping
    public BookMark createBookmark(
            @Parameter(description = "Bookmark creation request") @Valid @RequestBody CreateBookMarkRequest request) {
        return bookMarkService.createBookmark(request);
    }

    @Operation(summary = "Update a bookmark", description = "Update an existing bookmark's properties (title, location, color, etc.).")
    @ApiResponse(responseCode = "200", description = "Bookmark updated successfully")
    @PutMapping("/{bookmarkId}")
    public BookMark updateBookmark(
            @Parameter(description = "ID of the bookmark to update") @PathVariable Long bookmarkId,
            @Parameter(description = "Bookmark update request") @Valid @RequestBody UpdateBookMarkRequest request) {
        return bookMarkService.updateBookmark(bookmarkId, request);
    }

    @Operation(summary = "Delete a bookmark", description = "Delete a specific bookmark by its ID.")
    @ApiResponse(responseCode = "204", description = "Bookmark deleted successfully")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<Void> deleteBookmark(
            @Parameter(description = "ID of the bookmark to delete") @PathVariable Long bookmarkId) {
        bookMarkService.deleteBookmark(bookmarkId);
        return ResponseEntity.noContent().build();
    }
}
