package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.response.PdfBookInfo;
import org.booklore.service.reader.PdfReaderService;
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

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF Reader", description = "Endpoints for reading PDF format books")
public class PdfReaderController {

    private final PdfReaderService pdfReaderService;

    @Operation(summary = "List pages in a PDF book", description = "Retrieve a list of available page numbers for a PDF book.")
    @ApiResponse(responseCode = "200", description = "Page numbers returned successfully")
    @GetMapping("/{bookId}/pages")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<Integer>> listPages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest request) throws java.io.IOException {
        long lastModified = pdfReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        List<Integer> pages = pdfReaderService.getAvailablePages(bookId, bookType);
        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate());
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(pages);
    }

    @Operation(summary = "Get book info for a PDF book", description = "Retrieve book information including page count and hierarchical outline/table of contents.")
    @ApiResponse(responseCode = "200", description = "Book info returned successfully")
    @GetMapping("/{bookId}/info")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<PdfBookInfo> getBookInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest request) throws java.io.IOException {
        long lastModified = pdfReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        PdfBookInfo info = pdfReaderService.getBookInfo(bookId, bookType);
        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate());
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(info);
    }
}
