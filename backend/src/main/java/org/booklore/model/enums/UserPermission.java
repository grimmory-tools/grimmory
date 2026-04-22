package org.booklore.model.enums;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.UserUpdateRequest;
import org.booklore.model.entity.UserPermissionsEntity;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Getter
public enum UserPermission {
    IS_ADMIN("Admin access",
            BookLoreUser.UserPermissions::isAdmin,
            BookLoreUser.UserPermissions::setAdmin,
            UserUpdateRequest.Permissions::isAdmin,
            UserPermissionsEntity::isPermissionAdmin,
            UserPermissionsEntity::setPermissionAdmin
    ),
    CAN_UPLOAD(
            "Upload books",
            BookLoreUser.UserPermissions::isCanUpload,
            BookLoreUser.UserPermissions::setCanUpload,
            UserUpdateRequest.Permissions::isCanUpload,
            UserPermissionsEntity::isPermissionUpload,
            UserPermissionsEntity::setPermissionUpload
    ),
    CAN_DOWNLOAD(
            "Download books",
            BookLoreUser.UserPermissions::isCanDownload,
            BookLoreUser.UserPermissions::setCanDownload,
            UserUpdateRequest.Permissions::isCanDownload,
            UserPermissionsEntity::isPermissionDownload,
            UserPermissionsEntity::setPermissionDownload
    ),
    CAN_EDIT_METADATA(
            "Edit metadata",
            BookLoreUser.UserPermissions::isCanEditMetadata,
            BookLoreUser.UserPermissions::setCanEditMetadata,
            UserUpdateRequest.Permissions::isCanEditMetadata,
            UserPermissionsEntity::isPermissionEditMetadata,
            UserPermissionsEntity::setPermissionEditMetadata
    ),
    CAN_MANAGE_LIBRARY(
            "Manage library",
            BookLoreUser.UserPermissions::isCanManageLibrary,
            BookLoreUser.UserPermissions::setCanManageLibrary,
            UserUpdateRequest.Permissions::isCanManageLibrary,
            UserPermissionsEntity::isPermissionManageLibrary,
            UserPermissionsEntity::setPermissionManageLibrary
    ),
    CAN_SYNC_KOREADER(
            "Sync KoReader",
            BookLoreUser.UserPermissions::isCanSyncKoReader,
            BookLoreUser.UserPermissions::setCanSyncKoReader,
            UserUpdateRequest.Permissions::isCanSyncKoReader,
            UserPermissionsEntity::isPermissionSyncKoreader,
            UserPermissionsEntity::setPermissionSyncKoreader
    ),
    CAN_SYNC_KOBO(
            "Sync Kobo",
            BookLoreUser.UserPermissions::isCanSyncKobo,
            BookLoreUser.UserPermissions::setCanSyncKobo,
            UserUpdateRequest.Permissions::isCanSyncKobo,
            UserPermissionsEntity::isPermissionSyncKobo,
            UserPermissionsEntity::setPermissionSyncKobo
    ),
    CAN_EMAIL_BOOK(
            "Email books",
            BookLoreUser.UserPermissions::isCanEmailBook,
            BookLoreUser.UserPermissions::setCanEmailBook,
            UserUpdateRequest.Permissions::isCanEmailBook,
            UserPermissionsEntity::isPermissionEmailBook,
            UserPermissionsEntity::setPermissionEmailBook
    ),
    CAN_DELETE_BOOK(
            "Delete books",
            BookLoreUser.UserPermissions::isCanDeleteBook,
            BookLoreUser.UserPermissions::setCanDeleteBook,
            UserUpdateRequest.Permissions::isCanDeleteBook,
            UserPermissionsEntity::isPermissionDeleteBook,
            UserPermissionsEntity::setPermissionDeleteBook
    ),
    CAN_ACCESS_OPDS(
            "Access OPDS",
            BookLoreUser.UserPermissions::isCanAccessOpds,
            BookLoreUser.UserPermissions::setCanAccessOpds,
            UserUpdateRequest.Permissions::isCanAccessOpds,
            UserPermissionsEntity::isPermissionAccessOpds,
            UserPermissionsEntity::setPermissionAccessOpds
    ),
    CAN_MANAGE_METADATA_CONFIG(
            "Manage metadata config",
            BookLoreUser.UserPermissions::isCanManageMetadataConfig,
            BookLoreUser.UserPermissions::setCanManageMetadataConfig,
            UserUpdateRequest.Permissions::isCanManageMetadataConfig,
            UserPermissionsEntity::isPermissionManageMetadataConfig,
            UserPermissionsEntity::setPermissionManageMetadataConfig
    ),
    CAN_ACCESS_BOOKDROP(
            "Access bookdrop",
            BookLoreUser.UserPermissions::isCanAccessBookdrop,
            BookLoreUser.UserPermissions::setCanAccessBookdrop,
            UserUpdateRequest.Permissions::isCanAccessBookdrop,
            UserPermissionsEntity::isPermissionAccessBookdrop,
            UserPermissionsEntity::setPermissionAccessBookdrop
    ),
    CAN_ACCESS_LIBRARY_STATS(
            "Access library stats",
            BookLoreUser.UserPermissions::isCanAccessLibraryStats,
            BookLoreUser.UserPermissions::setCanAccessLibraryStats,
            UserUpdateRequest.Permissions::isCanAccessLibraryStats,
            UserPermissionsEntity::isPermissionAccessLibraryStats,
            UserPermissionsEntity::setPermissionAccessLibraryStats
    ),
    CAN_ACCESS_USER_STATS(
            "Access user stats",
            BookLoreUser.UserPermissions::isCanAccessUserStats,
            BookLoreUser.UserPermissions::setCanAccessUserStats,
            UserUpdateRequest.Permissions::isCanAccessUserStats,
            UserPermissionsEntity::isPermissionAccessUserStats,
            UserPermissionsEntity::setPermissionAccessUserStats
    ),
    CAN_ACCESS_TASK_MANAGER(
            "Access task manager",
            BookLoreUser.UserPermissions::isCanAccessTaskManager,
            BookLoreUser.UserPermissions::setCanAccessTaskManager,
            UserUpdateRequest.Permissions::isCanAccessTaskManager,
            UserPermissionsEntity::isPermissionAccessTaskManager,
            UserPermissionsEntity::setPermissionAccessTaskManager
    ),
    CAN_MANAGE_GLOBAL_PREFERENCES(
            "Manage global preferences",
            BookLoreUser.UserPermissions::isCanManageGlobalPreferences,
            BookLoreUser.UserPermissions::setCanManageGlobalPreferences,
            UserUpdateRequest.Permissions::isCanManageGlobalPreferences,
            UserPermissionsEntity::isPermissionManageGlobalPreferences,
            UserPermissionsEntity::setPermissionManageGlobalPreferences
    ),
    CAN_MANAGE_ICONS(
            "Manage icons",
            BookLoreUser.UserPermissions::isCanManageIcons,
            BookLoreUser.UserPermissions::setCanManageIcons,
            UserUpdateRequest.Permissions::isCanManageIcons,
            UserPermissionsEntity::isPermissionManageIcons,
            UserPermissionsEntity::setPermissionManageIcons
    ),
    CAN_MANAGE_FONTS(
            "Manage fonts",
            BookLoreUser.UserPermissions::isCanManageFonts,
            BookLoreUser.UserPermissions::setCanManageFonts,
            UserUpdateRequest.Permissions::isCanManageFonts,
            UserPermissionsEntity::isPermissionManageFonts,
            UserPermissionsEntity::setPermissionManageFonts
    ),
    CAN_BULK_AUTO_FETCH_METADATA(
            "Bulk auto fetch metadata",
            BookLoreUser.UserPermissions::isCanBulkAutoFetchMetadata,
            BookLoreUser.UserPermissions::setCanBulkAutoFetchMetadata,
            UserUpdateRequest.Permissions::isCanBulkAutoFetchMetadata,
            UserPermissionsEntity::isPermissionBulkAutoFetchMetadata,
            UserPermissionsEntity::setPermissionBulkAutoFetchMetadata
    ),
    CAN_BULK_CUSTOM_FETCH_METADATA(
            "Bulk custom fetch metadata",
            BookLoreUser.UserPermissions::isCanBulkCustomFetchMetadata,
            BookLoreUser.UserPermissions::setCanBulkCustomFetchMetadata,
            UserUpdateRequest.Permissions::isCanBulkCustomFetchMetadata,
            UserPermissionsEntity::isPermissionBulkCustomFetchMetadata,
            UserPermissionsEntity::setPermissionBulkCustomFetchMetadata
    ),
    CAN_BULK_EDIT_METADATA(
            "Bulk edit metadata",
            BookLoreUser.UserPermissions::isCanBulkEditMetadata,
            BookLoreUser.UserPermissions::setCanBulkEditMetadata,
            UserUpdateRequest.Permissions::isCanBulkEditMetadata,
            UserPermissionsEntity::isPermissionBulkEditMetadata,
            UserPermissionsEntity::setPermissionBulkEditMetadata
    ),
    CAN_BULK_REGENERATE_COVER(
            "Bulk regenerate cover",
            BookLoreUser.UserPermissions::isCanBulkRegenerateCover,
            BookLoreUser.UserPermissions::setCanBulkRegenerateCover,
            UserUpdateRequest.Permissions::isCanBulkRegenerateCover,
            UserPermissionsEntity::isPermissionBulkRegenerateCover,
            UserPermissionsEntity::setPermissionBulkRegenerateCover
    ),
    CAN_MOVE_ORGANIZE_FILES(
            "Move/organize files",
            BookLoreUser.UserPermissions::isCanMoveOrganizeFiles,
            BookLoreUser.UserPermissions::setCanMoveOrganizeFiles,
            UserUpdateRequest.Permissions::isCanMoveOrganizeFiles,
            UserPermissionsEntity::isPermissionMoveOrganizeFiles,
            UserPermissionsEntity::setPermissionMoveOrganizeFiles
    ),
    CAN_BULK_LOCK_UNLOCK_METADATA(
            "Bulk lock/unlock metadata",
            BookLoreUser.UserPermissions::isCanBulkLockUnlockMetadata,
            BookLoreUser.UserPermissions::setCanBulkLockUnlockMetadata,
            UserUpdateRequest.Permissions::isCanBulkLockUnlockMetadata,
            UserPermissionsEntity::isPermissionBulkLockUnlockMetadata,
            UserPermissionsEntity::setPermissionBulkLockUnlockMetadata
    ),
    CAN_BULK_RESET_BOOKLORE_READ_PROGRESS(
            "Bulk reset Booklore read progress",
            BookLoreUser.UserPermissions::isCanBulkResetBookloreReadProgress,
            BookLoreUser.UserPermissions::setCanBulkResetBookloreReadProgress,
            UserUpdateRequest.Permissions::isCanBulkResetBookloreReadProgress,
            UserPermissionsEntity::isPermissionBulkResetBookloreReadProgress,
            UserPermissionsEntity::setPermissionBulkResetBookloreReadProgress
    ),
    CAN_BULK_RESET_KOREADER_READ_PROGRESS(
            "Bulk reset KoReader read progress",
            BookLoreUser.UserPermissions::isCanBulkResetKoReaderReadProgress,
            BookLoreUser.UserPermissions::setCanBulkResetKoReaderReadProgress,
            UserUpdateRequest.Permissions::isCanBulkResetKoReaderReadProgress,
            UserPermissionsEntity::isPermissionBulkResetKoReaderReadProgress,
            UserPermissionsEntity::setPermissionBulkResetKoReaderReadProgress
    ),
    CAN_BULK_RESET_BOOK_READ_STATUS(
            "Bulk reset book read status",
            BookLoreUser.UserPermissions::isCanBulkResetBookReadStatus,
            BookLoreUser.UserPermissions::setCanBulkResetBookReadStatus,
            UserUpdateRequest.Permissions::isCanBulkResetBookReadStatus,
            UserPermissionsEntity::isPermissionBulkResetBookReadStatus,
            UserPermissionsEntity::setPermissionBulkResetBookReadStatus
    );

    private final String description;
    private final Predicate<BookLoreUser.UserPermissions> dtoGetter;
    private final BiConsumer<BookLoreUser.UserPermissions, Boolean> dtoSetter;
    private final Function<UserUpdateRequest.Permissions, Boolean> requestGetter;
    private final Predicate<UserPermissionsEntity> entityGetter;
    private final BiConsumer<UserPermissionsEntity, Boolean> entitySetter;

    UserPermission(
            String description,
            Predicate<BookLoreUser.UserPermissions> dtoGetter,
            BiConsumer<BookLoreUser.UserPermissions, Boolean> dtoSetter,
            Function<UserUpdateRequest.Permissions, Boolean> requestGetter,
            Predicate<UserPermissionsEntity> entityGetter,
            BiConsumer<UserPermissionsEntity, Boolean> entitySetter
    ) {
        this.description = description;
        this.dtoGetter = dtoGetter;
        this.dtoSetter = dtoSetter;
        this.requestGetter = requestGetter;
        this.entityGetter = entityGetter;
        this.entitySetter = entitySetter;
    }

    public boolean isGranted(BookLoreUser.UserPermissions permissions) {
        return permissions != null && dtoGetter.test(permissions);
    }

    public void setInDto(BookLoreUser.UserPermissions dto, boolean value) {
        if (dto != null) {
            dtoSetter.accept(dto, value);
        }
    }

    public boolean getFromEntity(UserPermissionsEntity entity) {
        return entity != null && entityGetter.test(entity);
    }

    public void setInEntity(UserPermissionsEntity entity, boolean value) {
        if (entity != null) {
            entitySetter.accept(entity, value);
        }
    }

    public boolean getFromRequest(UserUpdateRequest.Permissions request) {
        return request != null && requestGetter.apply(request);
    }

    public static void copyFromEntityToDto(UserPermissionsEntity source, BookLoreUser.UserPermissions target) {
        if (source == null || target == null) return;
        for (UserPermission permission : values()) {
            permission.setInDto(target, permission.getFromEntity(source));
        }
    }

    public static void copyFromRequestToEntity(UserUpdateRequest.Permissions source, UserPermissionsEntity target) {
        if (source == null || target == null) return;
        for (UserPermission permission : values()) {
            permission.setInEntity(target, permission.getFromRequest(source));
        }
    }
}
