package org.booklore.util;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.PermissionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @EnumSource(PermissionType.class)
    void testHasPermission_BookLoreUserPermissions_true(PermissionType permissionType) {
        BookLoreUser.UserPermissions perms = createBookLoreUserPermissionsWith(permissionType, true);
        assertTrue(UserPermissionUtils.hasPermission(perms, permissionType));
    }

    @ParameterizedTest
    @EnumSource(PermissionType.class)
    void testHasPermission_BookLoreUserPermissions_false(PermissionType permissionType) {
        BookLoreUser.UserPermissions perms = createBookLoreUserPermissionsWith(permissionType, false);
        assertFalse(UserPermissionUtils.hasPermission(perms, permissionType));
    }

    @Test
    void testHasPermission_allPermissionsFalse() {
        UserPermissionsEntity perms = UserPermissionsEntity.builder()
                .permissionUpload(false)
                .permissionDownload(false)
                .permissionEditMetadata(false)
                .permissionManageLibrary(false)
                .permissionEmailBook(false)
                .permissionDeleteBook(false)
                .permissionAccessOpds(false)
                .permissionSyncKoreader(false)
                .permissionSyncKobo(false)
                .permissionManageMetadataConfig(false)
                .permissionAccessBookdrop(false)
                .permissionAccessLibraryStats(false)
                .permissionAccessUserStats(false)
                .permissionAccessTaskManager(false)
                .permissionManageGlobalPreferences(false)
                .permissionManageIcons(false)
                .permissionManageFonts(false)
                .permissionDemoUser(false)
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
                .permissionManageLibrary(true)
                .permissionEmailBook(true)
                .permissionDeleteBook(true)
                .permissionAccessOpds(true)
                .permissionSyncKoreader(true)
                .permissionSyncKobo(true)
                .permissionManageMetadataConfig(true)
                .permissionAccessBookdrop(true)
                .permissionAccessLibraryStats(true)
                .permissionAccessUserStats(true)
                .permissionAccessTaskManager(true)
                .permissionManageGlobalPreferences(true)
                .permissionManageIcons(true)
                .permissionManageFonts(true)
                .permissionDemoUser(true)
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
                .permissionManageLibrary(false)
                .permissionEmailBook(false)
                .permissionDeleteBook(false)
                .permissionAccessOpds(false)
                .permissionSyncKoreader(false)
                .permissionSyncKobo(false)
                .permissionManageMetadataConfig(false)
                .permissionAccessBookdrop(false)
                .permissionAccessLibraryStats(false)
                .permissionAccessUserStats(false)
                .permissionAccessTaskManager(false)
                .permissionManageGlobalPreferences(false)
                .permissionManageIcons(false)
                .permissionManageFonts(false)
                .permissionDemoUser(false)
                .permissionAdmin(false);

        switch (permissionType) {
            case UPLOAD -> builder.permissionUpload(value);
            case DOWNLOAD -> builder.permissionDownload(value);
            case EDIT_METADATA -> builder.permissionEditMetadata(value);
            case MANAGE_LIBRARY -> builder.permissionManageLibrary(value);
            case EMAIL_BOOK -> builder.permissionEmailBook(value);
            case DELETE_BOOK -> builder.permissionDeleteBook(value);
            case ACCESS_OPDS -> builder.permissionAccessOpds(value);
            case SYNC_KOREADER -> builder.permissionSyncKoreader(value);
            case SYNC_KOBO -> builder.permissionSyncKobo(value);
            case MANAGE_METADATA_CONFIG -> builder.permissionManageMetadataConfig(value);
            case ACCESS_BOOKDROP -> builder.permissionAccessBookdrop(value);
            case ACCESS_LIBRARY_STATS -> builder.permissionAccessLibraryStats(value);
            case ACCESS_USER_STATS -> builder.permissionAccessUserStats(value);
            case ACCESS_TASK_MANAGER -> builder.permissionAccessTaskManager(value);
            case MANAGE_GLOBAL_PREFERENCES -> builder.permissionManageGlobalPreferences(value);
            case MANAGE_ICONS -> builder.permissionManageIcons(value);
            case MANAGE_FONTS -> builder.permissionManageFonts(value);
            case DEMO_USER -> builder.permissionDemoUser(value);
            case ADMIN -> builder.permissionAdmin(value);
            default -> throw new IllegalArgumentException("Test helper missing mapping for PermissionType: " + permissionType);
        }

        return builder.build();
    }

    private BookLoreUser.UserPermissions createBookLoreUserPermissionsWith(PermissionType permissionType, boolean value) {
        BookLoreUser.UserPermissions perms = new BookLoreUser.UserPermissions();
        perms.setAdmin(false);
        perms.setCanUpload(false);
        perms.setCanDownload(false);
        perms.setCanEditMetadata(false);
        perms.setCanManageLibrary(false);
        perms.setCanEmailBook(false);
        perms.setCanDeleteBook(false);
        perms.setCanAccessOpds(false);
        perms.setCanSyncKoReader(false);
        perms.setCanSyncKobo(false);
        perms.setCanManageMetadataConfig(false);
        perms.setCanAccessBookdrop(false);
        perms.setCanAccessLibraryStats(false);
        perms.setCanAccessUserStats(false);
        perms.setCanAccessTaskManager(false);
        perms.setCanManageIcons(false);
        perms.setCanManageFonts(false);
        perms.setCanManageGlobalPreferences(false);
        perms.setDemoUser(false);

        switch (permissionType) {
            case UPLOAD -> perms.setCanUpload(value);
            case DOWNLOAD -> perms.setCanDownload(value);
            case EDIT_METADATA -> perms.setCanEditMetadata(value);
            case MANAGE_LIBRARY -> perms.setCanManageLibrary(value);
            case EMAIL_BOOK -> perms.setCanEmailBook(value);
            case DELETE_BOOK -> perms.setCanDeleteBook(value);
            case ACCESS_OPDS -> perms.setCanAccessOpds(value);
            case SYNC_KOREADER -> perms.setCanSyncKoReader(value);
            case SYNC_KOBO -> perms.setCanSyncKobo(value);
            case MANAGE_METADATA_CONFIG -> perms.setCanManageMetadataConfig(value);
            case ACCESS_BOOKDROP -> perms.setCanAccessBookdrop(value);
            case ACCESS_LIBRARY_STATS -> perms.setCanAccessLibraryStats(value);
            case ACCESS_USER_STATS -> perms.setCanAccessUserStats(value);
            case ACCESS_TASK_MANAGER -> perms.setCanAccessTaskManager(value);
            case MANAGE_GLOBAL_PREFERENCES -> perms.setCanManageGlobalPreferences(value);
            case MANAGE_ICONS -> perms.setCanManageIcons(value);
            case MANAGE_FONTS -> perms.setCanManageFonts(value);
            case DEMO_USER -> perms.setDemoUser(value);
            case ADMIN -> perms.setAdmin(value);
            default -> throw new IllegalArgumentException("Test helper missing mapping for PermissionType: " + permissionType);
        }

        return perms;
    }
}
