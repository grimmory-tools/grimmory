package org.booklore.grimmlink.controller;

import org.booklore.grimmlink.dto.*;
import org.booklore.grimmlink.service.GrimmlinkAuthService;
import org.booklore.grimmlink.service.GrimmlinkBookService;
import org.booklore.grimmlink.service.GrimmlinkMetadataService;
import org.booklore.grimmlink.service.GrimmlinkPdfBridgeService;
import org.booklore.grimmlink.service.GrimmlinkProgressService;
import org.booklore.grimmlink.service.GrimmlinkReadingSessionService;
import org.booklore.grimmlink.service.GrimmlinkShelfService;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GrimmlinkV1ControllersTest {

    @Mock
    private GrimmlinkAuthService authService;

    @Mock
    private GrimmlinkBookService bookService;

    @Mock
    private GrimmlinkProgressService progressService;

    @Mock
    private GrimmlinkMetadataService metadataService;

    @Mock
    private GrimmlinkShelfService shelfService;

    @Mock
    private GrimmlinkPdfBridgeService pdfBridgeService;

    @Mock
    private GrimmlinkReadingSessionService readingSessionService;

    @InjectMocks
    private GrimmlinkV1AuthController authController;

    @InjectMocks
    private GrimmlinkV1BookController bookController;

    @InjectMocks
    private GrimmlinkV1SyncController syncController;

    @InjectMocks
    private GrimmlinkV1ShelfController shelfController;

    @InjectMocks
    private GrimmlinkV1BridgeController bridgeController;

    @InjectMocks
    private GrimmlinkV1ReadingSessionController readingSessionController;

    @BeforeEach
    void setUp() {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void authorize_returnsServiceResponseWithStatus() {
        Map<String, Object> expected = Map.of("status", "ok", "username", "reader", "syncEnabled", true);
        when(authService.authorize()).thenReturn(expected);

        ResponseEntity<Map<String, Object>> response = authController.authorize();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        assertEquals("ok", response.getBody().get("status"));
    }

    @Test
    void bookByHash_returnsBook() {
        Book book = Book.builder().id(12L).title("Title").build();
        when(bookService.getBookByHash("hash")).thenReturn(book);

        assertEquals(book, bookController.getBookByHash("hash").getBody());
    }

    @Test
    void progressRoutes_delegateToService() {
        KoreaderProgress progress = KoreaderProgress.builder().document("hash").progress("p").percentage(0.5F).build();
        when(progressService.getProgress("hash")).thenReturn(progress);

        assertEquals(progress, syncController.getProgress("hash").getBody());
        assertEquals("progress updated", syncController.updateProgress(progress).getBody().get("status"));
        verify(progressService).updateProgress(progress);
    }

    @Test
    void metadataPush_returnsFacadeResponse() {
        GrimmlinkMetadataSyncResponse expected = GrimmlinkMetadataSyncResponse.builder()
                .bookId(1L)
                .ok(true)
                .results(GrimmlinkMetadataSyncResults.builder().annotations(List.of()).bookmarks(List.of()).build())
                .build();
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        when(metadataService.syncMetadata(request)).thenReturn(expected);

        assertEquals(expected, syncController.syncMetadata(request).getBody());
    }

    @Test
    void metadataPullAndBatch_delegateToService() {
        Instant since = Instant.parse("2026-06-05T00:00:00Z");
        GrimmlinkMetadataPullResponse pull = GrimmlinkMetadataPullResponse.builder()
                .bookId(1L)
                .ok(true)
                .since(since)
                .nextCursor(since.plusSeconds(60))
                .limit(50)
                .items(List.of())
                .build();
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        GrimmlinkMetadataBatchResponse batch = GrimmlinkMetadataBatchResponse.builder()
                .ok(true)
                .pull(pull)
                .build();
        when(metadataService.pullMetadata(1L, null, null, since, null, 50, "annotation"))
                .thenReturn(pull);
        when(metadataService.syncMetadataBatch(request)).thenReturn(batch);

        assertEquals(pull, syncController.pullMetadata(1L, null, null, since, null, 50, "annotation").getBody());
        assertEquals(batch, syncController.syncMetadataBatch(request).getBody());
    }

    @Test
    void metadataPull_withCursorOverridesSince() {
        Instant since = Instant.parse("2026-06-05T00:00:00Z");
        Instant cursor = Instant.parse("2026-06-10T00:00:00Z");
        GrimmlinkMetadataPullResponse pull = GrimmlinkMetadataPullResponse.builder()
                .bookId(1L)
                .ok(true)
                .since(since)
                .nextCursor(cursor.plusSeconds(60))
                .limit(50)
                .items(List.of())
                .build();
        when(metadataService.pullMetadata(1L, null, null, since, cursor, 50, "annotation"))
                .thenReturn(pull);

        assertEquals(pull, syncController.pullMetadata(1L, null, null, since, cursor, 50, "annotation").getBody());
    }

    @Test
    void shelfRoutes_delegateToService() {
        var shelf = GrimmlinkShelfSummary.builder().id(1L).name("Shelf").type("regular").build();
        var book = GrimmlinkBookSummary.builder().bookId(2L).title("Book").build();
        var removal = GrimmlinkShelfRemovalResponse.builder().shelfId(1L).bookId(2L).removed(true).status("removed").build();
        when(shelfService.listShelves(null)).thenReturn(List.of(shelf));
        when(shelfService.listShelfBooks("magic", 1L, null, null, null))
                .thenReturn(List.of(book));
        when(shelfService.removeBookFromShelf("regular", 1L, 2L)).thenReturn(removal);

        assertEquals(List.of(shelf), shelfController.listShelves(null).getBody());
        assertEquals(List.of(book), shelfController.listShelfBooksByType("magic", 1L, null, null, null).getBody());
        assertEquals(removal, shelfController.removeBookFromShelf(1L, 2L).getBody());
    }

    @Test
    void readStatusAndBridgeRoutes_delegateToServices() {
        GrimmlinkReadStatusRequest statusRequest = new GrimmlinkReadStatusRequest();
        statusRequest.setStatus("READ");
        when(bookService.getSupportedReadStatuses()).thenReturn(List.of("UNREAD", "READING", "READ"));
        when(bookService.updateReadStatus(5L, "READ")).thenReturn(Map.of("updated", true));
        KoreaderProgress progress = KoreaderProgress.builder().bookId(5L).currentPage(10).build();
        when(pdfBridgeService.getPdfProgress(5L)).thenReturn(progress);
        when(pdfBridgeService.updatePdfProgress(5L, progress)).thenReturn(progress);

        assertEquals(List.of("UNREAD", "READING", "READ"), bookController.getSupportedReadStatuses().getBody().get("statuses"));
        assertEquals(true, bookController.updateReadStatus(5L, statusRequest).getBody().get("updated"));
        assertEquals(progress, bridgeController.getPdfProgress(5L).getBody());
        assertEquals(progress, bridgeController.updatePdfProgress(5L, progress).getBody());
    }

    @Test
    void readingSessionRoutes_delegateToService() {
        ReadingSessionRequest single = new ReadingSessionRequest();
        single.setBookId(7L);
        single.setStartTime(Instant.now());
        single.setEndTime(Instant.now());
        single.setDurationSeconds(60);
        GrimmlinkReadingSessionBatchRequest batch = new GrimmlinkReadingSessionBatchRequest();
        GrimmlinkReadingSessionBatchResponse expected = GrimmlinkReadingSessionBatchResponse.builder()
                .totalRequested(1)
                .successCount(1)
                .results(List.of())
                .build();
        when(readingSessionService.recordReadingSessionsBatch(batch)).thenReturn(expected);

        assertEquals(HttpStatus.ACCEPTED, readingSessionController.recordSession(single).getStatusCode());
        verify(readingSessionService).recordReadingSession(single);
        assertEquals(expected, readingSessionController.recordBatchSessions(batch).getBody());
    }
}
