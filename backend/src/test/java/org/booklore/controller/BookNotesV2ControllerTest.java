package org.booklore.controller;

import org.booklore.model.dto.BookNoteV2;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.service.book.BookNoteV2Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookNotesV2ControllerTest {

    @Mock
    private BookNoteV2Service bookNoteV2Service;

    @InjectMocks
    private BookNotesV2Controller controller;

    private final Long bookId = 1L;
    private final Long noteId = 2L;
    private final Long userId = 3L;
    private final String cfi = "epubcfi(/6/4!/4/2/1:0)";
    private final String selectedText = "Some selected text";
    private final String noteContent = "My note content";
    private final String color = "#FFC107";
    private final String chapterTitle = "Chapter 1";

    @Test
    void getNotesForBook_returnsNotesList() {
        BookNoteV2 note1 = createSampleNote(1L);
        BookNoteV2 note2 = createSampleNote(2L);
        List<BookNoteV2> expectedNotes = List.of(note1, note2);

        when(bookNoteV2Service.getNotesForBook(bookId)).thenReturn(expectedNotes);

        List<BookNoteV2> result = controller.getNotesForBook(bookId);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        verify(bookNoteV2Service).getNotesForBook(bookId);
    }

    @Test
    void getNotesForBook_returnsEmptyList_whenNoNotes() {
        when(bookNoteV2Service.getNotesForBook(bookId)).thenReturn(Collections.emptyList());

        List<BookNoteV2> result = controller.getNotesForBook(bookId);

        assertTrue(result.isEmpty());
        verify(bookNoteV2Service).getNotesForBook(bookId);
    }

    @Test
    void getNoteById_returnsNote() {
        BookNoteV2 expectedNote = createSampleNote(noteId);

        when(bookNoteV2Service.getNoteById(noteId)).thenReturn(expectedNote);

        BookNoteV2 result = controller.getNoteById(noteId);

        assertEquals(noteId, result.getId());
        assertEquals(cfi, result.getCfi());
        assertEquals(selectedText, result.getSelectedText());
        assertEquals(noteContent, result.getNoteContent());
        assertEquals(color, result.getColor());
        assertEquals(chapterTitle, result.getChapterTitle());
        verify(bookNoteV2Service).getNoteById(noteId);
    }

    @Test
    void createNote_createsAndReturnsNote() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(color)
                .chapterTitle(chapterTitle)
                .build();

        BookNoteV2 expectedNote = createSampleNote(noteId);

        when(bookNoteV2Service.createNote(request)).thenReturn(expectedNote);

        BookNoteV2 result = controller.createNote(request);

        assertEquals(noteId, result.getId());
        assertEquals(cfi, result.getCfi());
        assertEquals(noteContent, result.getNoteContent());
        verify(bookNoteV2Service).createNote(request);
    }

    @Test
    void createNote_createsNoteWithoutOptionalFields() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        BookNoteV2 expectedNote = BookNoteV2.builder()
                .id(noteId)
                .bookId(bookId)
                .userId(userId)
                .cfi(cfi)
                .noteContent(noteContent)
                .color("#FFC107")
                .build();

        when(bookNoteV2Service.createNote(request)).thenReturn(expectedNote);

        BookNoteV2 result = controller.createNote(request);

        assertEquals(noteId, result.getId());
        assertNull(result.getSelectedText());
        assertNull(result.getChapterTitle());
        verify(bookNoteV2Service).createNote(request);
    }

    @Test
    void updateNote_updatesAndReturnsNote() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        BookNoteV2 expectedNote = BookNoteV2.builder()
                .id(noteId)
                .bookId(bookId)
                .userId(userId)
                .cfi(cfi)
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        when(bookNoteV2Service.updateNote(noteId, request)).thenReturn(expectedNote);

        BookNoteV2 result = controller.updateNote(noteId, request);

        assertEquals(noteId, result.getId());
        assertEquals("Updated content", result.getNoteContent());
        assertEquals("#00FF00", result.getColor());
        assertEquals("Updated chapter", result.getChapterTitle());
        verify(bookNoteV2Service).updateNote(noteId, request);
    }

    @Test
    void updateNote_updatesPartialFields() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content only")
                .build();

        BookNoteV2 expectedNote = BookNoteV2.builder()
                .id(noteId)
                .noteContent("Updated content only")
                .color(color)
                .chapterTitle(chapterTitle)
                .build();

        when(bookNoteV2Service.updateNote(noteId, request)).thenReturn(expectedNote);

        BookNoteV2 result = controller.updateNote(noteId, request);

        assertEquals("Updated content only", result.getNoteContent());
        assertEquals(color, result.getColor());
        assertEquals(chapterTitle, result.getChapterTitle());
        verify(bookNoteV2Service).updateNote(noteId, request);
    }

    @Test
    void deleteNote_deletesNoteAndReturnsNoContent() {
        doNothing().when(bookNoteV2Service).deleteNote(noteId);

        ResponseEntity<Void> response = controller.deleteNote(noteId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(bookNoteV2Service).deleteNote(noteId);
    }

    @Test
    void deleteNote_callsServiceWithCorrectId() {
        doNothing().when(bookNoteV2Service).deleteNote(noteId);

        controller.deleteNote(noteId);

        verify(bookNoteV2Service, times(1)).deleteNote(noteId);
    }

    @Test
    void getNotesForBook_delegatesToService() {
        List<BookNoteV2> notes = List.of(createSampleNote(1L), createSampleNote(2L), createSampleNote(3L));
        when(bookNoteV2Service.getNotesForBook(bookId)).thenReturn(notes);

        List<BookNoteV2> result = controller.getNotesForBook(bookId);

        assertEquals(3, result.size());
        verify(bookNoteV2Service, times(1)).getNotesForBook(bookId);
        verifyNoMoreInteractions(bookNoteV2Service);
    }

    private BookNoteV2 createSampleNote(Long id) {
        return BookNoteV2.builder()
                .id(id)
                .bookId(bookId)
                .userId(userId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(color)
                .chapterTitle(chapterTitle)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
