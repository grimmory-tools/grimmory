package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.enums.PermissionType;
import lombok.Getter;

import java.util.List;

@Getter
public enum AppSettingKey {
    // @formatter:off
    // ADMIN only (public settings)
    OIDC_PROVIDER_DETAILS               ("oidc_provider_details",                true,  true,  List.of(PermissionType.ADMIN)),
    OIDC_ENABLED                        ("oidc_enabled",                         false, true,  List.of(PermissionType.ADMIN)),
    OIDC_AUTO_PROVISION_DETAILS         ("oidc_auto_provision_details",          true,  false, List.of(PermissionType.ADMIN)),
    KOBO_SETTINGS                       ("kobo_settings",                        true,  false, List.of(PermissionType.ADMIN)),
    OPDS_SERVER_ENABLED                 ("opds_server_enabled",                  false, false, List.of(PermissionType.ADMIN)),
    KOMGA_API_ENABLED                     ("komga_api_enabled",                  false, false, List.of(PermissionType.ADMIN)),
    KOMGA_GROUP_UNKNOWN                 ("komga_group_unknown",                  false, false, List.of(PermissionType.ADMIN)),

    // ADMIN + MANAGE_METADATA_CONFIG
    QUICK_BOOK_MATCH                    ("quick_book_match",                     true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    LIBRARY_METADATA_REFRESH_OPTIONS    ("library_metadata_refresh_options",     true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_PROVIDER_SETTINGS          ("metadata_provider_settings",           true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_MATCH_WEIGHTS              ("metadata_match_weights",               true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_PERSISTENCE_SETTINGS       ("metadata_persistence_settings_v2",     true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_PUBLIC_REVIEWS_SETTINGS    ("metadata_public_reviews_settings",     true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    UPLOAD_FILE_PATTERN                 ("upload_file_pattern",                  false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    MOVE_FILE_PATTERN                   ("move_file_pattern",                    false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_DOWNLOAD_ON_BOOKDROP       ("metadata_download_on_bookdrop",        false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),
    METADATA_PROVIDER_SPECIFIC_FIELDS   ("metadata_provider_specific_fields",    true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_METADATA_CONFIG)),

    // ADMIN + MANAGE_GLOBAL_PREFERENCES
    COVER_CROPPING_SETTINGS             ("cover_cropping_settings",              true,  false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),
    AUTO_BOOK_SEARCH                    ("auto_book_search",                     false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),
    SIMILAR_BOOK_RECOMMENDATION         ("similar_book_recommendation",          false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),
    PDF_CACHE_SIZE_IN_MB                ("pdf_cache_size_in_mb",                 false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),
    MAX_FILE_UPLOAD_SIZE_IN_MB          ("max_file_upload_size_in_mb",           false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),
    TELEMETRY_ENABLED                   ("telemetryEnabled",                     false, false, List.of(PermissionType.ADMIN, PermissionType.MANAGE_GLOBAL_PREFERENCES)),

    // No specific permissions required
    SIDEBAR_LIBRARY_SORTING             ("sidebar_library_sorting",              true,  false, List.of()),
    SIDEBAR_SHELF_SORTING               ("sidebar_shelf_sorting",                true,  false, List.of());
    // @formatter:on

    private final String dbKey;
    private final boolean isJson;
    private final boolean isPublic;
    private final List<PermissionType> requiredPermissions;

    AppSettingKey(String dbKey, boolean isJson, boolean isPublic, List<PermissionType> requiredPermissions) {
        this.dbKey = dbKey;
        this.isJson = isJson;
        this.isPublic = isPublic;
        this.requiredPermissions = requiredPermissions;
    }

    @Override
    public String toString() {
        return dbKey;
    }

    public static AppSettingKey fromDbKey(String dbKey) {
        for (AppSettingKey key : values()) {
            if (key.dbKey.equals(dbKey)) {
                return key;
            }
        }
        throw new IllegalArgumentException("Unknown setting key: " + dbKey);
    }
}