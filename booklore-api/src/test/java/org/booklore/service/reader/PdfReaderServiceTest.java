package org.booklore.service.reader;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfReaderServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterCacheService chapterCacheService;

    @InjectMocks
    private PdfReaderService pdfReaderService;

    private BookEntity bookEntity;
    private Path pdfPath;

    @BeforeEach
    void setup() {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        pdfPath = Path.of("/tmp/test.pdf");
    }

    @Test
    void testStreamPageImage_InvalidBookType_Throws() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
        APIException ex = assertThrows(APIException.class, () ->
                pdfReaderService.streamPageImage(1L, "../traversal", 1, new ByteArrayOutputStream())
        );
        assertTrue(ex.getMessage().contains("Invalid book type"), "Expected INVALID_INPUT, got: " + ex.getMessage());
    }

    @Test
    void testInitCache_InvalidBookType_Throws() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
        APIException ex = assertThrows(APIException.class, () ->
                pdfReaderService.initCache(1L, "../traversal")
        );
        assertTrue(ex.getMessage().contains("Invalid book type"), "Expected INVALID_INPUT, got: " + ex.getMessage());
    }
}
