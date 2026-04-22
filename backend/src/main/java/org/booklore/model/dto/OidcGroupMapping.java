package org.booklore.model.dto;

import java.util.List;

public record OidcGroupMapping(
        Long id,
        String oidcGroupClaim,
        boolean isAdmin,
        List<String> permissions,
        List<Long> libraryIds,
        String description
) {}
