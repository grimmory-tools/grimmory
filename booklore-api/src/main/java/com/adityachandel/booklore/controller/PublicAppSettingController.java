package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.PublicAppSetting;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public-settings")
@RequiredArgsConstructor
@Tag(name = "Public App Settings", description = "Endpoints for retrieving public application settings")
public class PublicAppSettingController {

    private final AppSettingService appSettingService;

    @Operation(summary = "Get public app settings", description = "Retrieve public application settings.")
    @ApiResponse(responseCode = "200", description = "Settings returned successfully")
    @GetMapping
    public PublicAppSetting getPublicSettings() {
        return appSettingService.getPublicSettings();
    }
}
