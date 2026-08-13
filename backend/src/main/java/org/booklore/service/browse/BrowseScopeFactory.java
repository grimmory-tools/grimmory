package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.security.policy.ContentRestrictionSpecification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrowseScopeFactory {

    private final UserContentRestrictionRepository restrictionRepository;

    public BrowseScope from(BookLoreUser user) {
        boolean isAdmin = user.getPermissions().isAdmin();
        return new BrowseScope(
                user.getId(),
                isAdmin,
                user.assignedLibraryIds(),
                isAdmin ? null : ContentRestrictionSpecification.from(restrictionRepository.findByUserId(user.getId())));
    }
}
