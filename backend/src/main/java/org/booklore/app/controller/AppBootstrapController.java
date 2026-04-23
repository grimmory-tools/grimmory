package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.app.dto.AppBootstrapResponse;
import org.booklore.app.service.AppBootstrapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/bootstrap")
@Tag(name = "App Bootstrap", description = "Consolidated initialization endpoints for the app")
public class AppBootstrapController {

    private final AppBootstrapService bootstrapService;

    @Operation(
            summary = "Get bootstrap data",
            description = "Retrieve all data needed for application startup (user, settings, version, counts, libraries, shelves) in a single request.",
            operationId = "appGetBootstrap"
    )
    @GetMapping
    public ResponseEntity<AppBootstrapResponse> getBootstrap() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(bootstrapService.getBootstrapData());
    }
}
