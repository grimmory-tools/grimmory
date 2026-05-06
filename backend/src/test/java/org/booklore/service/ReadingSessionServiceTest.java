package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionItemRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {

    @Mock
    AuthenticationService authenticationService;
    @Mock
    ReadingSessionRepository readingSessionRepository;
    @Mock
    BookRepository bookRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    UserBookProgressRepository userBookProgressRepository;

    @InjectMocks
    ReadingSessionService service;

    @Test
    void recordSessionsBatch_savesAllSessions() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        BookLoreUser user = BookLoreUser.builder()
                .id(5L)
                .permissions(permissions)
                .assignedLibraries(List.of(Library.builder().id(9L).build()))
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(userEntity));

        LibraryEntity library = new LibraryEntity();
        library.setId(9L);
        BookEntity book = new BookEntity();
        book.setId(12L);
        book.setLibrary(library);
        when(bookRepository.findById(12L)).thenReturn(Optional.of(book));

        when(readingSessionRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ReadingSessionEntity> sessions = invocation.getArgument(0);
            for (int i = 0; i < sessions.size(); i++) {
                sessions.get(i).setId((long) (i + 1));
            }
            return sessions;
        });

        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest(
                12L,
                null,
                List.of(
                        new ReadingSessionItemRequest(
                                Instant.parse("2026-01-01T10:00:00Z"),
                                Instant.parse("2026-01-01T10:05:00Z"),
                                300,
                                "5m",
                                10f,
                                12f,
                                2f,
                                "loc1",
                                "loc2"
                        ),
                        new ReadingSessionItemRequest(
                                Instant.parse("2026-01-01T11:00:00Z"),
                                Instant.parse("2026-01-01T11:02:00Z"),
                                120,
                                "2m",
                                12f,
                                13f,
                                1f,
                                "loc2",
                                "loc3"
                        )
                )
        );

        ReadingSessionBatchResponse response = service.recordSessionsBatch(request);

        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSuccessCount());

        ArgumentCaptor<List<ReadingSessionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(readingSessionRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(book, captor.getValue().getFirst().getBook());
        assertEquals(userEntity, captor.getValue().getFirst().getUser());
    }

    @Test
    void recordSessionsBatch_rejectsInvalidDelta() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(5L)
                .permissions(permissions)
                .assignedLibraries(List.of())
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(userEntity));

        BookEntity book = new BookEntity();
        book.setId(12L);
        book.setLibrary(new LibraryEntity());
        when(bookRepository.findById(12L)).thenReturn(Optional.of(book));

        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest(
                12L,
                null,
                List.of(new ReadingSessionItemRequest(
                        Instant.parse("2026-01-01T10:00:00Z"),
                        Instant.parse("2026-01-01T10:05:00Z"),
                        300,
                        "5m",
                        10f,
                        12f,
                        3f,
                        "loc1",
                        "loc2"
                ))
        );

        assertThrows(APIException.class, () -> service.recordSessionsBatch(request));
    }
}
