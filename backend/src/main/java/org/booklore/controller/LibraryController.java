package org.booklore.controller;

import org.booklore.config.security.annotation.CheckLibraryAccess;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.CreateLibraryRequest;
import org.booklore.service.library.LibraryHealthService;
import org.booklore.service.library.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/libraries")
@AllArgsConstructor
@Tag(name = "Libraries", description = "Endpoints for managing libraries and their books")
public class LibraryController {

    private final LibraryService libraryService;
    private final LibraryHealthService libraryHealthService;

    @Operation(summary = "Get all libraries", description = "Retrieve a list of all libraries.")
    @ApiResponse(responseCode = "200", description = "Libraries returned successfully")
    @GetMapping
    public ResponseEntity<List<Library>> getLibraries() {
        return ResponseEntity.ok(libraryService.getLibraries());
    }

    @Operation(summary = "Get library health", description = "Check accessibility of all library paths.")
    @ApiResponse(responseCode = "200", description = "Library health returned successfully")
    @GetMapping("/health")
    public ResponseEntity<Map<Long, Boolean>> getLibraryHealth() {
        return ResponseEntity.ok(libraryHealthService.getCurrentHealth());
    }

    @Operation(summary = "Get a library by ID", description = "Retrieve details of a specific library by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Library details returned successfully"),
            @ApiResponse(responseCode = "404", description = "Library not found")
    })
    @GetMapping("/{libraryId}")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    public ResponseEntity<Library> getLibrary(@Parameter(description = "ID of the library") @PathVariable long libraryId) {
        return ResponseEntity.ok(libraryService.getLibrary(libraryId));
    }

    @Operation(summary = "Create a library", description = "Create a new library. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "200", description = "Library created successfully")
    @PostMapping
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Library> createLibrary(@Parameter(description = "Library creation request") @Validated @RequestBody CreateLibraryRequest request) {
        return ResponseEntity.ok(libraryService.createLibrary(request));
    }

    @Operation(summary = "Update a library", description = "Update an existing library. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "200", description = "Library updated successfully")
    @PutMapping("/{libraryId}")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Library> updateLibrary(
            @Parameter(description = "Library update request") @Validated @RequestBody CreateLibraryRequest request,
            @Parameter(description = "ID of the library") @PathVariable Long libraryId) {
        return ResponseEntity.ok(libraryService.updateLibrary(request, libraryId));
    }

    @Operation(summary = "Delete a library", description = "Delete a library by its ID. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "204", description = "Library deleted successfully")
    @DeleteMapping("/{libraryId}")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<?> deleteLibrary(@Parameter(description = "ID of the library") @PathVariable long libraryId) {
        libraryService.deleteLibrary(libraryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get a book from a library", description = "Retrieve a specific book from a library by its ID.")
    @ApiResponse(responseCode = "200", description = "Book returned successfully")
    @GetMapping("/{libraryId}/book/{bookId}")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    public ResponseEntity<Book> getBook(
            @Parameter(description = "ID of the library") @PathVariable long libraryId,
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(libraryService.getBook(libraryId, bookId));
    }

    @Operation(summary = "Get all books from a library", description = "Retrieve all books from a specific library.")
    @ApiResponse(responseCode = "200", description = "Books returned successfully")
    @GetMapping("/{libraryId}/book")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    public ResponseEntity<List<Book>> getBooks(@Parameter(description = "ID of the library") @PathVariable long libraryId) {
        List<Book> books = libraryService.getBooks(libraryId);
        return ResponseEntity.ok(books);
    }

    @Operation(summary = "Rescan a library", description = "Rescan a library to refresh its contents. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "204", description = "Library rescanned successfully")
    @PutMapping("/{libraryId}/refresh")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<?> rescanLibrary(@Parameter(description = "ID of the library") @PathVariable long libraryId) {
        libraryService.rescanLibrary(libraryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set file naming pattern", description = "Set the file naming pattern for a library. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "200", description = "File naming pattern updated successfully")
    @PatchMapping("/{libraryId}/file-naming-pattern")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Library> setFileNamingPattern(
            @Parameter(description = "ID of the library") @PathVariable long libraryId,
            @Parameter(description = "File naming pattern body") @RequestBody Map<String, String> body) {
        String pattern = body.get("fileNamingPattern");
        Library updated = libraryService.setFileNamingPattern(libraryId, pattern);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Scan library paths", description = "Scan the provided library paths and return a count of processable files. Requires admin or manipulation permission.")
    @ApiResponse(responseCode = "200", description = "Processable files count returned successfully")
    @PostMapping("/scan")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Integer> scanLibraryPaths(
            @Parameter(description = "Library creation request with paths to scan")
            @Validated @RequestBody CreateLibraryRequest request) {
        int processableFilesCount = libraryService.scanLibraryPaths(request);
        return ResponseEntity.ok(processableFilesCount);
    }

    @Operation(summary = "Get book counts by format", description = "Get the count of books for each format in a library.")
    @ApiResponse(responseCode = "200", description = "Book counts returned successfully")
    @GetMapping("/{libraryId}/format-counts")
    @CheckLibraryAccess(libraryIdParam = "libraryId")
    public ResponseEntity<Map<String, Long>> getBookCountsByFormat(
            @Parameter(description = "ID of the library") @PathVariable long libraryId) {
        return ResponseEntity.ok(libraryService.getBookCountsByFormat(libraryId));
    }
}