package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.service.KoboSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/kobo-settings")
@RequiredArgsConstructor
public class KoboSettingsController {

    private final KoboSettingsService koboService;

    @GetMapping
    public ResponseEntity<KoboSyncSettings> getSettings() {
        KoboSyncSettings settings = koboService.getCurrentUserSettings();
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<KoboSyncSettings> createOrUpdateToken() {
        KoboSyncSettings updated = koboService.createOrUpdateToken();
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/sync")
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> toggleSync(@RequestParam boolean enabled) {
        koboService.setSyncEnabled(enabled);
        return ResponseEntity.noContent().build();
    }
}
