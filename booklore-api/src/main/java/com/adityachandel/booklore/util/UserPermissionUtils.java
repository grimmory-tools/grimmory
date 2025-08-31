package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.model.enums.PermissionType;

public class UserPermissionUtils {

    public static boolean hasPermission(UserPermissionsEntity perms, PermissionType type) {
        return switch (type) {
            case UPLOAD -> perms.isPermissionUpload();
            case DOWNLOAD -> perms.isPermissionDownload();
            case EDIT_METADATA -> perms.isPermissionEditMetadata();
            case MANIPULATE_LIBRARY -> perms.isPermissionManipulateLibrary();
            case EMAIL_BOOK -> perms.isPermissionEmailBook();
            case DELETE_BOOK -> perms.isPermissionDeleteBook();
            case ACCESS_OPDS -> perms.isPermissionAccessOpds();
            case SYNC_KOREADER -> perms.isPermissionSyncKoreader();
            case SYNC_KOBO -> perms.isPermissionSyncKobo();
            case ADMIN -> perms.isPermissionAdmin();
        };
    }
}
