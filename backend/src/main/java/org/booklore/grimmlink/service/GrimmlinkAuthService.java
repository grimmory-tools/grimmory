package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.booklore.service.koreader.KoreaderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrimmlinkAuthService {

    private final KoreaderService koreaderService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> authorize() {
        Map<String, String> upstreamResponse = koreaderService.authorizeUser().getBody();
        BookLoreUserEntity reader = requireCurrentReader(false);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("username", upstreamResponse != null
                ? upstreamResponse.getOrDefault("username", reader.getUsername())
                : reader.getUsername());
        response.put("userId", reader.getId());
        if (reader.getKoreaderUser() != null) {
            response.put("syncEnabled", reader.getKoreaderUser().isSyncEnabled());
            response.put("syncWithWebReader", reader.getKoreaderUser().isSyncWithWebReader());
        }
        return response;
    }

    public BookLoreUserEntity requireCurrentReader(boolean requireSyncEnabled) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof KoreaderUserDetails details)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Authentication required");
        }
        if (requireSyncEnabled && !details.isSyncEnabled()) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Sync is disabled for this user");
        }
        if (details.getBookLoreUserId() == null) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("KOReader user is not linked to a Grimmory user");
        }
        return userRepository.findByIdWithDetails(details.getBookLoreUserId())
                .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException(
                        "Authenticated user no longer exists"));
    }
}
