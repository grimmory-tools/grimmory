package org.booklore.controller;

import org.booklore.model.dto.ReleaseNote;
import org.booklore.model.dto.VersionInfo;
import org.booklore.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/version")
@Tag(name = "Version", description = "Endpoints for retrieving application version and changelog")
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "Get application version", description = "Retrieve the current application version.")
    @ApiResponse(responseCode = "200", description = "Version info returned successfully")
    @GetMapping
    public ResponseEntity<VersionInfo> getVersionInfo(WebRequest request) {
        VersionInfo info = versionService.getVersionInfo();
        String etag = info.getCurrent() + ":" + info.getLatest();
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
                .eTag(etag)
                .body(info);
    }

    @Operation(summary = "Get changelog since current version", description = "Retrieve the changelog since the current version.")
    @ApiResponse(responseCode = "200", description = "Changelog returned successfully")
    @GetMapping("/changelog")
    public ResponseEntity<List<ReleaseNote>> getChangelogSinceCurrent(WebRequest request) {
        List<ReleaseNote> changelog = versionService.getChangelogSinceCurrentVersion();
        if (changelog == null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(List.of());
        }

        String etag = changelog.stream()
                .map(note -> note.version() + ":" + note.publishedAt())
                .collect(Collectors.joining("|"));
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
                .eTag(etag)
                .body(changelog);
    }
}