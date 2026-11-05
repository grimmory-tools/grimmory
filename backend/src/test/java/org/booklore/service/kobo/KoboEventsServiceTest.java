package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboEventsContainer;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.service.ReadingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KoboEventsService Tests")
class KoboEventsServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock ReadingSessionService readingSessionService;
    @Mock BookRepository bookRepository;
    @Mock BookMetadataRepository bookMetadataRepository;
    @Mock ReadingSessionRepository readingSessionRepository;

    private KoboEventsService service;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        service = new KoboEventsService(authenticationService, readingSessionService, bookRepository,
                bookMetadataRepository, readingSessionRepository);
        when(authenticationService.getAuthenticatedUser()).thenReturn(BookLoreUser.builder().id(1L).build());
        when(bookRepository.existsById(42L)).thenReturn(true);
        when(bookRepository.findByIdWithBookFiles(anyLong())).thenReturn(Optional.empty());
    }

    private KoboEventsContainer parse(String json) {
        return mapper.readValue(json, KoboEventsContainer.class);
    }

    private static final String OPEN_AND_LEAVE = """
            {
              "AffiliateName": "Kobo",
              "ApplicationVersion": "4.41.23145",
              "PlatformId": "00000000-0000-0000-0000-000000000390",
              "SerialNumber": "N4181C1234567",
              "SomethingNew": true,
              "Events": [
                {
                  "ClientApplicationVersion": "4.41.23145",
                  "EventType": "OpenContent",
                  "Id": "a",
                  "Timestamp": "2026-11-01T20:00:00Z",
                  "Attributes": {"Monetization": "Paid", "volumeid": "42", "progress": "10", "Title": "Dune"}
                },
                {
                  "EventType": "LeaveContent",
                  "Id": "b",
                  "Timestamp": "2026-11-01T20:30:00Z",
                  "Attributes": {"Monetization": "Paid", "volumeid": "42", "progress": 15, "Title": "Dune"},
                  "Metrics": {"IdleTime": 3, "PagesTurned": 20, "SecondsRead": 1500}
                },
                {"EventType": "AppStart", "Id": "c", "Timestamp": "2026-11-01T19:00:00Z"}
              ]
            }
            """;

    @Test
    @DisplayName("Open + Leave events produce one reading session")
    void recordsSession() {
        service.handleEvents(parse(OPEN_AND_LEAVE));

        ArgumentCaptor<ReadingSessionRequest> captor = ArgumentCaptor.forClass(ReadingSessionRequest.class);
        verify(readingSessionService).recordSession(captor.capture());
        ReadingSessionRequest r = captor.getValue();
        assertEquals(42L, r.getBookId());
        assertEquals(Instant.parse("2026-11-01T20:30:00Z"), r.getEndTime());
        assertEquals(Instant.parse("2026-11-01T20:05:00Z"), r.getStartTime());
        assertEquals(1500, r.getDurationSeconds());
        assertEquals("25m 0s", r.getDurationFormatted());
        assertEquals(10f, r.getStartProgress());
        assertEquals(15f, r.getEndProgress());
        assertEquals(5f, r.getProgressDelta());
    }

    @Test
    @DisplayName("Duplicate sessions are not stored twice")
    void skipsDuplicates() {
        when(readingSessionRepository.existsByUser_IdAndBook_IdAndStartTimeAndEndTime(any(), any(), any(), any())).thenReturn(true);
        service.handleEvents(parse(OPEN_AND_LEAVE));
        verify(readingSessionService, never()).recordSession(any());
    }

    @Test
    @DisplayName("Idle sessions, unknown books and malformed events are ignored without failing")
    void ignoresJunk() {
        String json = """
                {"SerialNumber": "X", "Events": [
                  {"EventType": "LeaveContent", "Timestamp": "2026-11-01T20:30:00Z",
                   "Attributes": {"Monetization": "Paid", "volumeid": "42"},
                   "Metrics": {"PagesTurned": 0, "SecondsRead": 30}},
                  {"EventType": "LeaveContent", "Timestamp": "2026-11-01T20:30:00Z",
                   "Attributes": {"Monetization": "Paid", "volumeid": "abc-kobo-store-uuid"},
                   "Metrics": {"PagesTurned": 5, "SecondsRead": 30}},
                  {"EventType": "LeaveContent"},
                  {"EventType": "OpenContent", "Attributes": {}}
                ]}
                """;
        assertDoesNotThrow(() -> service.handleEvents(parse(json)));
        verify(readingSessionService, never()).recordSession(any());
    }

    @Test
    @DisplayName("Sideloaded books are matched by title, case-insensitively")
    void sideloadedByTitle() {
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setBookId(7L);
        when(bookMetadataRepository.findByTitleIgnoreCase("the hobbit")).thenReturn(List.of(meta));
        String json = """
                {"SerialNumber": "X", "Events": [
                  {"EventType": "LeaveContent", "Timestamp": "2026-11-01T20:30:00Z",
                   "Attributes": {"Monetization": "Sideloaded", "Title": " the hobbit ", "progress": 50},
                   "Metrics": {"PagesTurned": 5, "SecondsRead": 60}}
                ]}
                """;
        service.handleEvents(parse(json));
        ArgumentCaptor<ReadingSessionRequest> captor = ArgumentCaptor.forClass(ReadingSessionRequest.class);
        verify(readingSessionService).recordSession(captor.capture());
        assertEquals(7L, captor.getValue().getBookId());
        assertNull(captor.getValue().getStartProgress());
        assertNull(captor.getValue().getProgressDelta());
    }
}
