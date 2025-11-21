package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.service.kobo.KoboSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public ResponseEntity<KoboSyncSettings> getSettings() {
        KoboSyncSettings settings = koboService.getCurrentUserSettings();
        return ResponseEntity.ok(settings);
    }

    @Operation(summary = "Create or update Kobo token", description = "Create or update the Kobo sync token for the current user. Requires sync permission or admin.")
    @ApiResponse(responseCode = "200", description = "Token created/updated successfully")
    @PutMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> createOrUpdateToken() {
        KoboSyncSettings updated = koboService.createOrUpdateToken();
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Toggle Kobo sync", description = "Enable or disable Kobo sync for the current user. Requires sync permission or admin.")
    @ApiResponse(responseCode = "204", description = "Sync toggled successfully")
    @PutMapping("/sync")
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> toggleSync(
            @Parameter(description = "Enable or disable sync") @RequestParam boolean enabled) {
        koboService.setSyncEnabled(enabled);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update progress thresholds", description = "Update the progress thresholds for marking books as reading or finished. Requires sync permission or admin.")
    @ApiResponse(responseCode = "200", description = "Thresholds updated successfully")
    @PutMapping("/progress-thresholds")
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> updateProgressThresholds(
            @Parameter(description = "Progress percentage to mark as reading (0-100)") @RequestParam(required = false) Float readingThreshold,
            @Parameter(description = "Progress percentage to mark as finished (0-100)") @RequestParam(required = false) Float finishedThreshold) {
        KoboSyncSettings updated = koboService.updateProgressThresholds(readingThreshold, finishedThreshold);
        return ResponseEntity.ok(updated);
    }
}
