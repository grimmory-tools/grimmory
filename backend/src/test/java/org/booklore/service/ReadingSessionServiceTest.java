package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.*;
import org.booklore.model.dto.BookLoreUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {
    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private ReadingSessionRepository readingSessionRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBookProgressRepository userBookProgressRepository;

    @InjectMocks
    private ReadingSessionService service;

    private BookLoreUser user;
    private BookLoreUserEntity userEntity;
    private BookEntity bookEntity;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).build();
        userEntity = BookLoreUserEntity.builder().id(1L).build();

        BookFileEntity bookFileEntity = BookFileEntity.builder().bookType(BookFileType.EPUB).build();
        bookEntity = BookEntity.builder().id(1L).bookFiles(List.of(bookFileEntity)).build();
    }

    @Test
    void recordSession_allowsDurationOverrides() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));
        request.setDurationSeconds(100);
        request.setDurationFormatted("Something Else");

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(100, saved.getDurationSeconds());
        assertEquals("Something Else", saved.getDurationFormatted());
    }

    @Test
    void recordSession_infersDuration() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(128, saved.getDurationSeconds());
        assertEquals("2m 8s", saved.getDurationFormatted());
    }

    @Test
    void recordSession_infersDurationInverted() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173128));
        request.setEndTime(Instant.ofEpochSecond(1778173000));

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(128, saved.getDurationSeconds());
        assertEquals("2m 8s", saved.getDurationFormatted());
    }

    @Test
    void recordSession_overridesBookType() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));
        request.setBookType(BookFileType.FB2);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(BookFileType.FB2, saved.getBookType());
    }

    @Test
    void recordSession_ignoresBookTypeNoFiles() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        // Clear out the books attached
        bookEntity.setBookFiles(List.of());

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertNull(saved.getBookType());
    }

    @Test
    void recordSession_infersBookType() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(BookFileType.EPUB, saved.getBookType());
    }

    @Test
    void recordSession_overridesProgressDelta() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        request.setStartProgress(12.0f);
        request.setEndProgress(14.3f);

        // Wow, you read a lot of that book!
        request.setProgressDelta(123.0f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(123.0f, saved.getProgressDelta());
    }

    @Test
    void recordSession_ignoresMissingProgress() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertNull(saved.getProgressDelta());
    }

    @Test
    void recordSession_infersProgressDelta() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        request.setStartProgress(12.0f);
        request.setEndProgress(14.3f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(2.3f, saved.getProgressDelta(), 0.001f);
    }

    @Test
    void recordSession_infersProgressDeltaInverted() {
        ReadingSessionRequest request = new ReadingSessionRequest();

        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(1778173000));
        request.setEndTime(Instant.ofEpochSecond(1778173128));

        request.setStartProgress(14.3f);
        request.setEndProgress(12.0f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());

        ReadingSessionEntity saved = captor.getValue();
        assertEquals(2.3f, saved.getProgressDelta(), 0.001f);
    }
}
