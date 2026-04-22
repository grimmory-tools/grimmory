package org.booklore.controller;

import org.booklore.model.dto.MagicShelf;
import org.booklore.service.MagicShelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/magic-shelves")
@Tag(name = "Magic Shelves", description = "Endpoints for managing user-defined magic shelves")
public class MagicShelfController {

    private final MagicShelfService magicShelfService;

    public MagicShelfController(MagicShelfService magicShelfService) {
        this.magicShelfService = magicShelfService;
    }

    @Operation(summary = "Get all magic shelves for user", description = "Retrieve all magic shelves for the current user.")
    @ApiResponse(responseCode = "200", description = "Magic shelves returned successfully")
    @GetMapping
    public ResponseEntity<List<MagicShelf>> getAllForUser() {
        return ResponseEntity.ok(magicShelfService.getUserShelves());
    }

    @Operation(summary = "Get a magic shelf by ID", description = "Retrieve a specific magic shelf by its ID.")
    @ApiResponse(responseCode = "200", description = "Magic shelf returned successfully")
    @GetMapping("/{id}")
    public ResponseEntity<MagicShelf> getShelf(@Parameter(description = "ID of the magic shelf") @PathVariable Long id) {
        return ResponseEntity.ok(magicShelfService.getShelf(id));
    }

    @Operation(summary = "Create or update a magic shelf", description = "Create or update a magic shelf for the user.")
    @ApiResponse(responseCode = "200", description = "Magic shelf created/updated successfully")
    @PostMapping
    public ResponseEntity<MagicShelf> createUpdateShelf(@Parameter(description = "Magic shelf object") @Valid @RequestBody MagicShelf shelf) {
        return ResponseEntity.ok(magicShelfService.createOrUpdateShelf(shelf));
    }

    @Operation(summary = "Delete a magic shelf", description = "Delete a specific magic shelf by its ID.")
    @ApiResponse(responseCode = "204", description = "Magic shelf deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShelf(@Parameter(description = "ID of the magic shelf") @PathVariable Long id) {
        magicShelfService.deleteShelf(id);
        return ResponseEntity.noContent().build();
    }
}