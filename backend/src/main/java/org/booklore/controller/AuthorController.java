package org.booklore.controller;

import org.booklore.browse.BrowsePage;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.AuthorDetails;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.AuthorMatchRequest;
import org.booklore.model.dto.request.AuthorUpdateRequest;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.service.AuthorMetadataService;
import org.booklore.service.AuthorService;
import org.booklore.service.browse.AuthorBrowseService;
import org.booklore.service.browse.AuthorFacetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Authors", description = "Endpoints for retrieving authors related to books")
@RequestMapping("/api/v1/authors")
@RestController
@AllArgsConstructor
public class AuthorController {

    private final AuthorService authorService;
    private final AuthorMetadataService authorMetadataService;
    private final AuthorBrowseService authorBrowseService;
    private final AuthorFacetService authorFacetService;

    @Operation(summary = "Get all authors", description = "Retrieve all authors with book counts.")
    @ApiResponse(responseCode = "200", description = "Authors returned successfully")
    @GetMapping
    public ResponseEntity<List<AuthorSummary>> getAllAuthors() {
        return ResponseEntity.ok(authorMetadataService.getAllAuthors());
    }

    @Operation(summary = "Get authors (paginated)", description = "Retrieve a page of authors with book counts. Supports cursor pagination plus sort, facet, facet_logic, and query parameters. Facets: matched, has_photo, has_description (true/false); read_status (same ReadStatus values as the books browse, matching authors with at least one such book); book_count (count or range, e.g. 4, 2-10, 5-, -3); library (library id); genre and language (values from the facets endpoint). Rating sort keys share the books browse names (e.g. amazonRating) and average across the author's visible books.")
    @ApiResponse(responseCode = "200", description = "Page of authors returned successfully")
    @GetMapping("/page")
    public ResponseEntity<BrowsePage<AuthorSummary>> getAuthorsPaged(
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(description = "Comma-separated sort keys; prefix a key with '-' for descending (e.g. sortName or -bookCount)")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Facet selection in key:value form; repeatable (e.g. facet=matched:false&facet=genre:Horror)")
            @RequestParam(required = false) List<String> facet,
            @Parameter(description = "How facet values combine within a group: and, or, or not")
            @RequestParam(name = "facet_logic", required = false) String facetLogic,
            @Parameter(description = "Free-text search on author name and ASIN")
            @RequestParam(required = false) String query,
            @Parameter(description = "Opaque pagination cursor from a prior response's links")
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(authorBrowseService.browse(sort, facet, facetLogic, query, cursor, pageable));
    }

    @Operation(summary = "Get author facets", description = "Available facet values and author counts for the current user, scoped by the same facet, facet_logic, and query parameters as the page endpoint. Each facet omits its own selections from its counts. book_count, library, and read_status are filter-only and not enumerated here.")
    @ApiResponse(responseCode = "200", description = "Facet groups returned successfully")
    @GetMapping("/facets")
    public ResponseEntity<FacetGroupsResponse> getAuthorFacets(
            @Parameter(description = "Facet selection in key:value form; repeatable")
            @RequestParam(required = false) List<String> facet,
            @Parameter(description = "How facet values combine within a group: and, or, or not")
            @RequestParam(name = "facet_logic", required = false) String facetLogic,
            @Parameter(description = "Free-text search applied to the counts")
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(authorFacetService.getFacets(facet, facetLogic, query));
    }

    @Operation(summary = "Get matching author ids", description = "Returns every author id matching the given sort, facet, facet_logic, and query parameters, in sort order. For select-all over the current filters.")
    @ApiResponse(responseCode = "200", description = "Matching author ids returned successfully")
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getAuthorIds(
            @Parameter(description = "Comma-separated sort keys; prefix a key with '-' for descending")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Facet selection in key:value form; repeatable")
            @RequestParam(required = false) List<String> facet,
            @Parameter(description = "How facet values combine within a group: and, or, or not")
            @RequestParam(name = "facet_logic", required = false) String facetLogic,
            @Parameter(description = "Free-text search")
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(authorBrowseService.findAllIds(sort, facet, facetLogic, query));
    }

    @Operation(summary = "Find author by name", description = "Find an author by exact name (case-insensitive).")
    @ApiResponse(responseCode = "200", description = "Author found successfully")
    @GetMapping("/by-name")
    public ResponseEntity<AuthorDetails> getAuthorByName(
            @Parameter(description = "Author name") @RequestParam("name") String name) {
        return ResponseEntity.ok(authorMetadataService.getAuthorByName(name));
    }

    @Operation(summary = "Get authors by book ID", description = "Retrieve a list of authors for a specific book.")
    @ApiResponse(responseCode = "200", description = "Authors returned successfully")
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<String>> getAuthorsByBookId(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(authorService.getAuthorsByBookId(bookId));
    }

    @Operation(summary = "Get author details", description = "Retrieve author details including metadata.")
    @ApiResponse(responseCode = "200", description = "Author details returned successfully")
    @GetMapping("/{authorId}")
    public ResponseEntity<AuthorDetails> getAuthorDetails(
            @Parameter(description = "ID of the author") @PathVariable long authorId) {
        return ResponseEntity.ok(authorMetadataService.getAuthorDetails(authorId));
    }

    @Operation(summary = "Update author", description = "Update author details such as name, description, and ASIN.")
    @ApiResponse(responseCode = "200", description = "Author updated successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PutMapping("/{authorId}")
    public ResponseEntity<AuthorDetails> updateAuthor(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @RequestBody @Valid AuthorUpdateRequest request) {
        return ResponseEntity.ok(authorMetadataService.updateAuthor(authorId, request));
    }

    @Operation(summary = "Upload author photo", description = "Upload a photo for an author from a file.")
    @ApiResponse(responseCode = "200", description = "Author photo uploaded successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/{authorId}/photo/upload")
    public ResponseEntity<Void> uploadAuthorPhoto(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @Parameter(description = "Photo image file") @RequestParam("file") MultipartFile file) {
        authorMetadataService.uploadAuthorPhoto(authorId, file);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Search author metadata", description = "Search external providers for author metadata candidates.")
    @ApiResponse(responseCode = "200", description = "Search results returned successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @GetMapping("/{authorId}/search-metadata")
    public ResponseEntity<List<AuthorSearchResult>> searchAuthorMetadata(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @Parameter(description = "Author name to search") @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Author ASIN to look up") @RequestParam(required = false) String asin,
            @Parameter(description = "Region for provider lookup") @RequestParam(defaultValue = "us") String region) {
        if (asin != null && !asin.isBlank()) {
            return ResponseEntity.ok(authorMetadataService.lookupAuthorByAsin(asin.trim(), region));
        }
        if (query == null || query.isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Either 'q' or 'asin' parameter is required");
        }
        return ResponseEntity.ok(authorMetadataService.searchAuthorMetadata(query, region));
    }

    @Operation(summary = "Match author metadata", description = "Match an author with an external provider result to populate metadata.")
    @ApiResponse(responseCode = "200", description = "Author matched successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/{authorId}/match")
    public ResponseEntity<AuthorDetails> matchAuthor(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @RequestBody @Valid AuthorMatchRequest request) {
        return ResponseEntity.ok(authorMetadataService.matchAuthor(authorId, request));
    }

    @Operation(summary = "Quick-match author", description = "Search and match an author with the first result in a single call.")
    @ApiResponse(responseCode = "200", description = "Author quick-matched successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/{authorId}/quick-match")
    public ResponseEntity<AuthorDetails> quickMatchAuthor(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @RequestParam(defaultValue = "us") String region) {
        return ResponseEntity.ok(authorMetadataService.quickMatchAuthor(authorId, region));
    }

    @Operation(summary = "Auto-match authors", description = "Automatically match multiple authors, streaming results as each is matched.")
    @ApiResponse(responseCode = "200", description = "Authors auto-matched successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping(value = "/auto-match", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AuthorSummary> autoMatchAuthors(@RequestBody List<Long> authorIds) {
        return authorMetadataService.autoMatchAuthors(authorIds);
    }

    @Operation(summary = "Unmatch authors", description = "Clear metadata for multiple authors.")
    @ApiResponse(responseCode = "200", description = "Authors unmatched successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/unmatch")
    public ResponseEntity<Void> unmatchAuthors(@RequestBody List<Long> authorIds) {
        authorMetadataService.unmatchAuthors(authorIds);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Search author photos", description = "Search for author photos using DuckDuckGo image search.")
    @ApiResponse(responseCode = "200", description = "Photo search results returned successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @GetMapping(value = "/{authorId}/search-photos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CoverImage> searchAuthorPhotos(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @Parameter(description = "Author name to search") @RequestParam("q") String query) {
        return authorMetadataService.searchAuthorPhotos(query);
    }

    @Operation(summary = "Upload author photo from URL", description = "Download an image from a URL and save it as the author's photo.")
    @ApiResponse(responseCode = "200", description = "Author photo saved successfully")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/{authorId}/photo/url")
    public ResponseEntity<Void> uploadAuthorPhotoFromUrl(
            @Parameter(description = "ID of the author") @PathVariable long authorId,
            @RequestParam("url") String imageUrl) {
        authorMetadataService.uploadAuthorPhotoFromUrl(authorId, imageUrl);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete authors", description = "Delete multiple authors and their associated images.")
    @ApiResponse(responseCode = "200", description = "Authors deleted successfully")
    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    @DeleteMapping
    public ResponseEntity<Void> deleteAuthors(@RequestBody List<Long> authorIds) {
        authorMetadataService.deleteAuthors(authorIds);
        return ResponseEntity.ok().build();
    }
}
