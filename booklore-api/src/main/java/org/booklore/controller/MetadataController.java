package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.IsbnLookupRequest;
import org.booklore.model.dto.request.*;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.MetadataManagementService;
import org.booklore.service.metadata.MetadataMatchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
@AllArgsConstructor
@Tag(name = "Book Metadata", description = "Endpoints for managing book metadata, covers, and metadata operations")
public class MetadataController {

    private final BookMetadataService bookMetadataService;
    private final MetadataMatchService metadataMatchService;
    private final MetadataManagementService metadataManagementService;

    @Operation(summary = "Get prospective metadata for a book", description = "Fetch prospective metadata for a book by its ID. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Prospective metadata returned successfully")
    @PostMapping(value = "/{bookId}/metadata/prospective", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public Flux<BookMetadata> getMetadataList(
            @Parameter(description = "Fetch metadata request") @RequestBody(required = false) FetchMetadataRequest fetchMetadataRequest,
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return bookMetadataService.getProspectiveMetadataListForBookId(bookId, fetchMetadataRequest);
    }

    @Operation(summary = "Update book metadata", description = "Update metadata for a book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Metadata updated successfully")
    @PutMapping("/{bookId}/metadata")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> updateMetadata(
            @Parameter(description = "Metadata update wrapper") @Validated @RequestBody MetadataUpdateWrapper metadataUpdateWrapper,
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Merge categories") @RequestParam(defaultValue = "false") boolean mergeCategories,
            @Parameter(description = "Replace mode") @RequestParam(defaultValue = "REPLACE_ALL") MetadataReplaceMode replaceMode) {
        BookMetadata bookMetadata = bookMetadataService.updateBookMetadata(bookId, metadataUpdateWrapper, mergeCategories, replaceMode);
        return ResponseEntity.ok(bookMetadata);
    }

    @Operation(summary = "Bulk edit book metadata", description = "Bulk update metadata for multiple books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Bulk metadata updated successfully")
    @PutMapping("/bulk-edit-metadata")
    @PreAuthorize("@securityUtil.canBulkEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> bulkEditMetadata(@Parameter(description = "Bulk metadata update request") @Validated @RequestBody BulkMetadataUpdateRequest bulkMetadataUpdateRequest) {
        boolean mergeCategories = bulkMetadataUpdateRequest.isMergeCategories();
        boolean mergeMoods = bulkMetadataUpdateRequest.isMergeMoods();
        boolean mergeTags = bulkMetadataUpdateRequest.isMergeTags();
        bookMetadataService.bulkUpdateMetadata(bulkMetadataUpdateRequest, mergeCategories, mergeMoods, mergeTags);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle all metadata locks", description = "Toggle all metadata locks for books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Metadata locks toggled successfully")
    @PutMapping("/metadata/toggle-all-lock")
    @PreAuthorize("@securityUtil.canBulkLockUnlockMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleAllMetadata(@Parameter(description = "Toggle all lock request") @Validated @RequestBody ToggleAllLockRequest request) {
        return ResponseEntity.ok(bookMetadataService.toggleAllLock(request));
    }

    @Operation(summary = "Toggle field locks for metadata", description = "Toggle field locks for book metadata. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Field locks toggled successfully")
    @PutMapping("/metadata/toggle-field-locks")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleFieldLocks(@Parameter(description = "Toggle field locks request") @Validated @RequestBody ToggleFieldLocksRequest request) {
        bookMetadataService.toggleFieldLocks(request.getBookIds(), request.getFieldActions());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Recalculate metadata match scores", description = "Recalculate match scores for all metadata. Requires admin.")
    @ApiResponse(responseCode = "204", description = "Match scores recalculated successfully")
    @PostMapping("/metadata/recalculate-match-scores")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> recalculateMatchScores() {
        metadataMatchService.recalculateAllMatchScores();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consolidate metadata", description = "Merge metadata values. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Metadata consolidated successfully")
    @PostMapping("/metadata/manage/consolidate")
    @PreAuthorize("@securityUtil.canBulkEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> mergeMetadata(@Parameter(description = "Merge metadata request") @Validated @RequestBody MergeMetadataRequest request) {
        metadataManagementService.consolidateMetadata(request.getMetadataType(), request.getTargetValues(), request.getValuesToMerge());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete metadata values", description = "Delete metadata values. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Metadata deleted successfully")
    @PostMapping("/metadata/manage/delete")
    @PreAuthorize("@securityUtil.canBulkEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteMetadata(@Parameter(description = "Delete metadata request") @Validated @RequestBody DeleteMetadataRequest request) {
        metadataManagementService.deleteMetadata(request.getMetadataType(), request.getValuesToDelete());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lookup metadata by ISBN", description = "Fetch metadata for a book by ISBN. Requires library management permission or admin.")
    @ApiResponse(responseCode = "200", description = "Metadata found")
    @ApiResponse(responseCode = "404", description = "No metadata found for the given ISBN")
    @PostMapping("/metadata/isbn-lookup")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<BookMetadata> lookupByIsbn(@Validated @RequestBody IsbnLookupRequest request) {
        BookMetadata metadata = bookMetadataService.lookupByIsbn(request);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }

    @Operation(summary = "Get detailed metadata from provider", description = "Fetch full metadata details for a specific item from a provider. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Detailed metadata returned successfully")
    @GetMapping("/metadata/detail/{provider}/{providerItemId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<BookMetadata> getDetailedProviderMetadata(
            @Parameter(description = "Metadata provider") @PathVariable MetadataProvider provider,
            @Parameter(description = "Provider-specific item ID") @PathVariable String providerItemId) {
        BookMetadata metadata = bookMetadataService.getDetailedProviderMetadata(provider, providerItemId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }
}
