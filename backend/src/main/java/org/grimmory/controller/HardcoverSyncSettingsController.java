package org.booklore.controller;

import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hardcover-sync-settings")
@RequiredArgsConstructor
@Tag(name = "Hardcover Sync Settings", description = "Endpoints for managing Hardcover progress sync settings")
public class HardcoverSyncSettingsController {

    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;

    @Operation(summary = "Get Hardcover sync settings", description = "Retrieve the current user's Hardcover sync settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<HardcoverSyncSettings> getSettings() {
        HardcoverSyncSettings settings = hardcoverSyncSettingsService.getCurrentUserSettings();
        return ResponseEntity.ok(settings);
    }

    @Operation(summary = "Update Hardcover sync settings", description = "Update the current user's Hardcover sync settings.")
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @PutMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<HardcoverSyncSettings> updateSettings(@RequestBody @Valid HardcoverSyncSettings settings) {
        HardcoverSyncSettings updated = hardcoverSyncSettingsService.updateCurrentUserSettings(settings);
        return ResponseEntity.ok(updated);
    }
}
