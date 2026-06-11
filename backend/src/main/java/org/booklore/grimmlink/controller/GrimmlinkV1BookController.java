package org.booklore.grimmlink.controller;

import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkReadStatusRequest;
import org.booklore.grimmlink.service.GrimmlinkBookService;
import org.booklore.model.dto.Book;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/books")
public class GrimmlinkV1BookController {

    private final GrimmlinkBookService bookService;

    @GetMapping("/by-hash/{bookHash}")
    public ResponseEntity<Book> getBookByHash(@PathVariable String bookHash) {
        return ResponseEntity.ok(bookService.getBookByHash(bookHash));
    }

    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(@PathVariable Long bookId) {
        return bookService.downloadBook(bookId);
    }

    @GetMapping("/read-statuses")
    public ResponseEntity<Map<String, List<String>>> getSupportedReadStatuses() {
        return ResponseEntity.ok(Map.of("statuses", bookService.getSupportedReadStatuses()));
    }

    @PutMapping("/{bookId}/status")
    public ResponseEntity<Map<String, Object>> updateReadStatus(@PathVariable Long bookId,
                                                               @RequestBody(required = false) GrimmlinkReadStatusRequest request) {
        return ResponseEntity.ok(bookService.updateReadStatus(
                bookId,
                request != null ? request.getStatus() : null));
    }
}
