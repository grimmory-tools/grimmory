package org.grimmory.controller;

import org.grimmory.config.security.annotation.CheckBookAccess;
import org.grimmory.model.dto.CoverImage;
import org.grimmory.model.dto.request.BulkBookIdsRequest;
import org.grimmory.model.dto.request.CoverFetchRequest;
import org.grimmory.service.metadata.BookCoverService;
import org.grimmory.service.metadata.DuckDuckGoCoverService;
import org.grimmory.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/books")
@AllArgsConstructor
@Tag(name = "Book Metadata", description = "Endpoints for managing book metadata, covers, and metadata operations")
public class BookCoverController {

    private final BookCoverService bookCoverService;
    private final DuckDuckGoCoverService duckDuckGoCoverService;

    @Operation(summary = "Upload cover image from file", description = "Upload a cover image for a book from a file. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/upload")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadCoverFromFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file) {
        bookCoverService.updateCoverFromFile(bookId, file);
    }

    @Operation(summary = "Upload cover image from URL", description = "Upload a cover image for a book from a URL. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/cover/from-url")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadCoverFromUrl(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "URL body") @RequestBody Map<String, String> body) {
        bookCoverService.updateCoverFromUrl(bookId, body.get("url"));
    }

    @Operation(summary = "Upload audiobook cover image from file", description = "Upload an audiobook cover image for a book from a file. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Audiobook cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/audiobook-cover/upload")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadAudiobookCoverFromFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file) {
        bookCoverService.updateAudiobookCoverFromFile(bookId, file);
    }

    @Operation(summary = "Upload audiobook cover image from URL", description = "Upload an audiobook cover image for a book from a URL. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Audiobook cover image uploaded successfully")
    @PostMapping("/{bookId}/metadata/audiobook-cover/from-url")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void uploadAudiobookCoverFromUrl(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "URL body") @RequestBody Map<String, String> body) {
        bookCoverService.updateAudiobookCoverFromUrl(bookId, body.get("url"));
    }

    @Operation(summary = "Regenerate audiobook cover for a book", description = "Regenerate audiobook cover for a specific book by extracting from the audiobook file. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Audiobook cover regenerated successfully")
    @PostMapping("/{bookId}/regenerate-audiobook-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void regenerateAudiobookCover(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.regenerateAudiobookCover(bookId);
    }

    @Operation(summary = "Generate custom audiobook cover for a book", description = "Generate a custom audiobook cover for a specific book based on its metadata. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Custom audiobook cover generated successfully")
    @PostMapping("/{bookId}/generate-custom-audiobook-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void generateCustomAudiobookCover(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.generateCustomAudiobookCover(bookId);
    }

    @Operation(summary = "Regenerate all covers", description = "Regenerate covers for all books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Covers regenerated successfully")
    @PostMapping("/regenerate-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void regenerateCovers(@RequestParam(defaultValue = "false") boolean missingOnly) {
        bookCoverService.regenerateCovers(missingOnly);
    }

    @Operation(summary = "Regenerate cover for a book", description = "Regenerate cover for a specific book. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regenerated successfully")
    @PostMapping("/{bookId}/regenerate-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void regenerateCovers(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.regenerateCover(bookId);
    }

    @Operation(summary = "Generate custom cover for a book", description = "Generate a custom cover for a specific book based on its metadata. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Custom cover generated successfully")
    @PostMapping("/{bookId}/generate-custom-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void generateCustomCover(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        bookCoverService.generateCustomCover(bookId);
    }

    @Operation(summary = "Regenerate covers for selected books", description = "Regenerate covers for a list of books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover regeneration started successfully")
    @PostMapping("/bulk-regenerate-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void regenerateCoversForBooks(@Parameter(description = "List of book IDs") @Validated @RequestBody BulkBookIdsRequest request) {
        bookCoverService.regenerateCoversForBooks(request.getBookIds());
    }

    @Operation(summary = "Generate custom covers for selected books", description = "Generate custom covers for a list of books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Custom cover generation started successfully")
    @PostMapping("/bulk-generate-custom-covers")
    @PreAuthorize("@securityUtil.canBulkRegenerateCover() or @securityUtil.isAdmin()")
    public void generateCustomCoversForBooks(@Parameter(description = "List of book IDs") @Validated @RequestBody BulkBookIdsRequest request) {
        bookCoverService.generateCustomCoversForBooks(request.getBookIds());
    }

    @Operation(summary = "Upload cover image for multiple books", description = "Upload a cover image to apply to multiple books. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Cover upload started successfully")
    @PostMapping("/bulk-upload-cover")
    @PreAuthorize("@securityUtil.canBulkEditMetadata() or @securityUtil.isAdmin()")
    public void bulkUploadCover(
            @Parameter(description = "Cover image file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Comma-separated book IDs") @RequestParam("bookIds") @jakarta.validation.constraints.NotEmpty java.util.Set<Long> bookIds) {
        bookCoverService.updateCoverFromFileForBooks(bookIds, file);
    }

    @Operation(summary = "Get cover images for a book", description = "Fetch cover images for a book.")
    @ApiResponse(responseCode = "200", description = "Cover images returned successfully")
    @PostMapping(value = "/{bookId}/metadata/covers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public Flux<ServerSentEvent<CoverImage>> getImages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Cover fetch request") @RequestBody CoverFetchRequest request) {
        return duckDuckGoCoverService.getCovers(request)
                .subscribeOn(Schedulers.boundedElastic())
                .map(image -> ServerSentEvent.<CoverImage>builder()
                        .data(image)
                        .build())
                .onErrorMap(e -> ApiError.INTERNAL_SERVER_ERROR.createException("DuckDuckGo cover fetch failed"));
    }
}