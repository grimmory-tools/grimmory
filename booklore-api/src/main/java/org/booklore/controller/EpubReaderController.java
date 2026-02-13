package org.booklore.controller;

import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.service.reader.EpubReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.HandlerMapping;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/epub")
@RequiredArgsConstructor
@Tag(name = "EPUB Reader", description = "Endpoints for reading EPUB format books with streaming support")
public class EpubReaderController {

    private final EpubReaderService epubReaderService;

    @Operation(summary = "Get EPUB book info",
            description = "Retrieve parsed metadata, spine, manifest, and TOC for an EPUB book.")
    @ApiResponse(responseCode = "200", description = "Book info returned successfully")
    @GetMapping("/{bookId}/info")
    public ResponseEntity<EpubBookInfo> getBookInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., EPUB)") @RequestParam(required = false) String bookType) {
        return ResponseEntity.ok(epubReaderService.getBookInfo(bookId, bookType));
    }

    @Operation(summary = "Get file from EPUB", description = "Retrieve a specific file from within the EPUB archive (HTML, CSS, images, fonts, etc.).")
    @ApiResponse(responseCode = "200", description = "File content returned successfully")
    @GetMapping("/{bookId}/file/**")
    public void getFile(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., EPUB)") @RequestParam(required = false) String bookType,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/api/v1/epub/" + bookId + "/file/";
        String filePath = fullPath.substring(prefix.length());
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);

        String contentType = epubReaderService.getContentType(bookId, bookType, filePath);
        response.setContentType(contentType);

        long fileSize = epubReaderService.getFileSize(bookId, bookType, filePath);
        if (fileSize > 0) {
            response.setContentLengthLong(fileSize);
        }

        if (contentType.startsWith("font/") ||
                "application/font-woff".equals(contentType) ||
                "application/font-woff2".equals(contentType) ||
                "application/vnd.ms-fontobject".equals(contentType)) {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        response.setHeader("Cache-Control", "public, max-age=3600");

        try {
            epubReaderService.streamFile(bookId, bookType, filePath, response.getOutputStream());
        } catch (FileNotFoundException e) {
            response.reset();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
