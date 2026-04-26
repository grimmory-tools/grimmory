package org.booklore.service.kobo;

import org.booklore.service.book.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboThumbnailService {

    private final BookService bookService;

    public ResponseEntity<Resource> getThumbnail(String coverHash) {
        return getThumbnailInternal(coverHash);
    }

    private ResponseEntity<Resource> getThumbnailInternal(String coverHash) {
        Resource image = bookService.getBookCover(coverHash);
        if (!isValidImage(image)) {
            log.warn("Thumbnail not found for bookId={}", coverHash);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                .body(image);
    }

    private boolean isValidImage(Resource image) {
        return image != null && image.exists();
    }
}