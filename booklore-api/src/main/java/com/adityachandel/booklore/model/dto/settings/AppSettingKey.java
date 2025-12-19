package com.adityachandel.booklore.model.dto.settings;

import lombok.Getter;

@Getter
public enum AppSettingKey {
    OIDC_PROVIDER_DETAILS("oidc_provider_details", true, true),

    QUICK_BOOK_MATCH("quick_book_match", true, false),
    LIBRARY_METADATA_REFRESH_OPTIONS("library_metadata_refresh_options", true, false),
    OIDC_AUTO_PROVISION_DETAILS("oidc_auto_provision_details", true, false),
    SIDEBAR_LIBRARY_SORTING("sidebar_library_sorting", true, false),
    SIDEBAR_SHELF_SORTING("sidebar_shelf_sorting", true, false),
    METADATA_PROVIDER_SETTINGS("metadata_provider_settings", true, false),
    METADATA_MATCH_WEIGHTS("metadata_match_weights", true, false),
    METADATA_PERSISTENCE_SETTINGS("metadata_persistence_settings", true, false),
    METADATA_PUBLIC_REVIEWS_SETTINGS("metadata_public_reviews_settings", true, false),
    KOBO_SETTINGS("kobo_settings", true, false),
    COVER_CROPPING_SETTINGS("cover_cropping_settings", true, false),

    AUTO_BOOK_SEARCH("auto_book_search", false, false),
    COVER_IMAGE_RESOLUTION("cover_image_resolution", false, false),
    SIMILAR_BOOK_RECOMMENDATION("similar_book_recommendation", false, false),
    UPLOAD_FILE_PATTERN("upload_file_pattern", false, false),
    MOVE_FILE_PATTERN("move_file_pattern", false, false),
    OPDS_SERVER_ENABLED("opds_server_enabled", false, false),
    OIDC_ENABLED("oidc_enabled", false, true),
    CBX_CACHE_SIZE_IN_MB("cbx_cache_size_in_mb", false, false),
    PDF_CACHE_SIZE_IN_MB("pdf_cache_size_in_mb", false, false),
    BOOK_DELETION_ENABLED("book_deletion_enabled", false, false),
    METADATA_DOWNLOAD_ON_BOOKDROP("metadata_download_on_bookdrop", false, false),
    MAX_FILE_UPLOAD_SIZE_IN_MB("max_file_upload_size_in_mb", false, false);

    private final String dbKey;
    private final boolean isJson;
    private final boolean isPublic;

    AppSettingKey(String dbKey, boolean isJson, boolean isPublic) {
        this.dbKey = dbKey;
        this.isJson = isJson;
        this.isPublic = isPublic;
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