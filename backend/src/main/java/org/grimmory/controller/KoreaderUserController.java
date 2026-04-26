package org.booklore.controller;

import org.booklore.model.dto.KoreaderUser;
import org.booklore.service.koreader.KoreaderUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/koreader-users")
@RequiredArgsConstructor
@Tag(name = "KoReader Users", description = "Endpoints for managing KoReader user accounts and sync settings")
public class KoreaderUserController {

    private final KoreaderUserService koreaderUserService;

    @Operation(summary = "Get current KoReader user", description = "Retrieve the current KoReader user profile.")
    @ApiResponse(responseCode = "200", description = "User profile returned successfully")
    @GetMapping("/me")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<KoreaderUser> getCurrentUser() {
        return ResponseEntity.ok(koreaderUserService.getUser());
    }

    @Operation(summary = "Upsert current KoReader user", description = "Create or update the current KoReader user profile.")
    @ApiResponse(responseCode = "200", description = "User profile upserted successfully")
    @PutMapping("/me")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<KoreaderUser> upsertCurrentUser(
            @Parameter(description = "User data map") @RequestBody Map<String, String> userData) {
        KoreaderUser user = koreaderUserService.upsertUser(userData.get("username"), userData.get("password"));
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Toggle KoReader sync", description = "Enable or disable KoReader sync for the current user.")
    @ApiResponse(responseCode = "204", description = "Sync toggled successfully")
    @PatchMapping("/me/sync")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> updateSyncEnabled(
            @Parameter(description = "Enable or disable sync") @RequestParam boolean enabled) {
        koreaderUserService.toggleSync(enabled);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle sync progress with Booklore reader", description = "Enable or disable syncing reading progress with Booklore reader.")
    @ApiResponse(responseCode = "204", description = "Sync progress toggled successfully")
    @PatchMapping("/me/sync-progress-with-booklore")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> toggleSyncProgressWithBooklore(
            @Parameter(description = "Enable or disable sync progress") @RequestParam boolean enabled) {
        koreaderUserService.toggleSyncProgressWithBooklore(enabled);
        return ResponseEntity.noContent().build();
    }
}
