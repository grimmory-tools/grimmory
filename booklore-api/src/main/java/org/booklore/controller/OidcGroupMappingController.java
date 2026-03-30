package org.booklore.controller;

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
public class OidcGroupMappingController {

    private final OidcGroupMappingService oidcGroupMappingService;

    @GetMapping
    public List<OidcGroupMapping> getAll() {
        return oidcGroupMappingService.getAll();
    }

    @PostMapping
    public OidcGroupMapping create(@Valid @RequestBody OidcGroupMapping mapping) {
        return oidcGroupMappingService.create(mapping);
    }

    @PutMapping("/{id}")
    public OidcGroupMapping update(@PathVariable Long id, @Valid @RequestBody OidcGroupMapping mapping) {
        return oidcGroupMappingService.update(id, mapping);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        oidcGroupMappingService.delete(id);
    }
}
