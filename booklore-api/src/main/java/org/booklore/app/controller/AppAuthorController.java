package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.service.AppAuthorService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/authors")
@Tag(name = "App Authors", description = "Endpoints for browsing authors in the app experience")
public class AppAuthorController {

    private final AppAuthorService mobileAuthorService;

    @Operation(
            summary = "List app authors",
            description = "Retrieve paginated authors for the app with optional filtering and sorting.",
            operationId = "appGetAuthors"
    )
    @GetMapping
    public ResponseEntity<AppPageResponse<AppAuthorSummary>> getAuthors(
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "30") Integer size,
            @Parameter(description = "Sort field") @RequestParam(required = false, defaultValue = "name") String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false, defaultValue = "asc") String dir,
            @Parameter(description = "Library ID") @RequestParam(required = false) Long libraryId,
            @Parameter(description = "Search query") @RequestParam(required = false) String search,
            @Parameter(description = "Has photo filter") @RequestParam(required = false) Boolean hasPhoto) {

        return ResponseEntity.ok(mobileAuthorService.getAuthors(page, size, sort, dir, libraryId, search, hasPhoto));
    }

    @Operation(
            summary = "Get app author details",
            description = "Retrieve detailed app-facing information for a single author.",
            operationId = "appGetAuthorDetail"
    )
    @GetMapping("/{authorId}")
    public ResponseEntity<AppAuthorDetail> getAuthorDetail(
            @Parameter(description = "Author ID") @PathVariable Long authorId) {

        return ResponseEntity.ok(mobileAuthorService.getAuthorDetail(authorId));
    }
}
