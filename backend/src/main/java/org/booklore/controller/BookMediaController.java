package org.booklore.controller;

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
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Tag(name = "Book Media", description = "Endpoints for retrieving book media such as covers, thumbnails, and pages")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/media")
public class BookMediaController {

    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();

    private final BookService bookService;
    private final CbxReaderService cbxReaderService;
    private final BookDropService bookDropService;
    private final AuthorMetadataService authorMetadataService;

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book thumbnail returned successfully")
    @ApiResponse(responseCode = "404", description = "No thumbnail exists for this book")
    @GetMapping("/book/{bookId}/thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookThumbnail(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return toImageResponse(bookService.getBookThumbnailIfPresent(bookId));
    }

    @Operation(summary = "Get book cover", description = "Retrieve the cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book cover returned successfully")
    @ApiResponse(responseCode = "404", description = "No cover exists for this book")
    @GetMapping("/book/{bookId}/cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getBookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return toImageResponse(bookService.getBookCoverIfPresent(bookId));
    }

    @Operation(summary = "Get audiobook thumbnail", description = "Retrieve the audiobook thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook thumbnail returned successfully")
    @ApiResponse(responseCode = "404", description = "No audiobook thumbnail exists for this book")
    @GetMapping("/book/{bookId}/audiobook-thumbnail")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookThumbnail(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return toImageResponse(bookService.getAudiobookThumbnailIfPresent(bookId));
    }

    @Operation(summary = "Get audiobook cover", description = "Retrieve the audiobook cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Audiobook cover returned successfully")
    @ApiResponse(responseCode = "404", description = "No audiobook cover exists for this book")
    @GetMapping("/book/{bookId}/audiobook-cover")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> getAudiobookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return toImageResponse(bookService.getAudiobookCoverIfPresent(bookId));
    }

    private ResponseEntity<Resource> toImageResponse(Optional<Resource> image) {
        return image
                .map(resource -> ResponseEntity.ok().cacheControl(IMAGE_CACHE).body(resource))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
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
        response.setHeader(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE.getHeaderValue());
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
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(photo);
    }

    @Operation(summary = "Get author thumbnail", description = "Retrieve the thumbnail for a specific author.")
    @ApiResponse(responseCode = "200", description = "Author thumbnail returned successfully")
    @GetMapping("/author/{authorId}/thumbnail")
    public ResponseEntity<Resource> getAuthorThumbnail(@Parameter(description = "ID of the author") @PathVariable long authorId) {
        Resource thumbnail = authorMetadataService.getAuthorThumbnail(authorId);
        if (thumbnail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(thumbnail);
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
