package org.booklore.controller;

import org.booklore.model.dto.settings.PublicAppSetting;
import org.booklore.service.appsettings.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public-settings")
@RequiredArgsConstructor
@Tag(name = "Public App Settings", description = "Endpoints for retrieving public application settings")
public class PublicAppSettingController {

    private final AppSettingService appSettingService;

    @Operation(summary = "Get public app settings", description = "Retrieve public application settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    public ResponseEntity<PublicAppSetting> getPublicSettings(WebRequest request) {
        PublicAppSetting settings = appSettingService.getPublicSettings();
        String etag = Integer.toHexString(settings.hashCode());

        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().mustRevalidate())
                .eTag(etag)
                .body(settings);
    }
}
