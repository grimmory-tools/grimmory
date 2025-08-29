package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.BookService;
import com.adityachandel.booklore.service.opds.OpdsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opds")
@RequiredArgsConstructor
public class OpdsController {

    private final OpdsService opdsService;
    private final BookService bookService;

    @GetMapping(value = "/catalog", produces = "application/atom+xml;profile=opds-catalog")
    public ResponseEntity<String> getCatalogFeed(HttpServletRequest request) {
        String feed = opdsService.generateCatalogFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/atom+xml;profile=opds-catalog"))
                .body(feed);
    }
   
    @GetMapping(value = "/search", produces = "application/atom+xml;profile=opds-catalog")
    public ResponseEntity<String> search(HttpServletRequest request) {
        String feed = opdsService.generateSearchResults(request, request.getParameter("q"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/atom+xml;profile=opds-catalog"))
                .body(feed);
    }

    @GetMapping(value = "/search.opds", produces = "application/atom+xml;profile=opds-catalog")
    public ResponseEntity<String> searchDescription(HttpServletRequest request) {
        String feed = opdsService.generateSearchDescription(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/atom+xml;profile=opds-catalog"))
                .body(feed);
    }

    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(@PathVariable("bookId") Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@PathVariable long bookId) {
        Resource coverImage = bookService.getBookThumbnail(bookId);
        String contentType = "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + coverImage.getFilename() + "\"")
                .body(coverImage);
    }
}
