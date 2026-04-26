package org.grimmory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.service.oidc.OidcGroupMappingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/oidc-group-mappings")
@PreAuthorize("@securityUtil.isAdmin()")
@AllArgsConstructor
@Tag(name = "OIDC Group Mappings", description = "Endpoints for managing OIDC group-to-role mappings")
public class OidcGroupMappingController {

    private final OidcGroupMappingService oidcGroupMappingService;

    @Operation(
            summary = "List OIDC group mappings",
            description = "Retrieve all configured OIDC group mappings.",
            operationId = "oidcGroupMappingGetAll"
    )
    @GetMapping
    public List<OidcGroupMapping> getAll() {
        return oidcGroupMappingService.getAll();
    }

    @Operation(
            summary = "Create OIDC group mapping",
            description = "Create a new OIDC group mapping.",
            operationId = "oidcGroupMappingCreate"
    )
    @PostMapping
    public OidcGroupMapping create(@Valid @RequestBody OidcGroupMapping mapping) {
        return oidcGroupMappingService.create(mapping);
    }

    @Operation(
            summary = "Update OIDC group mapping",
            description = "Update an existing OIDC group mapping by ID.",
            operationId = "oidcGroupMappingUpdate"
    )
    @PutMapping("/{id}")
    public OidcGroupMapping update(@PathVariable Long id, @Valid @RequestBody OidcGroupMapping mapping) {
        return oidcGroupMappingService.update(id, mapping);
    }

    @Operation(
            summary = "Delete OIDC group mapping",
            description = "Delete an OIDC group mapping by ID.",
            operationId = "oidcGroupMappingDelete"
    )
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        oidcGroupMappingService.delete(id);
    }
}
