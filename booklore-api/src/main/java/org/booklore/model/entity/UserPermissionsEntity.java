package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_permissions")
public class UserPermissionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private BookLoreUserEntity user;

    @Column(name = "permission_admin", nullable = false)
    private boolean permissionAdmin;

    @Column(name = "permission_upload", nullable = false)
    @Builder.Default
    private boolean permissionUpload = false;

    @Column(name = "permission_download", nullable = false)
    @Builder.Default
    private boolean permissionDownload = false;

    @Column(name = "permission_edit_metadata", nullable = false)
    @Builder.Default
    private boolean permissionEditMetadata = false;

    @Column(name = "permission_manipulate_library", nullable = false)
    @Builder.Default
    private boolean permissionManageLibrary = false;

    @Column(name = "permission_email_book", nullable = false)
    @Builder.Default
    private boolean permissionEmailBook = false;

    @Column(name = "permission_delete_book", nullable = false)
    @Builder.Default
    private boolean permissionDeleteBook = false;

    @Column(name = "permission_sync_koreader", nullable = false)
    @Builder.Default
    private boolean permissionSyncKoreader = false;

    @Column(name = "permission_access_opds", nullable = false)
    @Builder.Default
    private boolean permissionAccessOpds = false;

    @Column(name = "permission_sync_kobo", nullable = false)
    @Builder.Default
    private boolean permissionSyncKobo = false;

    @Column(name = "permission_manage_metadata_config", nullable = false)
    @Builder.Default
    private boolean permissionManageMetadataConfig = false;

    @Column(name = "permission_access_bookdrop", nullable = false)
    @Builder.Default
    private boolean permissionAccessBookdrop = false;

    @Column(name = "permission_access_library_stats", nullable = false)
    @Builder.Default
    private boolean permissionAccessLibraryStats = false;

    @Column(name = "permission_access_user_stats", nullable = false)
    @Builder.Default
    private boolean permissionAccessUserStats = false;

    @Column(name = "permission_access_task_manager", nullable = false)
    @Builder.Default
    private boolean permissionAccessTaskManager = false;

    @Column(name = "permission_manage_global_preferences", nullable = false)
    @Builder.Default
    private boolean permissionManageGlobalPreferences = false;

    @Column(name = "permission_manage_icons", nullable = false)
    @Builder.Default
    private boolean permissionManageIcons = false;

    @Column(name = "permission_manage_fonts", nullable = false)
    @Builder.Default
    private boolean permissionManageFonts = false;

    @Column(name = "permission_demo_user", nullable = false)
    @Builder.Default
    private boolean permissionDemoUser = false;

    @Column(name = "permission_bulk_auto_fetch_metadata", nullable = false)
    @Builder.Default
    private boolean permissionBulkAutoFetchMetadata = false;

    @Column(name = "permission_bulk_custom_fetch_metadata", nullable = false)
    @Builder.Default
    private boolean permissionBulkCustomFetchMetadata = false;

    @Column(name = "permission_bulk_edit_metadata", nullable = false)
    @Builder.Default
    private boolean permissionBulkEditMetadata = false;

    @Column(name = "permission_bulk_regenerate_cover", nullable = false)
    @Builder.Default
    private boolean permissionBulkRegenerateCover = false;

    @Column(name = "permission_move_organize_files", nullable = false)
    @Builder.Default
    private boolean permissionMoveOrganizeFiles = false;

    @Column(name = "permission_bulk_lock_unlock_metadata", nullable = false)
    @Builder.Default
    private boolean permissionBulkLockUnlockMetadata = false;

    @Column(name = "permission_bulk_reset_booklore_read_progress", nullable = false)
    @Builder.Default
    private boolean permissionBulkResetBookloreReadProgress = false;

    @Column(name = "permission_bulk_reset_koreader_read_progress", nullable = false)
    @Builder.Default
    private boolean permissionBulkResetKoReaderReadProgress = false;

    @Column(name = "permission_bulk_reset_book_read_status", nullable = false)
    @Builder.Default
    private boolean permissionBulkResetBookReadStatus = false;
}