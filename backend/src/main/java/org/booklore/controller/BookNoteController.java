package org.booklore.controller;

import org.booklore.model.dto.BookNote;
import org.booklore.model.dto.CreateBookNoteRequest;
import org.booklore.service.book.BookNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/book-notes")
@AllArgsConstructor
@Tag(name = "Book Notes", description = "Endpoints for managing notes attached to books")
public class BookNoteController {

    private final BookNoteService bookNoteService;

    @Operation(summary = "Get notes for a book", description = "Retrieve all notes for a specific book.")
    @ApiResponse(responseCode = "200", description = "Notes returned successfully")
    @GetMapping("/book/{bookId}")
    public List<BookNote> getNotesForBook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return bookNoteService.getNotesForBook(bookId);
    }

    @Operation(summary = "Create or update a note", description = "Create a new note or update an existing note for a book.")
    @ApiResponse(responseCode = "200", description = "Note created/updated successfully")
    @PostMapping
    public BookNote createNote(
            @Parameter(description = "Note creation request") @Valid @RequestBody CreateBookNoteRequest request) {
        return bookNoteService.createOrUpdateNote(request);
    }

    @Operation(summary = "Delete a note", description = "Delete a specific note by its ID.")
    @ApiResponse(responseCode = "204", description = "Note deleted successfully")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @Parameter(description = "ID of the note to delete") @PathVariable Long noteId) {
        bookNoteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
