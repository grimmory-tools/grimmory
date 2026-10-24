package org.booklore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.booklore.service.upload.lcp.LcpSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users/me/lcp-settings")
@PreAuthorize("isAuthenticated()")
public class LcpSettingsController {
    private final LcpSettingsService settings;

    public record Status(boolean hasDefaultPassphrase) { }
    public record Update(@NotBlank @Size(max = 1024) String passphrase) {
        @Override
        public String toString() { return "LCP settings update [redacted]"; }
    }

    @GetMapping
    public Status get() { return new Status(settings.hasDefaultPassphrase()); }

    @PutMapping
    public ResponseEntity<Void> save(@Valid @RequestBody Update request) {
        settings.save(request.passphrase());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        settings.clear();
        return ResponseEntity.noContent().build();
    }
}
