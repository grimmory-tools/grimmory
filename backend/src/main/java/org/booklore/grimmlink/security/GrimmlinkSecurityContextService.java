package org.booklore.grimmlink.security;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrimmlinkSecurityContextService {

    private final UserRepository userRepository;

    public KoreaderUserDetails currentDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof KoreaderUserDetails details)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Authentication required");
        }
        return details;
    }

    public BookLoreUserEntity currentUser() {
        KoreaderUserDetails details = currentDetails();
        if (details.getBookLoreUserId() == null) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("GrimmLink user is not linked to a Grimmory user");
        }
        return userRepository.findByIdWithDetails(details.getBookLoreUserId())
                .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("Authenticated user no longer exists"));
    }
}
