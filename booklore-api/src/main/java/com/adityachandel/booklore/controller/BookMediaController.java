package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.metadata.BookMetadataService;
import com.adityachandel.booklore.service.reader.CbxReaderService;
import com.adityachandel.booklore.service.reader.PdfReaderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/media")
public class BookMediaController {

    private final BookService bookService;
    private final BookMetadataService bookMetadataService;
    private final PdfReaderService pdfReaderService;
    private final CbxReaderService cbxReaderService;
    private final BookDropService bookDropService;

    @GetMapping("/book/{bookId}/thumbnail")
    public ResponseEntity<Resource> getBookThumbnail(@PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookThumbnail(bookId));
    }

    @GetMapping("/book/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookCover(bookId));
    }

    @GetMapping("/book/{bookId}/backup-cover")
    public ResponseEntity<Resource> getBackupBookCover(@PathVariable long bookId) {
        Resource file = bookMetadataService.getBackupCoverForBook(bookId);
        if (file == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cover.jpg")
                .contentType(MediaType.IMAGE_JPEG)
                .body(file);
    }

    @GetMapping("/book/{bookId}/pdf/pages/{pageNumber}")
    public void getPdfPage(@PathVariable Long bookId, @PathVariable int pageNumber, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        pdfReaderService.streamPageImage(bookId, pageNumber, response.getOutputStream());
    }

    @GetMapping("/book/{bookId}/cbx/pages/{pageNumber}")
    public void getCbxPage(@PathVariable Long bookId, @PathVariable int pageNumber, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        cbxReaderService.streamPageImage(bookId, pageNumber, response.getOutputStream());
    }

    @GetMapping("/bookdrop/{bookdropId}/cover")
    public ResponseEntity<Resource> getBookdropCover(@PathVariable long bookdropId) {
        Resource file = bookDropService.getBookdropCover(bookdropId);
        return (file != null)
                ? ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cover.jpg")
                .contentType(MediaType.IMAGE_JPEG)
                .body(file)
                : ResponseEntity.noContent().build();
    }
}