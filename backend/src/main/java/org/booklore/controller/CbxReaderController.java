package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.response.CbxPageDimension;
import org.booklore.model.dto.response.CbxPageInfo;
import org.booklore.service.reader.CbxReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cbx")
@RequiredArgsConstructor
@Tag(name = "CBX Reader", description = "Endpoints for reading CBX format books")
public class CbxReaderController {

    private final CbxReaderService cbxReaderService;

    @Operation(summary = "List pages in a CBX book", description = "Retrieve a list of available page numbers for a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page numbers returned successfully")
    @GetMapping("/{bookId}/pages")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<Integer>> listPages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest request) throws IOException {
        long lastModified = cbxReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        List<Integer> pages = cbxReaderService.getAvailablePages(bookId, bookType);
        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate());
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(pages);
    }

    @Operation(summary = "Get page info for a CBX book", description = "Retrieve page information including display names for a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page info returned successfully")
    @GetMapping("/{bookId}/page-info")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<CbxPageInfo>> getPageInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest request) throws IOException {
        long lastModified = cbxReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        List<CbxPageInfo> info = cbxReaderService.getPageInfo(bookId, bookType);
        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate());
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(info);
    }

    @Operation(summary = "Get page dimensions for a CBX book", description = "Retrieve width, height, and wide flag for each page in a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page dimensions returned successfully")
    @GetMapping("/{bookId}/page-dimensions")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<CbxPageDimension>> getPageDimensions(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest request) throws IOException {
        long lastModified = cbxReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        List<CbxPageDimension> dimensions = cbxReaderService.getPageDimensions(bookId, bookType);
        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate());
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(dimensions);
    }
}