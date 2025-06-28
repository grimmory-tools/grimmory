package com.adityachandel.booklore.model.dto.settings;

import lombok.Getter;

@Getter
public enum UserSettingKey {
    PER_BOOK_SETTING("perBookSetting", true),
    PDF_READER_SETTING("pdfReaderSetting", true),
    NEW_PDF_READER_SETTING("newPdfReaderSetting", true),
    EPUB_READER_SETTING("epubReaderSetting", true),
    CBX_READER_SETTING("cbxReaderSetting", true),
    SIDEBAR_LIBRARY_SORTING("sidebarLibrarySorting", true),
    SIDEBAR_SHELF_SORTING("sidebarShelfSorting", true),
    ENTITY_VIEW_PREFERENCES("entityViewPreferences", true),
    FILTER_SORTING_MODE("filterSortingMode", false),
    METADATA_CENTER_VIEW_MODE("metadataCenterViewMode", false);

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