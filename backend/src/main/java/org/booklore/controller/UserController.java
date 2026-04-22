package org.booklore.controller;

import org.booklore.model.dto.request.ChangePasswordRequest;
import org.booklore.model.dto.request.ChangeUserPasswordRequest;
import org.booklore.model.dto.request.UpdateUserSettingRequest;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.UserUpdateRequest;
import org.booklore.service.user.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints for managing users and their settings")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user", description = "Retrieve the profile of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "User profile returned successfully")
    @GetMapping("/me")
    public ResponseEntity<BookLoreUser> getMyself() {
        return ResponseEntity.ok(userService.getMyself());
    }

    @Operation(summary = "Get user by ID", description = "Retrieve a user's profile by their ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile returned successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/{id}")
    @PreAuthorize("@securityUtil.canViewUserProfile(#id)")
    public ResponseEntity<BookLoreUser> getUser(@Parameter(description = "ID of the user") @PathVariable Long id) {
        return ResponseEntity.ok(userService.getBookLoreUser(id));
    }

    @Operation(summary = "Get all users", description = "Retrieve a list of all users. Requires admin.")
    @ApiResponse(responseCode = "200", description = "Users returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<List<BookLoreUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getBookLoreUsers());
    }

    @Operation(summary = "Update user", description = "Update a user's profile by their ID. Requires admin.")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @PutMapping("/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<BookLoreUser> updateUser(
            @Parameter(description = "ID of the user") @PathVariable Long id,
            @Parameter(description = "User update request") @Valid @RequestBody UserUpdateRequest updateRequest) {
        BookLoreUser updatedUser = userService.updateUser(id, updateRequest);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Delete user", description = "Delete a user by their ID. Requires admin.")
    @ApiResponse(responseCode = "204", description = "User deleted successfully")
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public void deleteUser(@Parameter(description = "ID of the user") @PathVariable Long id) {
        userService.deleteUser(id);
    }

    @Operation(summary = "Change password", description = "Change the password for the current user.")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@Parameter(description = "Change password request") @RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Change another user's password", description = "Change the password for another user. Requires admin.")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @PutMapping("/change-user-password")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<?> changeUserPassword(@Parameter(description = "Change user password request") @RequestBody @Valid ChangeUserPasswordRequest request) {
        userService.changeUserPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update user settings", description = "Update settings for the current user.")
    @ApiResponse(responseCode = "204", description = "User settings updated successfully")
    @PutMapping("/{id}/settings")
    @PreAuthorize("@securityUtil.isSelf(#id)")
    public void updateUserSetting(
            @Parameter(description = "ID of the user") @PathVariable Long id,
            @Parameter(description = "Update user setting request") @RequestBody @Valid UpdateUserSettingRequest request) {
        userService.updateUserSetting(id, request);
    }
}
