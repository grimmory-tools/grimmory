package org.booklore.model.dto.settings;

import lombok.Getter;

@Getter
public enum UserSettingKey {
    PER_BOOK_SETTING("perBookSetting", true),
    PDF_READER_SETTING("pdfReaderSetting", true),
    NEW_PDF_READER_SETTING("newPdfReaderSetting", true),
    EPUB_READER_SETTING("epubReaderSetting", true),
    EBOOK_READER_SETTING("ebookReaderSetting", true),
    CBX_READER_SETTING("cbxReaderSetting", true),
    SIDEBAR_LIBRARY_SORTING("sidebarLibrarySorting", true),
    SIDEBAR_SHELF_SORTING("sidebarShelfSorting", true),
    SIDEBAR_MAGIC_SHELF_SORTING("sidebarMagicShelfSorting", true),
    ENTITY_VIEW_PREFERENCES("entityViewPreferences", true),
    TABLE_COLUMN_PREFERENCE("tableColumnPreference", true),
    DASHBOARD_CONFIG("dashboardConfig", true),
    FILTER_MODE("filterMode", false),
    FILTER_SORTING_MODE("filterSortingMode", false),
    METADATA_CENTER_VIEW_MODE("metadataCenterViewMode", false),
    ENABLE_SERIES_VIEW("enableSeriesView", false),
    HARDCOVER_API_KEY("hardcoverApiKey", false),
    HARDCOVER_SYNC_ENABLED("hardcoverSyncEnabled", false),
    AUTO_SAVE_METADATA("autoSaveMetadata", false),
    VISIBLE_FILTERS("visibleFilters", true),
    VISIBLE_SORT_FIELDS("visibleSortFields", true);


    private final String dbKey;
    private final boolean isJson;

    UserSettingKey(String dbKey, boolean isJson) {
        this.dbKey = dbKey;
        this.isJson = isJson;
    }

    @Override
    public String toString() {
        return dbKey;
    }

    public static UserSettingKey fromDbKey(String dbKey) {
        for (UserSettingKey key : values()) {
            if (key.dbKey.equals(dbKey)) {
                return key;
            }
        }
        throw new IllegalArgumentException("Unknown setting key: " + dbKey);
    }
}
