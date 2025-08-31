package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.repository.ShelfRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component("securityUtil")
public class SecurityUtil {

    private final ShelfRepository shelfRepository;

    private BookLoreUser getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof BookLoreUser user) {
            return user;
        }
        return null;
    }

    public boolean isAdmin() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isAdmin();
    }

    public boolean isSelf(Long userId) {
        var user = getCurrentUser();
        return user != null && user.getId().equals(userId);
    }

    public boolean canUpload() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanUpload();
    }

    public boolean canDownload() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanDownload();
    }

    public boolean canManipulateLibrary() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanManipulateLibrary();
    }

    public boolean canSyncKoReader() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanSyncKoReader();
    }

    public boolean canSyncKobo() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanSyncKobo();
    }

    public boolean canEditMetadata() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanEditMetadata();
    }

    public boolean canEmailBook() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanEmailBook();
    }

    public boolean canDeleteBook() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanDeleteBook();
    }
    public boolean canAccessOpds() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanAccessOpds();
    }


    public boolean canViewUserProfile(Long userId) {
        var user = getCurrentUser();
        return user != null && (user.getPermissions().isAdmin() || user.getId().equals(userId));
    }

    public boolean isShelfOwner(Long shelfId) {
        var user = getCurrentUser();
        if (user != null) {
            return shelfRepository.findById(shelfId)
                    .map(shelf -> shelf.getUser().getId().equals(user.getId()))
                    .orElse(false);
        }
        return false;
    }
}
