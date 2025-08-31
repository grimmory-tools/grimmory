package com.adityachandel.booklore.model.dto.request;

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
        private boolean canManipulateLibrary;
        private boolean canEmailBook;
        private boolean canDeleteBook;
        private boolean canAccessOpds;
        private boolean canSyncKoReader;
        private boolean canSyncKobo;
    }
}
