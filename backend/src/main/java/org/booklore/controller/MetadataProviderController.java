package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.MetadataProviderDto;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.MetadataProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metadata/providers")
@AllArgsConstructor
@Tag(name = "Metadata Providers", description = "Endpoints for managing metadata providers")
public class MetadataProviderController {

    private final MetadataProviderService metadataProviderService;

    @Operation(summary = "Get Metadata Providers", description = "Retrieves a list of providers and whether or not they're enabled.")
    @ApiResponse(responseCode = "200", description = "Successfully Retrieved Providers")
    @GetMapping()
    public ResponseEntity<List<MetadataProviderDto>> getMetadataProviders() {
        // Read metadata providers
        return ResponseEntity.ok(metadataProviderService.getProviders());
    }
}
