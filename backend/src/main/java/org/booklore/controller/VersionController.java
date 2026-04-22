package org.booklore.controller;

import org.booklore.model.dto.ReleaseNote;
import org.booklore.model.dto.VersionInfo;
import org.booklore.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/version")
@Tag(name = "Version", description = "Endpoints for retrieving application version and changelog")
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "Get application version", description = "Retrieve the current application version.")
    @ApiResponse(responseCode = "200", description = "Version info returned successfully")
    @GetMapping
    public ResponseEntity<VersionInfo> getVersionInfo() {
        return ResponseEntity.ok(versionService.getVersionInfo());
    }

    @Operation(summary = "Get changelog since current version", description = "Retrieve the changelog since the current version.")
    @ApiResponse(responseCode = "200", description = "Changelog returned successfully")
    @GetMapping("/changelog")
    public ResponseEntity<List<ReleaseNote>> getChangelogSinceCurrent() {
        return ResponseEntity.ok(versionService.getChangelogSinceCurrentVersion());
    }
}