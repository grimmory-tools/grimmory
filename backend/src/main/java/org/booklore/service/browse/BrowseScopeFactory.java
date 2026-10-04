package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.security.policy.ContentRestrictionSpecification;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BrowseScopeFactory {

    private final UserContentRestrictionRepository restrictionRepository;

    private Set<Long> assignedLibraryIds(BookLoreUser user) {
        if (user.getAssignedLibraries() == null) {
            return Set.of();
        }

        return user.getAssignedLibraries()
                .stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }

    public BrowseScope from(BookLoreUser user) {
        boolean isAdmin = user.getPermissions().isAdmin();
        return new BrowseScope(
                user.getId(),
                isAdmin,
                assignedLibraryIds(user),
                isAdmin ? null : ContentRestrictionSpecification.from(restrictionRepository.findByUserId(user.getId())));
    }
}
