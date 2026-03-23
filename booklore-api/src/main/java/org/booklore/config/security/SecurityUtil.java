package org.booklore.config.security;

import lombok.AllArgsConstructor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.ShelfRepository;
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

    public boolean canManageLibrary() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanManageLibrary();
    }

    public boolean canManageIcons() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanManageIcons();
    }

    public boolean canManageFonts() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanManageFonts();
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

    public boolean canBulkEditMetadata() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanBulkEditMetadata();
    }

    public boolean canBulkLockUnlockMetadata() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanBulkLockUnlockMetadata();
    }

    public boolean canBulkRegenerateCover() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanBulkRegenerateCover();
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
            return shelfRepository.findByIdWithUser(shelfId)
                    .map(shelf -> shelf.getUser().getId().equals(user.getId()))
                    .orElse(false);
        }
        return false;
    }

    public boolean canAccessBookdrop() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanAccessBookdrop();
    }

    public boolean canAccessUserStats() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanAccessUserStats();
    }

    public boolean canAccessTaskManager() {
        var user = getCurrentUser();
        return user != null && user.getPermissions().isCanAccessTaskManager();
    }

    public boolean canReadShelf(Long shelfId) {
        var user = getCurrentUser();
        if (user != null) {
            return shelfRepository.findByIdWithUser(shelfId)
                    .map(shelf -> shelf.isPublic() || shelf.getUser().getId().equals(user.getId()))
                    .orElse(false);
        }
        return false;
    }
}
