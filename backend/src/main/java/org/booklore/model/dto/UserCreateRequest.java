package org.booklore.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private boolean permissionUpload;
    private boolean permissionDownload;
    private boolean permissionEditMetadata;
    private boolean permissionManageLibrary;
    private boolean permissionEmailBook;
    private boolean permissionDeleteBook;
    private boolean permissionAccessOpds;
    private boolean permissionSyncKoreader;
    private boolean permissionSyncKobo;
    private boolean permissionAdmin;
    private boolean permissionManageMetadataConfig;
    private boolean permissionAccessBookdrop;
    private boolean permissionAccessLibraryStats;
    private boolean permissionAccessUserStats;
    private boolean permissionAccessTaskManager;
    private boolean permissionManageGlobalPreferences;
    private boolean permissionManageIcons;
    private boolean permissionManageFonts;
    private boolean permissionBulkAutoFetchMetadata;
    private boolean permissionBulkCustomFetchMetadata;
    private boolean permissionBulkEditMetadata;
    private boolean permissionBulkRegenerateCover;
    private boolean permissionMoveOrganizeFiles;
    private boolean permissionBulkLockUnlockMetadata;
    private boolean permissionBulkResetBookloreReadProgress;
    private boolean permissionBulkResetKoReaderReadProgress;
    private boolean permissionBulkResetBookReadStatus;

    private Set<Long> selectedLibraries;
}