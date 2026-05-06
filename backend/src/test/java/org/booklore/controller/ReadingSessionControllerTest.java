package org.booklore.controller;

import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionItemRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.service.ReadingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class ReadingSessionControllerTest {

    @Mock
    private ReadingSessionService readingSessionService;

    @InjectMocks
    private ReadingSessionController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void recordReadingSession_returnsAccepted() {
        ReadingSessionRequest request = new ReadingSessionRequest();
        doNothing().when(readingSessionService).recordSession(request);

        ResponseEntity<Void> response = controller.recordReadingSession(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void recordBatchSessions_returnsResponse() {
        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest(
                7L,
                null,
                List.of(new ReadingSessionItemRequest(
                        Instant.parse("2026-01-01T10:00:00Z"),
                        Instant.parse("2026-01-01T10:05:00Z"),
                        300,
                        "5m",
                        10f,
                        12f,
                        2f,
                        "a",
                        "b"
                ))
        );
        ReadingSessionBatchResponse expected = ReadingSessionBatchResponse.builder()
                .totalRequested(1)
                .successCount(1)
                .build();
        when(readingSessionService.recordSessionsBatch(request)).thenReturn(expected);

        ResponseEntity<ReadingSessionBatchResponse> response = controller.recordBatchSessions(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
