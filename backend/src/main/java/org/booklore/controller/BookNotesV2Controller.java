package org.booklore.controller;

import org.booklore.model.dto.BookNoteV2;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.service.book.BookNoteV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/book-notes")
@Tag(name = "Book Notes V2", description = "Endpoints for managing CFI-based book notes with text selection context")
public class BookNotesV2Controller {

    private final BookNoteV2Service bookNoteV2Service;

    @Operation(summary = "Get notes for a book", description = "Retrieve all notes for a specific book.")
    @ApiResponse(responseCode = "200", description = "Notes returned successfully")
    @GetMapping("/book/{bookId}")
    public List<BookNoteV2> getNotesForBook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return bookNoteV2Service.getNotesForBook(bookId);
    }

    @Operation(summary = "Get a specific note", description = "Retrieve a specific note by its ID.")
    @ApiResponse(responseCode = "200", description = "Note returned successfully")
    @GetMapping("/{noteId}")
    public BookNoteV2 getNoteById(
            @Parameter(description = "ID of the note") @PathVariable Long noteId) {
        return bookNoteV2Service.getNoteById(noteId);
    }

    @Operation(summary = "Create a note", description = "Create a new note at a specific location in a book.")
    @ApiResponse(responseCode = "200", description = "Note created successfully")
    @PostMapping
    public BookNoteV2 createNote(
            @Parameter(description = "Note creation request") @Valid @RequestBody CreateBookNoteV2Request request) {
        return bookNoteV2Service.createNote(request);
    }

    @Operation(summary = "Update a note", description = "Update an existing note's content or color.")
    @ApiResponse(responseCode = "200", description = "Note updated successfully")
    @PutMapping("/{noteId}")
    public BookNoteV2 updateNote(
            @Parameter(description = "ID of the note to update") @PathVariable Long noteId,
            @Parameter(description = "Note update request") @Valid @RequestBody UpdateBookNoteV2Request request) {
        return bookNoteV2Service.updateNote(noteId, request);
    }

    @Operation(summary = "Delete a note", description = "Delete a specific note by its ID.")
    @ApiResponse(responseCode = "204", description = "Note deleted successfully")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @Parameter(description = "ID of the note to delete") @PathVariable Long noteId) {
        bookNoteV2Service.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
