package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.booklore.app.dto.AppNotebookBookSummary;
import org.booklore.app.dto.AppNotebookEntry;
import org.booklore.app.dto.AppNotebookUpdateRequest;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.service.AppNotebookService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/notebook")
@Tag(name = "App Notebook", description = "Endpoints for notebook browsing and editing in the app experience")
public class AppNotebookController {

    private final AppNotebookService mobileNotebookService;

    @Operation(
            summary = "List notebook books",
            description = "Retrieve paginated books that contain notebook entries in the app.",
            operationId = "appGetBooksWithAnnotations"
    )
    @GetMapping("/books")
    public ResponseEntity<AppPageResponse<AppNotebookBookSummary>> getBooksWithAnnotations(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String search) {

        return ResponseEntity.ok(mobileNotebookService.getBooksWithAnnotations(page, size, search));
    }

    @Operation(
            summary = "List notebook entries for book",
            description = "Retrieve paginated notebook entries for a specific book in the app.",
            operationId = "appGetEntriesForBook"
    )
    @GetMapping("/books/{bookId}/entries")
    public ResponseEntity<AppPageResponse<AppNotebookEntry>> getEntriesForBook(
            @PathVariable Long bookId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) Set<String> types,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "date_desc") String sort) {

        return ResponseEntity.ok(mobileNotebookService.getEntriesForBook(bookId, page, size, types, search, sort));
    }

    @Operation(
            summary = "Update notebook entry",
            description = "Update an existing notebook entry in the app.",
            operationId = "appUpdateEntry"
    )
    @PutMapping("/entries/{entryId}")
    public ResponseEntity<AppNotebookEntry> updateEntry(
            @PathVariable Long entryId,
            @RequestParam String type,
            @Valid @RequestBody AppNotebookUpdateRequest request) {

        return ResponseEntity.ok(mobileNotebookService.updateEntry(entryId, type, request));
    }

    @Operation(
            summary = "Delete notebook entry",
            description = "Delete an existing notebook entry in the app.",
            operationId = "appDeleteEntry"
    )
    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable Long entryId,
            @RequestParam String type) {

        mobileNotebookService.deleteEntry(entryId, type);
        return ResponseEntity.noContent().build();
    }
}
