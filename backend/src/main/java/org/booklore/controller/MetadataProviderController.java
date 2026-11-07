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

    @Operation(summary = "Get detailed metadata from provider", description = "Fetch full metadata details for a specific item from a provider. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Detailed metadata returned successfully")
    @GetMapping("/{provider}/fetch/{providerItemId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<BookMetadata> getDetailedProviderMetadata(
            @Parameter(description = "Metadata provider") @PathVariable MetadataProvider provider,
            @Parameter(description = "Provider-specific item ID") @PathVariable String providerItemId) {
        BookMetadata metadata = metadataProviderService.getDetailedMetadata(provider, providerItemId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }
}
