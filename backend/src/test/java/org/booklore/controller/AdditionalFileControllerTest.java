package org.booklore.controller;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.request.DetachBookFileRequest;
import org.booklore.model.dto.response.DetachBookFileResponse;
import org.booklore.service.book.BookFileDetachmentService;
import org.booklore.service.metadata.BookCoverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalFileControllerTest {

    @Mock private BookFileDetachmentService bookFileDetachmentService;
    @Mock private BookCoverService bookCoverService;

    @InjectMocks
    private AdditionalFileController controller;

    private final Long bookId = 1L;
    private final Long fileId = 2L;
    private final Long newBookId = 99L;

    private DetachBookFileResponse detachResponse() {
        Book sourceBook = Book.builder().id(bookId).build();
        Book newBook = Book.builder().id(newBookId).build();
        return new DetachBookFileResponse(sourceBook, newBook);
    }

    @Test
    void detachFile_regeneratesCoverForNewBookAfterDetach() {
        DetachBookFileRequest request = new DetachBookFileRequest(true);
        DetachBookFileResponse response = detachResponse();
        when(bookFileDetachmentService.detachBookFile(bookId, fileId, true)).thenReturn(response);

        ResponseEntity<DetachBookFileResponse> result = controller.detachFile(bookId, fileId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
        verify(bookFileDetachmentService).detachBookFile(bookId, fileId, true);
        verify(bookCoverService).regenerateCover(newBookId);
    }

    @Test
    void detachFile_passesCopyMetadataFlag() {
        DetachBookFileRequest request = new DetachBookFileRequest(false);
        when(bookFileDetachmentService.detachBookFile(bookId, fileId, false)).thenReturn(detachResponse());

        controller.detachFile(bookId, fileId, request);

        verify(bookFileDetachmentService).detachBookFile(bookId, fileId, false);
        verify(bookCoverService).regenerateCover(newBookId);
    }

    @Test
    void detachFile_swallowsCoverRegenerationFailure() {
        DetachBookFileRequest request = new DetachBookFileRequest(true);
        DetachBookFileResponse response = detachResponse();
        when(bookFileDetachmentService.detachBookFile(bookId, fileId, true)).thenReturn(response);
        doThrow(new RuntimeException("no ebook file")).when(bookCoverService).regenerateCover(newBookId);

        ResponseEntity<DetachBookFileResponse> result = controller.detachFile(bookId, fileId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
        verify(bookCoverService).regenerateCover(newBookId);
    }
}
