package org.booklore.service.user;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.SidebarSortOption;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.enums.*;
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
        defaultSettings.put(UserSettingKey.EBOOK_READER_SETTING, this::buildDefaultEbookReaderSetting);
        defaultSettings.put(UserSettingKey.CBX_READER_SETTING, this::buildDefaultCbxReaderSetting);
        defaultSettings.put(UserSettingKey.NEW_PDF_READER_SETTING, this::buildDefaultNewPdfReaderSetting);
        defaultSettings.put(UserSettingKey.SIDEBAR_LIBRARY_SORTING, this::buildDefaultSidebarLibrarySorting);
        defaultSettings.put(UserSettingKey.SIDEBAR_SHELF_SORTING, this::buildDefaultSidebarShelfSorting);
        defaultSettings.put(UserSettingKey.SIDEBAR_MAGIC_SHELF_SORTING, this::buildDefaultSidebarMagicShelfSorting);
        defaultSettings.put(UserSettingKey.ENTITY_VIEW_PREFERENCES, this::buildDefaultEntityViewPreferences);
        defaultSettings.put(UserSettingKey.TABLE_COLUMN_PREFERENCE, () -> null);
        defaultSettings.put(UserSettingKey.FILTER_MODE, () -> "and");
        defaultSettings.put(UserSettingKey.FILTER_SORTING_MODE, () -> "count");
        defaultSettings.put(UserSettingKey.METADATA_CENTER_VIEW_MODE, () -> "route");
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
                .newPdf(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual)
                .build();
    }

    private BookLoreUser.UserSettings.PdfReaderSetting buildDefaultPdfReaderSetting() {
        return BookLoreUser.UserSettings.PdfReaderSetting.builder()
                .pageSpread("off")
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
                .spread("double")
                .build();
    }

    private BookLoreUser.UserSettings.EbookReaderSetting buildDefaultEbookReaderSetting() {
        return BookLoreUser.UserSettings.EbookReaderSetting.builder()
                .fontFamily("serif")
                .fontSize(16)
                .gap(0.05f)
                .hyphenate(false)
                .isDark(false)
                .justify(false)
                .lineHeight(1.5f)
                .maxBlockSize(1440)
                .maxColumnCount(2)
                .maxInlineSize(720)
                .theme("gray")
                .flow("paginated")
                .build();
    }

    private BookLoreUser.UserSettings.CbxReaderSetting buildDefaultCbxReaderSetting() {
        return BookLoreUser.UserSettings.CbxReaderSetting.builder()
                .pageViewMode(CbxPageViewMode.SINGLE_PAGE)
                .pageSpread(CbxPageSpread.ODD)
                .fitMode(CbxPageFitMode.FIT_HEIGHT)
                .scrollMode(CbxPageScrollMode.PAGINATED)
                .backgroundColor(CbxBackgroundColor.GRAY)
                .stripMaxWidthPercent(100)
                .build();
    }

    private BookLoreUser.UserSettings.NewPdfReaderSetting buildDefaultNewPdfReaderSetting() {
        return BookLoreUser.UserSettings.NewPdfReaderSetting.builder()
                .pageViewMode(NewPdfPageViewMode.SINGLE_PAGE)
                .pageSpread(NewPdfPageSpread.ODD)
                .fitMode(NewPdfPageFitMode.FIT_HEIGHT)
                .scrollMode(NewPdfPageScrollMode.PAGINATED)
                .backgroundColor(NewPdfBackgroundColor.WHITE)
                .build();
    }

    private SidebarSortOption buildDefaultSidebarLibrarySorting() {
        return SidebarSortOption.builder().field("id").order("asc").build();
    }

    private SidebarSortOption buildDefaultSidebarShelfSorting() {
        return SidebarSortOption.builder().field("id").order("asc").build();
    }

    private SidebarSortOption buildDefaultSidebarMagicShelfSorting() {
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
                        .overlayBookType(true)
                        .build())
                .overrides(null)
                .build();
    }
}