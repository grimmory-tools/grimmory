package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookNoteV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookNoteV2;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookNoteV2Entity;
import org.booklore.repository.BookNoteV2Repository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.book.BookNoteV2Service;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookNoteV2ServiceTest {

    private BookNoteV2Repository bookNoteV2Repository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private AuthenticationService authenticationService;
    private BookNoteV2Mapper mapper;
    private BookNoteV2Service service;

    private final Long userId = 1L;
    private final Long bookId = 2L;
    private final Long noteId = 3L;
    private final String cfi = "epubcfi(/6/4!/4/2/1:0)";
    private final String selectedText = "Some selected text";
    private final String noteContent = "My note content";
    private final String defaultColor = "#FFC107";
    private final String customColor = "#FF5733";
    private final String chapterTitle = "Chapter 1";

    @BeforeEach
    void setUp() {
        bookNoteV2Repository = mock(BookNoteV2Repository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        authenticationService = mock(AuthenticationService.class);
        mapper = mock(BookNoteV2Mapper.class);
        service = new BookNoteV2Service(bookNoteV2Repository, bookRepository, userRepository, authenticationService, mapper);

        BookLoreUser user = new BookLoreUser();
        user.setId(userId);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void getNotesForBook_returnsMappedNotes() {
        BookNoteV2Entity entity = BookNoteV2Entity.builder().id(noteId).cfi(cfi).build();
        BookNoteV2 dto = BookNoteV2.builder().id(noteId).cfi(cfi).build();
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(Collections.singletonList(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertEquals(1, result.size());
        assertEquals(noteId, result.getFirst().getId());
        assertEquals(cfi, result.getFirst().getCfi());
        verify(bookNoteV2Repository).findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId);
    }

    @Test
    void getNotesForBook_returnsEmptyList_whenNoNotes() {
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(Collections.emptyList());

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getNoteById_returnsNote_whenExists() {
        BookNoteV2Entity entity = BookNoteV2Entity.builder().id(noteId).cfi(cfi).build();
        BookNoteV2 dto = BookNoteV2.builder().id(noteId).cfi(cfi).build();
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        BookNoteV2 result = service.getNoteById(noteId);

        assertEquals(noteId, result.getId());
        assertEquals(cfi, result.getCfi());
    }

    @Test
    void getNoteById_throwsEntityNotFoundException_whenNotFound() {
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.getNoteById(noteId));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
    }

    @Test
    void createNote_createsNewNote_withDefaultColor() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .chapterTitle(chapterTitle)
                .build();

        BookEntity book = BookEntity.builder().id(bookId).build();
        BookLoreUserEntity userEntity = BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build();
        BookNoteV2Entity savedEntity = BookNoteV2Entity.builder()
                .id(noteId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(defaultColor)
                .chapterTitle(chapterTitle)
                .build();
        BookNoteV2 dto = BookNoteV2.builder()
                .id(noteId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(defaultColor)
                .chapterTitle(chapterTitle)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(bookNoteV2Repository.save(any(BookNoteV2Entity.class))).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        BookNoteV2 result = service.createNote(request);

        assertEquals(noteId, result.getId());
        assertEquals(defaultColor, result.getColor());

        ArgumentCaptor<BookNoteV2Entity> captor = ArgumentCaptor.forClass(BookNoteV2Entity.class);
        verify(bookNoteV2Repository).save(captor.capture());
        BookNoteV2Entity captured = captor.getValue();
        assertEquals(cfi, captured.getCfi());
        assertEquals(selectedText, captured.getSelectedText());
        assertEquals(noteContent, captured.getNoteContent());
        assertEquals(defaultColor, captured.getColor());
        assertEquals(chapterTitle, captured.getChapterTitle());
        assertEquals(book, captured.getBook());
        assertEquals(userEntity, captured.getUser());
    }

    @Test
    void createNote_createsNewNote_withCustomColor() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(customColor)
                .chapterTitle(chapterTitle)
                .build();

        BookEntity book = BookEntity.builder().id(bookId).build();
        BookLoreUserEntity userEntity = BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build();
        BookNoteV2Entity savedEntity = BookNoteV2Entity.builder()
                .id(noteId)
                .color(customColor)
                .build();
        BookNoteV2 dto = BookNoteV2.builder()
                .id(noteId)
                .color(customColor)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(bookNoteV2Repository.save(any(BookNoteV2Entity.class))).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        BookNoteV2 result = service.createNote(request);

        assertEquals(customColor, result.getColor());

        ArgumentCaptor<BookNoteV2Entity> captor = ArgumentCaptor.forClass(BookNoteV2Entity.class);
        verify(bookNoteV2Repository).save(captor.capture());
        assertEquals(customColor, captor.getValue().getColor());
    }

    @Test
    void createNote_throwsAPIException_whenDuplicateCfiExists() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(true);

        APIException ex = assertThrows(APIException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains("Note already exists"));
        verify(bookNoteV2Repository, never()).save(any());
    }

    @Test
    void createNote_throwsEntityNotFoundException_whenBookNotFound() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains(String.valueOf(bookId)));
        verify(bookNoteV2Repository, never()).save(any());
    }

    @Test
    void createNote_throwsEntityNotFoundException_whenUserNotFound() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        BookEntity book = BookEntity.builder().id(bookId).build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains(String.valueOf(userId)));
        verify(bookNoteV2Repository, never()).save(any());
    }

    @Test
    void updateNote_updatesAllFields() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        BookNoteV2Entity existingEntity = BookNoteV2Entity.builder()
                .id(noteId)
                .cfi(cfi)
                .noteContent(noteContent)
                .color(defaultColor)
                .chapterTitle(chapterTitle)
                .build();

        BookNoteV2Entity savedEntity = BookNoteV2Entity.builder()
                .id(noteId)
                .cfi(cfi)
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        BookNoteV2 dto = BookNoteV2.builder()
                .id(noteId)
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(existingEntity));
        when(bookNoteV2Repository.save(existingEntity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        BookNoteV2 result = service.updateNote(noteId, request);

        assertEquals("Updated content", result.getNoteContent());
        assertEquals("#00FF00", result.getColor());
        assertEquals("Updated chapter", result.getChapterTitle());

        assertEquals("Updated content", existingEntity.getNoteContent());
        assertEquals("#00FF00", existingEntity.getColor());
        assertEquals("Updated chapter", existingEntity.getChapterTitle());
    }

    @Test
    void updateNote_updatesOnlyNonNullFields() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .build();

        BookNoteV2Entity existingEntity = BookNoteV2Entity.builder()
                .id(noteId)
                .cfi(cfi)
                .noteContent(noteContent)
                .color(defaultColor)
                .chapterTitle(chapterTitle)
                .build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(existingEntity));
        when(bookNoteV2Repository.save(existingEntity)).thenReturn(existingEntity);
        when(mapper.toDto(existingEntity)).thenReturn(BookNoteV2.builder().id(noteId).build());

        service.updateNote(noteId, request);

        assertEquals("Updated content", existingEntity.getNoteContent());
        assertEquals(defaultColor, existingEntity.getColor());
        assertEquals(chapterTitle, existingEntity.getChapterTitle());
    }

    @Test
    void updateNote_throwsEntityNotFoundException_whenNoteNotFound() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.updateNote(noteId, request));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
        verify(bookNoteV2Repository, never()).save(any());
    }

    @Test
    void deleteNote_deletesExistingNote() {
        BookNoteV2Entity entity = BookNoteV2Entity.builder().id(noteId).build();
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(entity));

        service.deleteNote(noteId);

        verify(bookNoteV2Repository).delete(entity);
    }

    @Test
    void deleteNote_throwsEntityNotFoundException_whenNotFound() {
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.deleteNote(noteId));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
        verify(bookNoteV2Repository, never()).delete(any());
    }

    @Test
    void getNotesForBook_returnsMultipleNotes_orderedByCreatedAtDesc() {
        BookNoteV2Entity entity1 = BookNoteV2Entity.builder().id(1L).cfi("cfi1").build();
        BookNoteV2Entity entity2 = BookNoteV2Entity.builder().id(2L).cfi("cfi2").build();
        BookNoteV2Entity entity3 = BookNoteV2Entity.builder().id(3L).cfi("cfi3").build();

        BookNoteV2 dto1 = BookNoteV2.builder().id(1L).cfi("cfi1").build();
        BookNoteV2 dto2 = BookNoteV2.builder().id(2L).cfi("cfi2").build();
        BookNoteV2 dto3 = BookNoteV2.builder().id(3L).cfi("cfi3").build();

        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(List.of(entity3, entity2, entity1));
        when(mapper.toDto(entity3)).thenReturn(dto3);
        when(mapper.toDto(entity2)).thenReturn(dto2);
        when(mapper.toDto(entity1)).thenReturn(dto1);

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertEquals(3, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(1L, result.get(2).getId());
    }
}
