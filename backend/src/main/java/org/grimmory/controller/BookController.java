package org.grimmory.controller;

import org.grimmory.config.security.annotation.CheckBookAccess;
import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.BookRecommendation;
import org.grimmory.model.dto.BookViewerSettings;
import org.grimmory.model.dto.request.AttachBookFileRequest;
import org.grimmory.model.dto.request.CreatePhysicalBookRequest;
import org.grimmory.model.dto.request.DuplicateDetectionRequest;
import org.grimmory.model.dto.request.PersonalRatingUpdateRequest;
import org.grimmory.model.dto.request.ReadProgressRequest;
import org.grimmory.model.dto.request.ReadStatusUpdateRequest;
import org.grimmory.model.dto.request.ShelvesAssignmentRequest;
import org.grimmory.model.dto.response.AttachBookFileResponse;
import org.grimmory.model.dto.response.BookDeletionResponse;
import org.grimmory.model.dto.response.BookStatusUpdateResponse;
import org.grimmory.model.dto.response.DuplicateGroup;
import org.grimmory.model.dto.response.PersonalRatingUpdateResponse;
import org.grimmory.model.enums.ResetProgressType;
import org.grimmory.service.book.BookFileAttachmentService;
import org.grimmory.service.book.BookService;
import org.grimmory.service.book.BookUpdateService;
import org.grimmory.service.book.DuplicateDetectionService;
import org.grimmory.service.book.PhysicalBookService;
import org.grimmory.service.metadata.BookMetadataService;
import org.grimmory.service.progress.ReadingProgressService;
import org.grimmory.service.recommender.BookRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "Books", description = "Endpoints for managing books, their metadata, progress, and recommendations")
@RequestMapping("/api/v1/books")
@Validated
@RestController
@AllArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookUpdateService bookUpdateService;
    private final BookRecommendationService bookRecommendationService;
    private final BookFileAttachmentService bookFileAttachmentService;
    private final BookMetadataService bookMetadataService;
    private final ReadingProgressService readingProgressService;
    private final PhysicalBookService physicalBookService;
    private final DuplicateDetectionService duplicateDetectionService;

    @Operation(summary = "Get all books", description = "Retrieve a list of all books. Optionally include descriptions.")
    @ApiResponse(responseCode = "200", description = "List of books returned successfully")
    @GetMapping
    public ResponseEntity<List<Book>> getBooks(
            @Parameter(description = "Include book descriptions in the response")
            @RequestParam(required = false, defaultValue = "false") boolean withDescription,
            @Parameter(description = "Remove other metadata fields from the response")
            @RequestParam(required = false, defaultValue = "true") boolean stripForListView) {
        return ResponseEntity.ok(bookService.getBookDTOs(withDescription, stripForListView));
    }

    @Operation(summary = "Get books (paginated)", description = "Retrieve a paginated list of books. Supports sorting via 'sort' parameter (e.g. sort=metadata.title,asc).")
    @ApiResponse(responseCode = "200", description = "Page of books returned successfully")
    @GetMapping("/page")
    public ResponseEntity<Page<Book>> getBooksPaged(
            @Parameter(hidden = true) Pageable pageable) {
        return ResponseEntity.ok(bookService.getBookDTOsPaged(pageable));
    }

    @Operation(summary = "Get a book by ID", description = "Retrieve details of a specific book by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book details returned successfully"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Book> getBook(
            @Parameter(description = "ID of the book to retrieve") @PathVariable long bookId,
            @Parameter(description = "Include book description in the response") @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBook(bookId, withDescription));
    }

    @Operation(summary = "Create a physical book", description = "Create a physical book without digital files. Requires library management permission or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Physical book created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Library not found")
    })
    @PostMapping("/physical")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Book> createPhysicalBook(
            @Parameter(description = "Physical book creation request") @RequestBody @Valid CreatePhysicalBookRequest request) {
        return ResponseEntity.status(201).body(physicalBookService.createPhysicalBook(request));
    }

    @Operation(summary = "Delete books", description = "Delete one or more books by their IDs. Requires admin or delete permission.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    @DeleteMapping
    public ResponseEntity<BookDeletionResponse> deleteBooks(
            @Parameter(description = "Set of book IDs to delete") @RequestParam Set<Long> ids) {
        return bookService.deleteBooks(ids);
    }

    @Operation(summary = "Get books by IDs", description = "Retrieve multiple books by their IDs. Optionally include descriptions.")
    @ApiResponse(responseCode = "200", description = "Books returned successfully")
    @GetMapping("/batch")
    public ResponseEntity<List<Book>> getBooksByIds(
            @Parameter(description = "Set of book IDs to retrieve") @RequestParam Set<Long> ids,
            @Parameter(description = "Include book descriptions in the response") @RequestParam(required = false, defaultValue = "false") boolean withDescription) {
        return ResponseEntity.ok(bookService.getBooksByIds(ids, withDescription));
    }

    @Operation(summary = "Get ComicInfo metadata", description = "Retrieve ComicInfo metadata for a specific book.")
    @ApiResponse(responseCode = "200", description = "ComicInfo metadata returned successfully")
    @GetMapping("/{bookId}/cbx/metadata/comicinfo")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<?> getComicInfoMetadata(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookMetadataService.getComicInfoMetadata(bookId));
    }

    @Operation(summary = "Get file metadata", description = "Extract embedded metadata from the book file.")
    @ApiResponse(responseCode = "200", description = "File metadata returned successfully")
    @GetMapping("/{bookId}/file-metadata")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<?> getFileMetadata(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookMetadataService.getFileMetadata(bookId));
    }

    @Operation(summary = "Get book content", description = "Retrieve the binary content of a book for reading. Supports HTTP Range requests for partial content streaming.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full book content returned"),
            @ApiResponse(responseCode = "206", description = "Partial content returned for Range request")
    })
    @GetMapping("/{bookId}/content")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookContent(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., EPUB, PDF, MOBI)") @RequestParam(required = false) String bookType
            ) {
        return bookService.getBookContent(bookId, bookType);
    }

    @Operation(summary = "Replace book content", description = "Overwrite the primary PDF file for a book with the uploaded content. Used by the document viewer to persist annotation changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Book content replaced successfully"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PutMapping("/{bookId}/content")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> replaceBookContent(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType,
            HttpServletRequest request) throws java.io.IOException {
        bookService.replaceBookContent(bookId, bookType, request.getInputStream());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Download book", description = "Download the book file. Requires download permission or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book downloaded successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/{bookId}/download")
    @PreAuthorize("@securityUtil.canDownload() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> downloadBook(@Parameter(description = "ID of the book to download") @PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Download all book files", description = "Download all files for a book as a ZIP archive. For single-file books, downloads the file directly. Requires download permission or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Files downloaded successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}/download-all")
    @PreAuthorize("@securityUtil.canDownload() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void downloadAllBookFiles(
            @Parameter(description = "ID of the book") @PathVariable("bookId") Long bookId,
            HttpServletResponse response) {
        bookService.downloadAllBookFiles(bookId, response);
    }

    @Operation(summary = "Get viewer settings", description = "Retrieve viewer settings for a specific book file.")
    @ApiResponse(responseCode = "200", description = "Viewer settings returned successfully")
    @GetMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookViewerSettings> getBookViewerSettings(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "ID of the book file") @RequestParam long bookFileId) {
        return ResponseEntity.ok(bookService.getBookViewerSetting(bookId, bookFileId));
    }

    @Operation(summary = "Update viewer settings", description = "Update viewer settings for a specific book.")
    @ApiResponse(responseCode = "204", description = "Viewer settings updated successfully")
    @PutMapping("/{bookId}/viewer-setting")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> updateBookViewerSettings(
            @Parameter(description = "Viewer settings to update") @RequestBody @Valid BookViewerSettings bookViewerSettings,
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        bookService.updateBookViewerSetting(bookId, bookViewerSettings);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign books to shelves", description = "Assign or unassign books to/from shelves.")
    @ApiResponse(responseCode = "200", description = "Books assigned/unassigned to shelves successfully")
    @PostMapping("/shelves")
    public ResponseEntity<List<Book>> addBookToShelf(
            @Parameter(description = "Shelves assignment request") @RequestBody @Valid ShelvesAssignmentRequest request) {
        return ResponseEntity.ok(bookService.assignShelvesToBooks(request.getBookIds(), request.getShelvesToAssign(), request.getShelvesToUnassign()));
    }

    @Operation(summary = "Update read progress", description = "Update the read progress for a book.")
    @ApiResponse(responseCode = "204", description = "Read progress updated successfully")
    @PostMapping("/progress")
    public ResponseEntity<Void> addBookToProgress(
            @Parameter(description = "Read progress request") @RequestBody @Valid ReadProgressRequest request) {
        bookService.updateReadProgress(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get book recommendations", description = "Get recommended books based on a specific book.")
    @ApiResponse(responseCode = "200", description = "Recommendations returned successfully")
    @GetMapping("/{id}/recommendations")
    @CheckBookAccess(bookIdParam = "id")
    public ResponseEntity<List<BookRecommendation>> getRecommendations(
            @Parameter(description = "ID of the book for recommendations") @PathVariable Long id,
            @Parameter(description = "Maximum number of recommendations to return (max 25)") @RequestParam(defaultValue = "25") @Max(25) @Min(1) int limit) {
        return ResponseEntity.ok(bookRecommendationService.getRecommendations(id, limit));
    }

    @Operation(summary = "Update read status", description = "Update the read status for one or more books.")
    @ApiResponse(responseCode = "200", description = "Read status updated successfully")
    @PostMapping("/status")
    public List<BookStatusUpdateResponse> updateReadStatus(@RequestBody @Valid ReadStatusUpdateRequest request) {
        return bookService.updateReadStatus(request.getBookIds(), request.getStatus());
    }

    @Operation(summary = "Reset reading progress", description = "Reset the reading progress for one or more books.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress reset successfully"),
            @ApiResponse(responseCode = "400", description = "No book IDs provided")
    })
    @PostMapping("/reset-progress")
    public ResponseEntity<List<BookStatusUpdateResponse>> resetProgress(
            @Parameter(description = "List of book IDs to reset progress for") @RequestBody @Size(max = 500) List<Long> bookIds,
            @Parameter(description = "Type of progress reset") @RequestParam ResetProgressType type) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("No book IDs provided");
        }
        return ResponseEntity.ok(readingProgressService.resetProgress(bookIds, type));
    }

    @Operation(summary = "Update personal rating", description = "Update the personal rating for one or more books.")
    @ApiResponse(responseCode = "200", description = "Personal rating updated successfully")
    @PutMapping("/personal-rating")
    public ResponseEntity<List<PersonalRatingUpdateResponse>> updatePersonalRating(
            @Parameter(description = "Personal rating update request") @RequestBody @Valid PersonalRatingUpdateRequest request) {
        return ResponseEntity.ok(bookUpdateService.updatePersonalRating(request.ids(), request.rating()));
    }

    @Operation(summary = "Reset personal rating", description = "Reset the personal rating for one or more books.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personal rating reset successfully"),
            @ApiResponse(responseCode = "400", description = "No book IDs provided")
    })
    @PostMapping("/reset-personal-rating")
    public ResponseEntity<List<PersonalRatingUpdateResponse>> resetPersonalRating(
            @Parameter(description = "List of book IDs to reset personal rating for") @RequestBody @Size(max = 500) List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("No book IDs provided");
        }
        List<PersonalRatingUpdateResponse> updatedBooks = bookUpdateService.resetPersonalRating(bookIds);
        return ResponseEntity.ok(updatedBooks);
    }

    @Operation(summary = "Find duplicate books", description = "Detect potential duplicate books in a library using configurable matching strategies.")
    @ApiResponse(responseCode = "200", description = "Duplicate groups returned successfully")
    @PostMapping("/duplicates")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<List<DuplicateGroup>> findDuplicates(
            @Parameter(description = "Duplicate detection configuration") @RequestBody @Valid DuplicateDetectionRequest request) {
        return ResponseEntity.ok(duplicateDetectionService.findDuplicates(request));
    }

    @Operation(summary = "Toggle physical book flag", description = "Mark or unmark a book as a physical book.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Physical flag updated successfully"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PatchMapping("/{bookId}/physical")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<Book> togglePhysicalFlag(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Whether the book is physical") @RequestParam boolean physical) {
        return ResponseEntity.ok(physicalBookService.togglePhysicalFlag(bookId, physical));
    }

    @Operation(summary = "Attach book files", description = "Attach book files from single-file source books to a target book as alternative formats.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book files attached successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - books must be in same library, sources must have exactly one file each"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires library management permission"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PostMapping("/{targetBookId}/attach-file")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<AttachBookFileResponse> attachBookFiles(
            @Parameter(description = "ID of the target book to attach the files to") @PathVariable Long targetBookId,
            @Parameter(description = "Request containing source book IDs and delete option") @RequestBody @Valid AttachBookFileRequest request) {
        return ResponseEntity.ok(bookFileAttachmentService.attachBookFiles(targetBookId, request.getSourceBookIds(), request.isMoveFiles()));
    }
}
