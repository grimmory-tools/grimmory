package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookMark;
import com.adityachandel.booklore.model.dto.CreateBookMarkRequest;
import com.adityachandel.booklore.service.book.BookMarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookmarks")
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

    @Operation(summary = "Create a bookmark", description = "Create a new bookmark for a book.")
    @ApiResponse(responseCode = "200", description = "Bookmark created successfully")
    @PostMapping
    public BookMark createBookmark(
            @Parameter(description = "Bookmark creation request") @Valid @RequestBody CreateBookMarkRequest request) {
        return bookMarkService.createBookmark(request);
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
