package org.booklore.controller;

import org.booklore.service.AuthorMetadataService;
import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.service.book.BookService;
import org.booklore.service.bookdrop.BookDropService;
import org.booklore.service.media.ThumbnailRenderingService;
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

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Tag(name = "Book Media", description = "Endpoints for retrieving book media such as covers, thumbnails, and pages")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/media")
public class BookMediaController {

    private final BookService bookService;
    private final CbxReaderService cbxReaderService;
    private final BookDropService bookDropService;
    private final AuthorMetadataService authorMetadataService;
    private final ThumbnailRenderingService thumbnailRenderingService;

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book thumbnail returned successfully")
    @GetMapping("/book/{bookId}/thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookThumbnail(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Optional target width for on-the-fly resizing (60-600)")
            @RequestParam(name = "w", required = false) Integer width) {
        boolean realFile = bookService.hasBookThumbnail(bookId);
        Instant updatedOn = realFile ? bookService.getCoverUpdatedOn(bookId) : null;

        Resource resource = null;
        int clampedWidth = ThumbnailRenderingService.clampWidth(width);
        if (realFile && clampedWidth > 0) {
            byte[] bytes = thumbnailRenderingService.getResizedBookThumbnail(bookId, clampedWidth);
            if (bytes != null) {
                resource = new ByteArrayResource(bytes);
            }
        }
        if (resource == null) {
            resource = bookService.getBookThumbnail(bookId);
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(realFile
                        ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate()
                        : CacheControl.noStore());

        if (updatedOn != null) {
            builder.lastModified(updatedOn);
        }

        return builder.body(resource);
    }

    @Operation(summary = "Get book cover", description = "Retrieve the cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book cover returned successfully")
    @GetMapping("/book/{bookId}/cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        Resource resource = bookService.getBookCover(bookId);
        boolean realFile = bookService.hasBookCover(bookId);
        Instant updatedOn = realFile ? bookService.getCoverUpdatedOn(bookId) : null;

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(realFile
                        ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate()
                        : CacheControl.noStore());

        if (updatedOn != null) {
            builder.lastModified(updatedOn);
        }

        return builder.body(resource);
    }

    @Operation(summary = "Get audiobook thumbnail", description = "Retrieve the audiobook thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook thumbnail returned successfully")
    @GetMapping("/book/{bookId}/audiobook-thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookThumbnail(
            @Parameter(description = "ID of the book") @PathVariable long bookId,
            @Parameter(description = "Optional target width for on-the-fly resizing (60-600)")
            @RequestParam(name = "w", required = false) Integer width) {
        boolean realFile = bookService.hasAudiobookThumbnail(bookId);
        Instant updatedOn = realFile ? bookService.getAudiobookCoverUpdatedOn(bookId) : null;

        Resource resource = null;
        int clampedWidth = ThumbnailRenderingService.clampWidth(width);
        if (realFile && clampedWidth > 0) {
            byte[] bytes = thumbnailRenderingService.getResizedAudiobookThumbnail(bookId, clampedWidth);
            if (bytes != null) {
                resource = new ByteArrayResource(bytes);
            }
        }
        if (resource == null) {
            resource = bookService.getAudiobookThumbnail(bookId);
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(realFile
                        ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate()
                        : CacheControl.noStore());

        if (updatedOn != null) {
            builder.lastModified(updatedOn);
        }

        return builder.body(resource);
    }

    @Operation(summary = "Get audiobook cover", description = "Retrieve the audiobook cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook cover returned successfully")
    @GetMapping("/book/{bookId}/audiobook-cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        Resource resource = bookService.getAudiobookCover(bookId);
        boolean realFile = bookService.hasAudiobookCover(bookId);
        Instant updatedOn = realFile ? bookService.getAudiobookCoverUpdatedOn(bookId) : null;

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(realFile
                        ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate()
                        : CacheControl.noStore());

        if (updatedOn != null) {
            builder.lastModified(updatedOn);
        }

        return builder.body(resource);
    }

    @Operation(summary = "Get CBX page as image", description = "Retrieve a specific page from a CBX book as an image.")
    @ApiResponse(responseCode = "200", description = "CBX page image returned successfully")
    @GetMapping("/book/{bookId}/cbx/pages/{pageNumber}")
    @CheckBookAccess(bookIdParam = "bookId")
    public void getCbxPage(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Page number to retrieve") @PathVariable int pageNumber,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
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

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate())
                .contentType(MediaType.IMAGE_JPEG);

        try {
            builder.lastModified(photo.lastModified());
        } catch (IOException e) {
            // Ignore if lastModified cannot be determined
        }

        return builder.body(photo);
    }

    @Operation(summary = "Get author thumbnail", description = "Retrieve the thumbnail for a specific author.")
    @ApiResponse(responseCode = "200", description = "Author thumbnail returned successfully")
    @GetMapping("/author/{authorId}/thumbnail")
    public ResponseEntity<Resource> getAuthorThumbnail(@Parameter(description = "ID of the author") @PathVariable long authorId) {
        Resource thumbnail = authorMetadataService.getAuthorThumbnail(authorId);
        if (thumbnail == null) {
            return ResponseEntity.notFound().build();
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().mustRevalidate())
                .contentType(MediaType.IMAGE_JPEG);

        try {
            builder.lastModified(thumbnail.lastModified());
        } catch (IOException e) {
            // Ignore if lastModified cannot be determined
        }

        return builder.body(thumbnail);
    }

    @Operation(summary = "Get bookdrop cover", description = "Retrieve the cover image for a specific bookdrop file.")
    @ApiResponse(responseCode = "200", description = "Bookdrop cover returned successfully")
    @GetMapping("/bookdrop/{bookdropId}/cover")
    public ResponseEntity<Resource> getBookdropCover(@Parameter(description = "ID of the bookdrop file") @PathVariable long bookdropId) {
        Resource file = bookDropService.getBookdropCover(bookdropId);
        String contentDisposition = "inline; filename=\"cover.jpg\"; filename*=UTF-8''cover.jpg";
        return (file != null)
                ? ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.IMAGE_JPEG)
                .body(file)
                : ResponseEntity.noContent().build();
    }
}