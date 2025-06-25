package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.SidebarSortOption;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.enums.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DefaultUserSettingsProvider {

    private final Map<UserSettingKey, Supplier<Object>> defaultSettings = new EnumMap<>(UserSettingKey.class);

    @PostConstruct
    public void init() {
        defaultSettings.put(UserSettingKey.PER_BOOK_SETTING, this::buildDefaultPerBookSetting);
        defaultSettings.put(UserSettingKey.PDF_READER_SETTING, this::buildDefaultPdfReaderSetting);
        defaultSettings.put(UserSettingKey.EPUB_READER_SETTING, this::buildDefaultEpubReaderSetting);
        defaultSettings.put(UserSettingKey.CBX_READER_SETTING, this::buildDefaultCbxReaderSetting);
        defaultSettings.put(UserSettingKey.NEW_PDF_READER_SETTING, this::buildDefaultNewPdfReaderSetting);
        defaultSettings.put(UserSettingKey.SIDEBAR_LIBRARY_SORTING, this::buildDefaultSidebarLibrarySorting);
        defaultSettings.put(UserSettingKey.SIDEBAR_SHELF_SORTING, this::buildDefaultSidebarShelfSorting);
        defaultSettings.put(UserSettingKey.ENTITY_VIEW_PREFERENCES, this::buildDefaultEntityViewPreferences);
        defaultSettings.put(UserSettingKey.FILTER_SORTING_MODE, () -> "count");
    }

    public Set<UserSettingKey> getAllKeys() {
        return defaultSettings.keySet();
    }

    public Object getDefaultValue(UserSettingKey key) {
        Supplier<Object> supplier = defaultSettings.get(key);
        if (supplier == null) {
            throw new IllegalArgumentException("No default value defined for key: " + key);
        }
        return supplier.get();
    }

    private BookLoreUser.UserSettings.PerBookSetting buildDefaultPerBookSetting() {
        return BookLoreUser.UserSettings.PerBookSetting.builder()
                .epub(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .pdf(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .cbx(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .build();
    }

    private BookLoreUser.UserSettings.PdfReaderSetting buildDefaultPdfReaderSetting() {
        return BookLoreUser.UserSettings.PdfReaderSetting.builder()
                .pageSpread("odd")
                .pageZoom("page-fit")
                .build();
    }

    private BookLoreUser.UserSettings.EpubReaderSetting buildDefaultEpubReaderSetting() {
        return BookLoreUser.UserSettings.EpubReaderSetting.builder()
                .theme("white")
                .font(null)
                .fontSize(100)
                .letterSpacing(null)
                .lineHeight(null)
                .flow("paginated")
                .build();
    }

    private BookLoreUser.UserSettings.CbxReaderSetting buildDefaultCbxReaderSetting() {
        return BookLoreUser.UserSettings.CbxReaderSetting.builder()
                .pageViewMode(CbxPageViewMode.SINGLE_PAGE)
                .pageSpread(CbxPageSpread.ODD)
                .build();
    }

    private BookLoreUser.UserSettings.NewPdfReaderSetting buildDefaultNewPdfReaderSetting() {
        return BookLoreUser.UserSettings.NewPdfReaderSetting.builder()
                .pageViewMode(NewPdfPageViewMode.SINGLE_PAGE)
                .pageSpread(NewPdfPageSpread.ODD)
                .build();
    }

    private SidebarSortOption buildDefaultSidebarLibrarySorting() {
        return SidebarSortOption.builder().field("id").order("asc").build();
    }

    private SidebarSortOption buildDefaultSidebarShelfSorting() {
        return SidebarSortOption.builder().field("id").order("asc").build();
    }

    private BookLoreUser.UserSettings.EntityViewPreferences buildDefaultEntityViewPreferences() {
        return BookLoreUser.UserSettings.EntityViewPreferences.builder()
                .global(BookLoreUser.UserSettings.GlobalPreferences.builder()
                        .sortKey("title")
                        .sortDir("ASC")
                        .view("GRID")
                        .coverSize(1.0F)
                        .seriesCollapsed(false)
                        .build())
                .overrides(null)
                .build();
    }
}