package org.booklore.controller;

import org.booklore.model.dto.NotebookBookOption;
import org.booklore.model.dto.NotebookEntry;
import org.booklore.service.book.NotebookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notebook")
@Tag(name = "Notebook", description = "Endpoints for the annotation notebook view")
public class NotebookController {

    private final NotebookService notebookService;

    @Operation(summary = "Get paginated notebook entries")
    @GetMapping
    public Page<NotebookEntry> getNotebookEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Set<String> types,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "desc") String sort) {
        return notebookService.getNotebookEntries(page, size, types, bookId, search, sort);
    }

    @Operation(summary = "Get all notebook entries for export")
    @GetMapping("/export")
    public List<NotebookEntry> exportNotebookEntries(
            @RequestParam(required = false) Set<String> types,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "desc") String sort) {
        return notebookService.getAllNotebookEntries(types, bookId, search, sort);
    }

    @Operation(summary = "Get books that have annotations")
    @GetMapping("/books")
    public List<NotebookBookOption> getBooksWithAnnotations(
            @RequestParam(required = false) String search) {
        return notebookService.getBooksWithAnnotations(search);
    }
}
