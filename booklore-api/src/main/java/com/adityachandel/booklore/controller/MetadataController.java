package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.CoverImage;
import com.adityachandel.booklore.model.dto.request.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.*;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/books")
@AllArgsConstructor
@Tag(name = "Book Metadata", description = "Endpoints for managing book metadata, covers, and metadata operations")
public class MetadataController {

    private final BookMetadataService bookMetadataService;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final AuthenticationService authenticationService;
    private final BookMetadataMapper bookMetadataMapper;
    private final MetadataMatchService metadataMatchService;
    private final DuckDuckGoCoverService duckDuckGoCoverService;
    private final BookRepository bookRepository;
    private final MetadataManagementService metadataManagementService;

    @Operation(summary = "Get prospective metadata for a book", description = "Fetch prospective metadata for a book by its ID. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Prospective metadata returned successfully")
    @PostMapping("/{bookId}/metadata/prospective")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<BookMetadata>> getMetadataList(
            @Parameter(description = "Fetch metadata request") @RequestBody(required = false) FetchMetadataRequest fetchMetadataRequest,
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(bookMetadataService.getProspectiveMetadataListForBookId(bookId, fetchMetadataRequest));
    }

    @Operation(summary = "Update book metadata", description = "Update metadata for a book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Metadata updated successfully")
    @PutMapping("/{bookId}/metadata")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> updateMetadata(
            @Parameter(description = "Metadata update wrapper") @RequestBody MetadataUpdateWrapper metadataUpdateWrapper,
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Merge categories") @RequestParam(defaultValue = "true") boolean mergeCategories) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(metadataUpdateWrapper)
                .updateThumbnail(true)
                .mergeCategories(mergeCategories)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeMoods(false)
                .mergeTags(false)
                .build();

        bookMetadataUpdater.setBookMetadata(context);
        bookRepository.save(bookEntity);
        BookMetadata bookMetadata = bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
        return ResponseEntity.ok(bookMetadata);
    }

    @Operation(summary = "Bulk edit book metadata", description = "Bulk update metadata for multiple books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Bulk metadata updated successfully")
    @PutMapping("/bulk-edit-metadata")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> bulkEditMetadata(
            @Parameter(description = "Bulk metadata update request") @RequestBody BulkMetadataUpdateRequest bulkMetadataUpdateRequest) {
        boolean mergeCategories = bulkMetadataUpdateRequest.isMergeCategories();
        boolean mergeMoods = bulkMetadataUpdateRequest.isMergeMoods();
        boolean mergeTags = bulkMetadataUpdateRequest.isMergeTags();
        bookMetadataService.bulkUpdateMetadata(bulkMetadataUpdateRequest, mergeCategories, mergeMoods, mergeTags);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload cover image from file", description = "Upload a cover image for a book from a file. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/upload")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> uploadCoverFromFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file) {
        BookMetadata updated = bookMetadataService.updateCoverImageFromFile(bookId, file);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Upload cover image from URL", description = "Upload a cover image for a book from a URL. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/from-url")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> uploadCoverFromUrl(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "URL body") @RequestBody Map<String, String> body) {
        BookMetadata updated = bookMetadataService.updateCoverImageFromUrl(bookId, body.get("url"));
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Toggle all metadata locks", description = "Toggle all metadata locks for books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Metadata locks toggled successfully")
    @PutMapping("/metadata/toggle-all-lock")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleAllMetadata(
            @Parameter(description = "Toggle all lock request") @RequestBody ToggleAllLockRequest request) {
        return ResponseEntity.ok(bookMetadataService.toggleAllLock(request));
    }

    @Operation(summary = "Toggle field locks for metadata", description = "Toggle field locks for book metadata. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Field locks toggled successfully")
    @PutMapping("/metadata/toggle-field-locks")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleFieldLocks(
            @Parameter(description = "Toggle field locks request") @RequestBody ToggleFieldLocksRequest request) {
        bookMetadataService.toggleFieldLocks(request.getBookIds(), request.getFieldActions());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Regenerate all covers", description = "Regenerate covers for all books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Covers regenerated successfully")
    @PostMapping("/regenerate-covers")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public void regenerateCovers() {
        bookMetadataService.regenerateCovers();
    }

    @Operation(summary = "Regenerate cover for a book", description = "Regenerate cover for a specific book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regenerated successfully")
    @PostMapping("/{bookId}/regenerate-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void regenerateCovers(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookMetadataService.regenerateCover(bookId);
    }

    @Operation(summary = "Regenerate covers for selected books", description = "Regenerate covers for a list of books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regeneration started successfully")
    @PostMapping("/bulk-regenerate-covers")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> regenerateCoversForBooks(
            @Parameter(description = "List of book IDs") @Validated @RequestBody BulkBookIdsRequest request) {
        bookMetadataService.regenerateCoversForBooks(request.getBookIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload cover image for multiple books", description = "Upload a cover image to apply to multiple books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover upload started successfully")
    @PostMapping("/bulk-upload-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> bulkUploadCover(
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Comma-separated book IDs") @RequestParam("bookIds") @jakarta.validation.constraints.NotEmpty java.util.Set<Long> bookIds) {
        bookMetadataService.updateCoverImageFromFileForBooks(bookIds, file);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Recalculate metadata match scores", description = "Recalculate match scores for all metadata. Requires admin.")
    @ApiResponse(responseCode = "204", description = "Match scores recalculated successfully")
    @PostMapping("/metadata/recalculate-match-scores")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> recalculateMatchScores() {
        metadataMatchService.recalculateAllMatchScores();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get cover images for a book", description = "Fetch cover images for a book.")
    @ApiResponse(responseCode = "200", description = "Cover images returned successfully")
    @PostMapping("/{bookId}/metadata/covers")
    public ResponseEntity<List<CoverImage>> getImages(
            @Parameter(description = "Cover fetch request") @RequestBody CoverFetchRequest request) {
        return ResponseEntity.ok(duckDuckGoCoverService.getCovers(request));
    }

    @Operation(summary = "Consolidate metadata", description = "Merge metadata values. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Metadata consolidated successfully")
    @PostMapping("/metadata/manage/consolidate")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> mergeMetadata(
            @Parameter(description = "Merge metadata request") @Validated @RequestBody MergeMetadataRequest request) {
        metadataManagementService.consolidateMetadata(request.getMetadataType(), request.getTargetValues(), request.getValuesToMerge());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete metadata values", description = "Delete metadata values. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Metadata deleted successfully")
    @PostMapping("/metadata/manage/delete")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteMetadata(
            @Parameter(description = "Delete metadata request") @Validated @RequestBody DeleteMetadataRequest request) {
        metadataManagementService.deleteMetadata(request.getMetadataType(), request.getValuesToDelete());
        return ResponseEntity.noContent().build();
    }
}