package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.dto.settings.BookPreferences;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import lombok.Data;

import java.util.List;

@Data
public class BookLoreUser {
    private Long id;
    private String username;
    private boolean isDefaultPassword;
    private String name;
    private String email;
    private ProvisioningMethod provisioningMethod;
    private List<Library> assignedLibraries;
    private UserPermissions permissions;
    private BookPreferences bookPreferences;

    @Data
    public static class UserPermissions {
        private boolean isAdmin;
        private boolean canUpload;
        private boolean canDownload;
        private boolean canEditMetadata;
        private boolean canManipulateLibrary;
        private boolean canEmailBook;
    }
}
