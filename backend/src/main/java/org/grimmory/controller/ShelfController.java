package org.booklore.controller;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.service.ShelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping(("/api/v1/shelves"))
@Tag(name = "Shelves", description = "Endpoints for managing shelves and their books")
public class ShelfController {

    private final ShelfService shelfService;

    @Operation(summary = "Get all shelves", description = "Retrieve a list of all shelves.")
    @ApiResponse(responseCode = "200", description = "Shelves returned successfully")
    @GetMapping
    public ResponseEntity<List<Shelf>> getAllShelves() {
        return ResponseEntity.ok(shelfService.getShelves());
    }

    @Operation(summary = "Get a shelf by ID", description = "Retrieve details of a specific shelf by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shelf details returned successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/{shelfId}")
    @PreAuthorize("@securityUtil.canReadShelf(#shelfId)")
    public ResponseEntity<Shelf> getShelf(
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        return ResponseEntity.ok(shelfService.getShelf(shelfId));
    }

    @Operation(summary = "Create a shelf", description = "Create a new shelf.")
    @ApiResponse(responseCode = "201", description = "Shelf created successfully")
    @PostMapping
    public ResponseEntity<Shelf> createShelf(
            @Parameter(description = "Shelf creation request") @Valid @RequestBody ShelfCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shelfService.createShelf(request));
    }

    @Operation(summary = "Update a shelf", description = "Update an existing shelf by its ID.")
    @ApiResponse(responseCode = "200", description = "Shelf updated successfully")
    @PutMapping("/{shelfId}")
    @PreAuthorize("@securityUtil.isShelfOwner(#shelfId)")
    public ResponseEntity<Shelf> updateShelf(
            @Parameter(description = "Shelf update request") @Valid @RequestBody ShelfCreateRequest request,
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        return ResponseEntity.ok(shelfService.updateShelf(shelfId, request));
    }

    @Operation(summary = "Delete a shelf", description = "Delete a shelf by its ID.")
    @ApiResponse(responseCode = "204", description = "Shelf deleted successfully")
    @DeleteMapping("/{shelfId}")
    @PreAuthorize("@securityUtil.isShelfOwner(#shelfId)")
    public ResponseEntity<Void> deleteShelf(
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        shelfService.deleteShelf(shelfId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Get books on a shelf", description = "Retrieve all books assigned to a specific shelf.")
    @ApiResponse(responseCode = "200", description = "Books returned successfully")
    @GetMapping("/{shelfId}/books")
    @PreAuthorize("@securityUtil.canReadShelf(#shelfId)")
    public ResponseEntity<List<Book>> getShelfBooks(
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        return ResponseEntity.ok(shelfService.getShelfBooks(shelfId));
    }
}
