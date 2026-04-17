package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.dto.AppSeriesSummary;
import org.booklore.app.service.AppSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/series")
@Tag(name = "App Series", description = "Endpoints for browsing series in the app experience")
public class AppSeriesController {

    private final AppSeriesService mobileSeriesService;

    @Operation(
            summary = "List app series",
            description = "Retrieve paginated series for the app with optional filtering and sorting.",
            operationId = "appGetSeries"
    )
    @GetMapping
    public ResponseEntity<AppPageResponse<AppSeriesSummary>> getSeries(
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "Sort field") @RequestParam(required = false, defaultValue = "recentlyAdded") String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false, defaultValue = "desc") String dir,
            @Parameter(description = "Library ID") @RequestParam(required = false) Long libraryId,
            @Parameter(description = "Search query") @RequestParam(required = false) String search,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status) {

        boolean inProgressOnly = "in-progress".equalsIgnoreCase(status);

        AppPageResponse<AppSeriesSummary> response = mobileSeriesService.getSeries(
                page, size, sort, dir, libraryId, search, inProgressOnly);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "List books in app series",
            description = "Retrieve paginated books belonging to a specific series for the app.",
            operationId = "appGetSeriesBooks"
    )
    @GetMapping("/{seriesName}/books")
    public ResponseEntity<AppPageResponse<AppBookSummary>> getSeriesBooks(
            @Parameter(description = "Series name") @PathVariable String seriesName,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "Sort field") @RequestParam(required = false, defaultValue = "seriesNumber") String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false, defaultValue = "asc") String dir,
            @Parameter(description = "Library ID") @RequestParam(required = false) Long libraryId) {

        AppPageResponse<AppBookSummary> response = mobileSeriesService.getSeriesBooks(
                seriesName, page, size, sort, dir, libraryId);

        return ResponseEntity.ok(response);
    }
}
