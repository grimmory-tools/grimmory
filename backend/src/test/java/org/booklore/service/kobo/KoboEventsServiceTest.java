package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.kobo.KoboEventsContainer;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.service.ReadingSessionService;
import org.booklore.service.restriction.ContentRestrictionService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KoboEventsService Tests")
class KoboEventsServiceTest {

    private static final long ACCESSIBLE_LIBRARY = 1L;
    private static final long OTHER_LIBRARY = 2L;
    private static final String PAID_42 = "{\"Monetization\": \"Paid\", \"volumeid\": \"42\"}";

    @Mock AuthenticationService authenticationService;
    @Mock ReadingSessionService readingSessionService;
    @Mock BookRepository bookRepository;
    @Mock BookMetadataRepository bookMetadataRepository;
    @Mock ReadingSessionRepository readingSessionRepository;
    @Mock ContentRestrictionService contentRestrictionService;

    private KoboEventsService service;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        service = new KoboEventsService(authenticationService, readingSessionService, bookRepository,
                bookMetadataRepository, readingSessionRepository, contentRestrictionService);
        BookLoreUser user = BookLoreUser.builder()
                .id(1L)
                .permissions(new BookLoreUser.UserPermissions())
                .assignedLibraries(List.of(Library.builder().id(ACCESSIBLE_LIBRARY).build()))
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithMetadata(anyLong())).thenReturn(Optional.empty());
        givenBook(42L, ACCESSIBLE_LIBRARY);
        when(bookRepository.findByIdWithBookFiles(anyLong())).thenReturn(Optional.empty());
        when(contentRestrictionService.applyRestrictions(anyList(), eq(1L))).thenAnswer(inv -> inv.getArgument(0));
    }

    private KoboEventsContainer parse(String json) {
        return mapper.readValue(json, KoboEventsContainer.class);
    }

    private void givenBook(long bookId, long libraryId) {
        when(bookRepository.findByIdWithMetadata(bookId)).thenReturn(Optional.of(book(bookId, libraryId)));
    }

    private static BookEntity book(long bookId, long libraryId) {
        return BookEntity.builder().id(bookId).library(LibraryEntity.builder().id(libraryId).build()).build();
    }

    private static BookMetadataEntity metadata(long bookId) {
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setBookId(bookId);
        return meta;
    }

    private static String leaveEvent(String timestampJson, String attributesJson) {
        return """
                {"EventType": "LeaveContent", "Timestamp": %s, "Attributes": %s,
                 "Metrics": {"PagesTurned": 5, "SecondsRead": 60}}
                """.formatted(timestampJson, attributesJson);
    }

    private static String batch(String... events) {
        return "{\"SerialNumber\": \"X\", \"Events\": [" + String.join(",", events) + "]}";
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
        assertThat(r.getBookId()).isEqualTo(42L);
        assertThat(r.getEndTime()).isEqualTo(Instant.parse("2026-11-01T20:30:00Z"));
        assertThat(r.getStartTime()).isEqualTo(Instant.parse("2026-11-01T20:05:00Z"));
        assertThat(r.getDurationSeconds()).isEqualTo(1500);
        assertThat(r.getStartProgress()).isEqualTo(10f);
        assertThat(r.getEndProgress()).isEqualTo(15f);
        assertThat(r.getProgressDelta()).isEqualTo(5f);
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
        assertThatCode(() -> service.handleEvents(parse(json))).doesNotThrowAnyException();
        verify(readingSessionService, never()).recordSession(any());
    }

    @Test
    @DisplayName("Sideloaded books are matched by title, case-insensitively")
    void sideloadedByTitle() {
        givenBook(7L, ACCESSIBLE_LIBRARY);
        when(bookMetadataRepository.findByTitleIgnoreCase("the hobbit")).thenReturn(List.of(metadata(7L)));
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
        assertThat(captor.getValue().getBookId()).isEqualTo(7L);
        assertThat(captor.getValue().getStartProgress()).isNull();
        assertThat(captor.getValue().getProgressDelta()).isNull();
    }

    @Test
    @DisplayName("Ambiguous sideloaded titles are ignored; inaccessible matches do not count")
    void ambiguousSideloadedTitle() {
        givenBook(7L, ACCESSIBLE_LIBRARY);
        givenBook(8L, ACCESSIBLE_LIBRARY);
        givenBook(9L, OTHER_LIBRARY);
        when(bookMetadataRepository.findByTitleIgnoreCase("Dune")).thenReturn(List.of(metadata(7L), metadata(8L)));
        when(bookMetadataRepository.findByTitleIgnoreCase("Emma")).thenReturn(List.of(metadata(7L), metadata(9L)));

        service.handleEvents(parse(batch(
                leaveEvent("\"2026-11-01T20:30:00Z\"", "{\"Monetization\": \"Sideloaded\", \"Title\": \"Dune\"}"),
                leaveEvent("\"2026-11-01T21:30:00Z\"", "{\"Monetization\": \"Sideloaded\", \"Title\": \"Emma\"}"))));

        ArgumentCaptor<ReadingSessionRequest> captor = ArgumentCaptor.forClass(ReadingSessionRequest.class);
        verify(readingSessionService).recordSession(captor.capture());
        assertThat(captor.getValue().getBookId()).isEqualTo(7L);
        assertThat(captor.getValue().getEndTime()).isEqualTo(Instant.parse("2026-11-01T21:30:00Z"));
    }

    @Test
    @DisplayName("Books outside the user's libraries or hidden by content restrictions are ignored")
    void inaccessibleBooks() {
        givenBook(43L, OTHER_LIBRARY);
        givenBook(44L, ACCESSIBLE_LIBRARY);
        when(contentRestrictionService.applyRestrictions(argThat(books -> books != null && !books.isEmpty() && books.getFirst().getId() == 44L), eq(1L)))
                .thenReturn(List.of());

        service.handleEvents(parse(batch(
                leaveEvent("\"2026-11-01T20:30:00Z\"", "{\"Monetization\": \"Paid\", \"volumeid\": \"43\"}"),
                leaveEvent("\"2026-11-01T20:30:00Z\"", "{\"Monetization\": \"Paid\", \"volumeid\": \"44\"}"))));

        verify(readingSessionService, never()).recordSession(any());
    }

    @Test
    @DisplayName("An unparseable timestamp only skips its own event")
    void invalidTimestamp() {
        service.handleEvents(parse(batch(
                leaveEvent("\"not a timestamp\"", PAID_42),
                leaveEvent("{\"nested\": true}", PAID_42),
                leaveEvent("\"2026-11-01T20:30:00.123+01:00\"", PAID_42))));

        ArgumentCaptor<ReadingSessionRequest> captor = ArgumentCaptor.forClass(ReadingSessionRequest.class);
        verify(readingSessionService).recordSession(captor.capture());
        assertThat(captor.getValue().getEndTime()).isEqualTo(Instant.parse("2026-11-01T19:30:00.123Z"));
    }
}
