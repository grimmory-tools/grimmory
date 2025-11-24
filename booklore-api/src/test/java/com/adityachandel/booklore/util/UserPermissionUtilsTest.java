package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class UserPermissionUtilsTest {

    @ParameterizedTest
    @EnumSource(PermissionType.class)
    void testHasPermission_true(PermissionType permissionType) {
        UserPermissionsEntity perms = createPermissionsWith(permissionType, true);
        assertTrue(UserPermissionUtils.hasPermission(perms, permissionType));
    }

    @ParameterizedTest
    @EnumSource(PermissionType.class)
    void testHasPermission_false(PermissionType permissionType) {
        UserPermissionsEntity perms = createPermissionsWith(permissionType, false);
        assertFalse(UserPermissionUtils.hasPermission(perms, permissionType));
    }

    @Test
    void testHasPermission_allPermissionsFalse() {
        UserPermissionsEntity perms = UserPermissionsEntity.builder()
                .permissionUpload(false)
                .permissionDownload(false)
                .permissionEditMetadata(false)
                .permissionManipulateLibrary(false)
                .permissionEmailBook(false)
                .permissionDeleteBook(false)
                .permissionAccessOpds(false)
                .permissionSyncKoreader(false)
                .permissionSyncKobo(false)
                .permissionAdmin(false)
                .build();

        for (PermissionType type : PermissionType.values()) {
            assertFalse(UserPermissionUtils.hasPermission(perms, type));
        }
    }

    @Test
    void testHasPermission_allPermissionsTrue() {
        UserPermissionsEntity perms = UserPermissionsEntity.builder()
                .permissionUpload(true)
                .permissionDownload(true)
                .permissionEditMetadata(true)
                .permissionManipulateLibrary(true)
                .permissionEmailBook(true)
                .permissionDeleteBook(true)
                .permissionAccessOpds(true)
                .permissionSyncKoreader(true)
                .permissionSyncKobo(true)
                .permissionAdmin(true)
                .build();

        for (PermissionType type : PermissionType.values()) {
            assertTrue(UserPermissionUtils.hasPermission(perms, type));
        }
    }

    private UserPermissionsEntity createPermissionsWith(PermissionType permissionType, boolean value) {
        UserPermissionsEntity.UserPermissionsEntityBuilder builder = UserPermissionsEntity.builder()
                .permissionUpload(false)
                .permissionDownload(false)
                .permissionEditMetadata(false)
                .permissionManipulateLibrary(false)
                .permissionEmailBook(false)
                .permissionDeleteBook(false)
                .permissionAccessOpds(false)
                .permissionSyncKoreader(false)
                .permissionSyncKobo(false)
                .permissionAdmin(false);

        switch (permissionType) {
            case UPLOAD -> builder.permissionUpload(value);
            case DOWNLOAD -> builder.permissionDownload(value);
            case EDIT_METADATA -> builder.permissionEditMetadata(value);
            case MANIPULATE_LIBRARY -> builder.permissionManipulateLibrary(value);
            case EMAIL_BOOK -> builder.permissionEmailBook(value);
            case DELETE_BOOK -> builder.permissionDeleteBook(value);
            case ACCESS_OPDS -> builder.permissionAccessOpds(value);
            case SYNC_KOREADER -> builder.permissionSyncKoreader(value);
            case SYNC_KOBO -> builder.permissionSyncKobo(value);
            case ADMIN -> builder.permissionAdmin(value);
        }

        return builder.build();
    }
}
