package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.service.AppBookService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App Filters", description = "Endpoints for retrieving app filter options")
public class AppFilterController {

    private final AppBookService mobileBookService;

    @Operation(
            summary = "Get app filter options",
            description = "Retrieve available filter values for app book browsing.",
            operationId = "appGetFilterOptions"
    )
    @GetMapping("/filter-options")
    public ResponseEntity<AppFilterOptions> getFilterOptions(
            @Parameter(description = "Library ID") @RequestParam(required = false) Long libraryId,
            @Parameter(description = "Shelf ID") @RequestParam(required = false) Long shelfId,
            @Parameter(description = "Magic shelf ID") @RequestParam(required = false) Long magicShelfId) {
        return ResponseEntity.ok(mobileBookService.getFilterOptions(libraryId, shelfId, magicShelfId));
    }
}
