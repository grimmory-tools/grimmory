package org.booklore.model.enums;

import lombok.Getter;

@Getter
public enum PermissionType {

    ADMIN("permissionAdmin"),
    UPLOAD("permissionUpload"),
    DOWNLOAD("permissionDownload"),
    EDIT_METADATA("permissionEditMetadata"),
    MANAGE_LIBRARY("permissionManageLibrary"),
    EMAIL_BOOK("permissionEmailBook"),
    DELETE_BOOK("permissionDeleteBook"),
    SYNC_KOREADER("permissionSyncKoreader"),
    SYNC_KOBO("permissionSyncKobo"),
    ACCESS_OPDS("permissionAccessOpds"),
    MANAGE_METADATA_CONFIG("permissionManageMetadataConfig"),
    ACCESS_BOOKDROP("permissionAccessBookdrop"),
    ACCESS_LIBRARY_STATS("permissionAccessLibraryStats"),
    ACCESS_USER_STATS("permissionAccessUserStats"),
    ACCESS_TASK_MANAGER("permissionAccessTaskManager"),
    MANAGE_GLOBAL_PREFERENCES("permissionManageGlobalPreferences"),
    MANAGE_ICONS("permissionManageIcons"),
    MANAGE_FONTS("permissionManageFonts"),
    DEMO_USER("permissionDemoUser");

    private final String entityField;

    PermissionType(String entityField) {
        this.entityField = entityField;
    }
}
