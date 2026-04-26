package org.grimmory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.dto.settings.SettingRequest;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.oidc.OidcDiagnosticService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;

import java.util.List;

@Tag(name = "App Settings", description = "Endpoints for retrieving and updating application settings")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/settings")
public class AppSettingController {

    private final AppSettingService appSettingService;
    private final OidcDiagnosticService oidcDiagnosticService;
    private final AuditService auditService;

    @Operation(summary = "Get application settings", description = "Retrieve all application settings.")
    @ApiResponse(responseCode = "200", description = "Application settings returned successfully")
    @GetMapping
    public AppSettings getAppSettings() {
        return appSettingService.getAppSettings();
    }

    @Operation(summary = "Update application settings", description = "Update one or more application settings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("@securityUtil.isAdmin()")
    @PutMapping
    public void updateSettings(@Parameter(description = "List of settings to update") @RequestBody List<SettingRequest> settingRequests) throws JacksonException {
        for (SettingRequest settingRequest : settingRequests) {
            AppSettingKey key = AppSettingKey.valueOf(settingRequest.getName());
            appSettingService.updateSetting(key, settingRequest.getValue());
        }
    }

    @PostMapping("/oidc/test")
    @PreAuthorize("@securityUtil.isAdmin()")
    public OidcDiagnosticService.OidcTestResult testOidcConnection(@RequestBody OidcProviderDetails providerDetails) {
        var result = oidcDiagnosticService.testConnection(providerDetails);
        auditService.log(AuditAction.OIDC_CONNECTION_TEST, "OIDC connection test: " + (result.success() ? "passed" : "failed"));
        return result;
    }
}