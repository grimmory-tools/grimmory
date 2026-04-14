package org.booklore.controller;

import org.apache.commons.text.StringEscapeUtils;
import org.booklore.service.AuthorMetadataService;
import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.service.book.BookService;
import org.booklore.service.bookdrop.BookDropService;
import org.booklore.service.reader.CbxReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Tag(name = "Book Media", description = "Endpoints for retrieving book media such as covers, thumbnails, and pages")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/media")
public class BookMediaController {

    private final BookService bookService;
    private final CbxReaderService cbxReaderService;
    private final BookDropService bookDropService;
    private final AuthorMetadataService authorMetadataService;

    private ResponseEntity<Resource> serveResourceWithCaching(Resource resource, WebRequest request, Supplier<String> titleSupplier) {
        try {
            if (resource.exists()) {
                long lastModified = resource.lastModified();
                if (request.checkNotModified(lastModified)) {
                    return null;
                }
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400, stale-while-revalidate=3600")
                        .lastModified(lastModified)
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                // Return a deterministic placeholder SVG instead of a broken image or 404
                String svg = generatePlaceholderSvg(titleSupplier.get());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400") // Cache placeholders longer
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(new ByteArrayResource(svg.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String generatePlaceholderSvg(String title) {
        String label;
        if (title == null || title.isBlank()) {
            label = "B";
        } else {
            String trimmed = title.trim();
            int codePoint = trimmed.codePointAt(0);
            label = new String(Character.toChars(Character.toUpperCase(codePoint)));
        }

        // Escape for XML/SVG
        label = StringEscapeUtils.escapeXml11(label);

        int hue = (title == null) ? 200 : Math.abs(title.hashCode() % 360);
        
        return String.format(
            "<svg width=\"250\" height=\"350\" xmlns=\"http://www.w3.org/2000/svg\">" +
            "<rect width=\"100%%\" height=\"100%%\" fill=\"hsl(%d, 40%%, 40%%)\"/>" +
            "<text x=\"50%%\" y=\"50%%\" font-family=\"Arial, sans-serif\" font-size=\"80\" " +
            "fill=\"white\" text-anchor=\"middle\" dominant-baseline=\"central\">%s</text>" +
            "</svg>", hue, label);
    }

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book thumbnail returned successfully")
    @GetMapping("/book/{bookId}/thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookThumbnail(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            WebRequest request) {
        Resource resource = bookService.getBookThumbnail(bookId);
        return serveResourceWithCaching(resource, request, () -> bookService.getBook(bookId, false).getTitle());
    }

    @Operation(summary = "Get book cover", description = "Retrieve the cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book cover returned successfully")
    @GetMapping("/book/{bookId}/cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookCover(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            WebRequest request) {
        Resource resource = bookService.getBookCover(bookId);
        return serveResourceWithCaching(resource, request, () -> bookService.getBook(bookId, false).getTitle());
    }

    @Operation(summary = "Get audiobook thumbnail", description = "Retrieve the audiobook thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook thumbnail returned successfully")
    @GetMapping("/book/{bookId}/audiobook-thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookThumbnail(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getAudiobookThumbnail(bookId));
    }

    @Operation(summary = "Get audiobook cover", description = "Retrieve the audiobook cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook cover returned successfully")
    @GetMapping("/book/{bookId}/audiobook-cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG)
                .body(bookService.getAudiobookCover(bookId));
    }

    @Operation(summary = "Get CBX page as image", description = "Retrieve a specific page from a CBX book as an image.")
    @ApiResponse(responseCode = "200", description = "CBX page image returned successfully")
    @GetMapping("/book/{bookId}/cbx/pages/{pageNumber}")
    @CheckBookAccess(bookIdParam = "bookId")
    public void getCbxPage(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Page number to retrieve") @PathVariable int pageNumber,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            WebRequest webRequest,
            HttpServletResponse response) throws IOException {
        long lastModified = cbxReaderService.getArchiveLastModified(bookId, bookType);
        String variant = (bookType == null || bookType.isBlank()) ? "default" : bookType;
        String etag = "W/\"" + bookId + "-" + variant + "-" + pageNumber + "-" + lastModified + "\"";
        if (webRequest.checkNotModified(etag, lastModified)) {
            return;
        }
        response.setContentType(cbxReaderService.getPageContentType(bookId, bookType, pageNumber));
        response.setHeader("Cache-Control", "private, max-age=3600, stale-while-revalidate=3600");
        cbxReaderService.streamPageImage(bookId, bookType, pageNumber, response.getOutputStream());
    }

    @Operation(summary = "Get author photo", description = "Retrieve the photo for a specific author.")
    @ApiResponse(responseCode = "200", description = "Author photo returned successfully")
    @GetMapping("/author/{authorId}/photo")
    public ResponseEntity<Resource> getAuthorPhoto(@Parameter(description = "ID of the author") @PathVariable long authorId) {
        Resource photo = authorMetadataService.getAuthorPhoto(authorId);
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG)
                .body(photo);
    }

    @Operation(summary = "Get author thumbnail", description = "Retrieve the thumbnail for a specific author.")
    @ApiResponse(responseCode = "200", description = "Author thumbnail returned successfully")
    @GetMapping("/author/{authorId}/thumbnail")
    public ResponseEntity<Resource> getAuthorThumbnail(@Parameter(description = "ID of the author") @PathVariable long authorId) {
        Resource thumbnail = authorMetadataService.getAuthorThumbnail(authorId);
        if (thumbnail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG)
                .body(thumbnail);
    }

    @Operation(summary = "Get bookdrop cover", description = "Retrieve the cover image for a specific bookdrop file.")
    @ApiResponse(responseCode = "200", description = "Bookdrop cover returned successfully")
    @GetMapping("/bookdrop/{bookdropId}/cover")
    public ResponseEntity<Resource> getBookdropCover(@Parameter(description = "ID of the bookdrop file") @PathVariable long bookdropId) {
        Resource file = bookDropService.getBookdropCover(bookdropId);
        String contentDisposition = "inline; filename=\"cover.jpg\"; filename*=UTF-8''cover.jpg";
        return (file != null)
                ? ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.IMAGE_JPEG)
                .body(file)
                : ResponseEntity.noContent().build();
    }
}
