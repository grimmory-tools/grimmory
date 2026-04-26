package org.grimmory.controller;

import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.service.kobo.KoboSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/kobo-settings")
@RequiredArgsConstructor
@Tag(name = "Kobo Settings", description = "Endpoints for managing Kobo sync settings")
public class KoboSettingsController {

    private final KoboSettingsService koboService;

    @Operation(summary = "Get Kobo sync settings", description = "Retrieve the current user's Kobo sync settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> getSettings() {
        KoboSyncSettings settings = koboService.getCurrentUserSettings();
        return ResponseEntity.ok(settings);
    }

    @Operation(summary = "Create or update Kobo token", description = "Create or update the Kobo sync token for the current user. Requires sync permission or admin.")
    @ApiResponse(responseCode = "200", description = "Token created/updated successfully")
    @PutMapping("/token")
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> createOrUpdateToken() {
        KoboSyncSettings updated = koboService.createOrUpdateToken();
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Update Kobo settings", description = "Update Kobo sync settings for the current user. Requires sync permission or admin.")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @PutMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> updateSettings(@RequestBody @Valid KoboSyncSettings settings) {
        KoboSyncSettings updated = koboService.updateSettings(settings);
        return ResponseEntity.ok(updated);
    }
}
