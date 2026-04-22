package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.ContentRestriction;
import org.booklore.service.restriction.ContentRestrictionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Content Restrictions", description = "Endpoints for managing user content restrictions")
@RestController
@RequestMapping("/api/v1/users/{userId}/content-restrictions")
@RequiredArgsConstructor
public class ContentRestrictionController {

    private final ContentRestrictionService contentRestrictionService;

    @Operation(summary = "Get user content restrictions", description = "Retrieve all content restrictions for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content restrictions retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin or own user"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.isSelf(#userId)")
    public ResponseEntity<List<ContentRestriction>> getUserRestrictions(
            @Parameter(description = "ID of the user") @PathVariable Long userId) {
        return ResponseEntity.ok(contentRestrictionService.getUserRestrictions(userId));
    }

    @Operation(summary = "Add content restriction", description = "Add a new content restriction for a user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Content restriction created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or restriction already exists"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ContentRestriction> addRestriction(
            @Parameter(description = "ID of the user") @PathVariable Long userId,
            @Parameter(description = "Content restriction to add") @RequestBody @Valid ContentRestriction restriction) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentRestrictionService.addRestriction(userId, restriction));
    }

    @Operation(summary = "Update all content restrictions", description = "Replace all content restrictions for a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content restrictions updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<List<ContentRestriction>> updateRestrictions(
            @Parameter(description = "ID of the user") @PathVariable Long userId,
            @Parameter(description = "List of content restrictions") @RequestBody @Valid List<ContentRestriction> restrictions) {
        return ResponseEntity.ok(contentRestrictionService.updateRestrictions(userId, restrictions));
    }

    @Operation(summary = "Delete content restriction", description = "Delete a specific content restriction")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Content restriction deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin"),
            @ApiResponse(responseCode = "404", description = "Content restriction not found")
    })
    @DeleteMapping("/{restrictionId}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteRestriction(
            @Parameter(description = "ID of the user") @PathVariable Long userId,
            @Parameter(description = "ID of the restriction to delete") @PathVariable Long restrictionId) {
        contentRestrictionService.deleteRestriction(restrictionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete all user content restrictions", description = "Delete all content restrictions for a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "All content restrictions deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin")
    })
    @DeleteMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteAllRestrictions(
            @Parameter(description = "ID of the user") @PathVariable Long userId) {
        contentRestrictionService.deleteAllUserRestrictions(userId);
        return ResponseEntity.noContent().build();
    }
}
