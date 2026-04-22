package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookNoteMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookNote;
import org.booklore.model.dto.CreateBookNoteRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookNoteEntity;
import org.booklore.repository.BookNoteRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.book.BookNoteService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookNoteServiceTest {

    private BookNoteRepository bookNoteRepository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private BookNoteMapper mapper;
    private BookNoteService service;

    private final Long userId = 1L;
    private final Long bookId = 2L;
    private final Long noteId = 3L;

    @BeforeEach
    void setUp() {
        bookNoteRepository = mock(BookNoteRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        mapper = mock(BookNoteMapper.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        service = new BookNoteService(bookNoteRepository, bookRepository, userRepository, mapper, authenticationService);

        BookLoreUser user = new BookLoreUser();
        user.setId(userId);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void getNotesForBook_returnsMappedNotes() {
        BookNoteEntity entity = BookNoteEntity.builder().id(noteId).build();
        BookNote dto = BookNote.builder().id(noteId).build();
        when(bookNoteRepository.findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId, userId))
                .thenReturn(Collections.singletonList(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        List<BookNote> result = service.getNotesForBook(bookId);

        assertEquals(1, result.size());
        assertEquals(noteId, result.getFirst().getId());
    }

    @Test
    void createOrUpdateNote_createsNewNote_whenIdIsNull() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        BookEntity book = BookEntity.builder().id(bookId).build();
        BookLoreUserEntity userEntity = BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build();
        BookNoteEntity savedEntity = BookNoteEntity.builder().id(noteId).build();
        BookNote dto = BookNote.builder().id(noteId).build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(bookNoteRepository.save(any(BookNoteEntity.class))).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        BookNote result = service.createOrUpdateNote(req);

        assertEquals(noteId, result.getId());
        ArgumentCaptor<BookNoteEntity> captor = ArgumentCaptor.forClass(BookNoteEntity.class);
        verify(bookNoteRepository).save(captor.capture());
        BookNoteEntity entity = captor.getValue();
        assertEquals(book, entity.getBook());
        assertEquals(userEntity, entity.getUser());
        assertEquals("t", entity.getTitle());
        assertEquals("c", entity.getContent());
    }

    @Test
    void createOrUpdateNote_updatesExistingNote_whenIdIsPresent() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .id(noteId)
                .bookId(bookId)
                .title("new title")
                .content("new content")
                .build();

        BookNoteEntity existing = BookNoteEntity.builder().id(noteId).title("old").content("old").build();
        BookNoteEntity saved = BookNoteEntity.builder().id(noteId).title("new title").content("new content").build();
        BookNote dto = BookNote.builder().id(noteId).title("new title").content("new content").build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(BookEntity.builder().id(bookId).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build()));
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(existing));
        when(bookNoteRepository.save(existing)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(dto);

        BookNote result = service.createOrUpdateNote(req);

        assertEquals(noteId, result.getId());
        assertEquals("new title", existing.getTitle());
        assertEquals("new content", existing.getContent());
    }

    @Test
    void createOrUpdateNote_throwsIfBookNotFound() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createOrUpdateNote(req));

        assertTrue(
                ex.getMessage().contains("BOOK_NOT_FOUND") || ex.getMessage().contains(String.valueOf(bookId)),
                "Exception message should contain 'BOOK_NOT_FOUND' or the book id"
        );
    }

    @Test
    void createOrUpdateNote_throwsIfUserNotFound() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(BookEntity.builder().id(bookId).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.createOrUpdateNote(req));
    }

    @Test
    void createOrUpdateNote_throwsIfNoteNotFoundForUpdate() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .id(noteId)
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(BookEntity.builder().id(bookId).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build()));
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.createOrUpdateNote(req));
    }

    @Test
    void deleteNote_deletesIfExists() {
        BookNoteEntity entity = BookNoteEntity.builder().id(noteId).build();
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(entity));

        service.deleteNote(noteId);

        verify(bookNoteRepository).delete(entity);
    }

    @Test
    void deleteNote_throwsIfNotFound() {
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.deleteNote(noteId));
    }
}
