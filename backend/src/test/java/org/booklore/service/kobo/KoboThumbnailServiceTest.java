package org.booklore.service.kobo;

import org.booklore.service.book.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KoboThumbnailService Tests")
class KoboThumbnailServiceTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private KoboThumbnailService thumbnailService;

    @Test
    @DisplayName("Should return thumbnail with JPEG content type when image exists")
    void getThumbnail_existingImage() {
        String coverHash = "abc123hash";
        Resource image = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public boolean exists() {
                return true;
            }
        };
        when(bookService.getBookCover(coverHash)).thenReturn(image);

        ResponseEntity<Resource> response = thumbnailService.getThumbnail(coverHash);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/jpeg", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Should return 404 when image is null")
    void getThumbnail_nullImage() {
        when(bookService.getBookCover("nonexistent")).thenReturn(null);

        ResponseEntity<Resource> response = thumbnailService.getThumbnail("nonexistent");

        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should return 404 when image does not exist")
    void getThumbnail_imageNotExists() {
        Resource image = mock(Resource.class);
        when(image.exists()).thenReturn(false);
        when(bookService.getBookCover("missing")).thenReturn(image);

        ResponseEntity<Resource> response = thumbnailService.getThumbnail("missing");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Should pass cover hash to book service")
    void getThumbnail_passesCorrectHash() {
        String coverHash = "unique-hash-456";
        when(bookService.getBookCover(coverHash)).thenReturn(null);

        thumbnailService.getThumbnail(coverHash);

        verify(bookService).getBookCover(coverHash);
    }
}
