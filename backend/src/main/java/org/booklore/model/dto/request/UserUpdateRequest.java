package org.booklore.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UserUpdateRequest {
    private String name;
    private String email;
    private Permissions permissions;
    private List<Long> assignedLibraries;

    @Data
    public static class Permissions {
        private boolean isAdmin;
        private boolean canUpload;
        private boolean canDownload;
        private boolean canEditMetadata;
        private boolean canManageLibrary;
        private boolean canEmailBook;
        private boolean canDeleteBook;
        private boolean canAccessOpds;
        private boolean canSyncKoReader;
        private boolean canSyncKobo;
        private boolean canManageMetadataConfig;
        private boolean canAccessBookdrop;
        private boolean canAccessLibraryStats;
        private boolean canAccessUserStats;
        private boolean canAccessTaskManager;
        private boolean canManageGlobalPreferences;
        private boolean canManageIcons;
        private boolean canManageFonts;
        private boolean canBulkAutoFetchMetadata;
        private boolean canBulkCustomFetchMetadata;
        private boolean canBulkEditMetadata;
        private boolean canBulkRegenerateCover;
        private boolean canMoveOrganizeFiles;
        private boolean canBulkLockUnlockMetadata;
        private boolean canBulkResetBookloreReadProgress;
        private boolean canBulkResetKoReaderReadProgress;
        private boolean canBulkResetBookReadStatus;
    }
}
