package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.service.reader.EpubReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import org.springframework.web.servlet.HandlerMapping;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/epub")
@RequiredArgsConstructor
@Tag(name = "EPUB Reader", description = "Endpoints for reading EPUB format books with streaming support")
public class EpubReaderController {

    private final EpubReaderService epubReaderService;

    @Operation(summary = "Get EPUB book info",
            description = "Retrieve parsed metadata, spine, manifest, and TOC for an EPUB book.")
    @ApiResponse(responseCode = "200", description = "Book info returned successfully")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/info")
    public ResponseEntity<EpubBookInfo> getBookInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., EPUB)") @RequestParam(required = false) String bookType,
            WebRequest request) throws IOException {
        long lastModified = epubReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        EpubBookInfo info = epubReaderService.getBookInfo(bookId, bookType);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate())
                .eTag(etag)
                .body(info);
    }

    @Operation(summary = "Get file from EPUB", description = "Retrieve a specific file from within the EPUB archive (HTML, CSS, images, fonts, etc.).")
    @ApiResponse(responseCode = "200", description = "File content returned successfully")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/file/{*filePath}")
    public void getFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @PathVariable String filePath,
            @Parameter(description = "Optional book type for alternative format (e.g., EPUB)") @RequestParam(required = false) String bookType,
            WebRequest request,
            HttpServletResponse response) throws IOException {

        long lastModified = epubReaderService.getLastModified(bookId, bookType);
        String etag = lastModified > 0L ? Long.toHexString(lastModified) : null;

        if (etag != null && request.checkNotModified(etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader(HttpHeaders.ETAG, etag);
            return;
        }

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        cleanPath = URLDecoder.decode(cleanPath, StandardCharsets.UTF_8);

        String contentType = epubReaderService.getContentType(bookId, bookType, cleanPath);
        response.setContentType(contentType);

        long fileSize = epubReaderService.getFileSize(bookId, bookType, cleanPath);
        if (fileSize > 0) {
            response.setContentLengthLong(fileSize);
        }

        if (contentType.startsWith("font/") ||
                "application/font-woff".equals(contentType) ||
                "application/font-woff2".equals(contentType) ||
                "application/vnd.ms-fontobject".equals(contentType)) {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }
        // Defense in depth for untrusted EPUB resources. See Foliate's security guidance:
        // https://github.com/johnfactotum/foliate-js#security
        response.setHeader("Content-Security-Policy", "script-src 'none'");
        response.setHeader(HttpHeaders.CACHE_CONTROL,
                CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().mustRevalidate().getHeaderValue());
        response.setHeader(HttpHeaders.ETAG, etag);

        try {
            epubReaderService.streamFile(bookId, bookType, cleanPath, response.getOutputStream());
        } catch (FileNotFoundException e) {
            response.reset();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
