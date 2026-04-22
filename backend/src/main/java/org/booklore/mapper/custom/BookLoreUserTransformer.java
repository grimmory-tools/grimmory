package org.booklore.mapper.custom;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.SidebarSortOption;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.model.enums.UserPermission;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class BookLoreUserTransformer {

    private final ObjectMapper objectMapper;
    private final LibraryMapper libraryMapper;

    public BookLoreUser toDTO(BookLoreUserEntity userEntity) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        UserPermission.copyFromEntityToDto(userEntity.getPermissions(), permissions);

        BookLoreUser bookLoreUser = new BookLoreUser();
        bookLoreUser.setId(userEntity.getId());
        bookLoreUser.setUsername(userEntity.getUsername());
        bookLoreUser.setName(userEntity.getName());
        bookLoreUser.setEmail(userEntity.getEmail());
        bookLoreUser.setDefaultPassword(userEntity.isDefaultPassword());
        bookLoreUser.setPermissions(permissions);

        BookLoreUser.UserSettings userSettings = new BookLoreUser.UserSettings();

        for (UserSettingEntity settingEntity : userEntity.getSettings()) {
            String key = settingEntity.getSettingKey();
            String value = settingEntity.getSettingValue();

            try {
                UserSettingKey settingKey = UserSettingKey.fromDbKey(key);
                if (settingKey.isJson()) {
                    switch (settingKey) {
                        case PER_BOOK_SETTING -> userSettings.setPerBookSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.PerBookSetting.class));
                        case PDF_READER_SETTING -> userSettings.setPdfReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.PdfReaderSetting.class));
                        case EPUB_READER_SETTING -> userSettings.setEpubReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.EpubReaderSetting.class));
                        case EBOOK_READER_SETTING -> userSettings.setEbookReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.EbookReaderSetting.class));
                        case CBX_READER_SETTING -> userSettings.setCbxReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.CbxReaderSetting.class));
                        case NEW_PDF_READER_SETTING -> userSettings.setNewPdfReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.NewPdfReaderSetting.class));
                        case SIDEBAR_LIBRARY_SORTING -> userSettings.setSidebarLibrarySorting(objectMapper.readValue(value, SidebarSortOption.class));
                        case SIDEBAR_SHELF_SORTING -> userSettings.setSidebarShelfSorting(objectMapper.readValue(value, SidebarSortOption.class));
                        case SIDEBAR_MAGIC_SHELF_SORTING -> userSettings.setSidebarMagicShelfSorting(objectMapper.readValue(value, SidebarSortOption.class));
                        case ENTITY_VIEW_PREFERENCES -> userSettings.setEntityViewPreferences(objectMapper.readValue(value, BookLoreUser.UserSettings.EntityViewPreferences.class));
                        case TABLE_COLUMN_PREFERENCE -> userSettings.setTableColumnPreference(objectMapper.readValue(value, new TypeReference<>() {
                        }));
                        case DASHBOARD_CONFIG -> userSettings.setDashboardConfig(objectMapper.readValue(value, BookLoreUser.UserSettings.DashboardConfig.class));
                        case VISIBLE_FILTERS -> userSettings.setVisibleFilters(objectMapper.readValue(value, new TypeReference<>() {
                        }));
                        case VISIBLE_SORT_FIELDS -> userSettings.setVisibleSortFields(objectMapper.readValue(value, new TypeReference<>() {
                        }));
                    }
                } else {
                    switch (settingKey) {
                        case FILTER_MODE -> userSettings.setFilterMode(value);
                        case FILTER_SORTING_MODE -> userSettings.setFilterSortingMode(value);
                        case METADATA_CENTER_VIEW_MODE -> userSettings.setMetadataCenterViewMode(value);
                        case ENABLE_SERIES_VIEW -> userSettings.setEnableSeriesView(Boolean.parseBoolean(value));
                        case AUTO_SAVE_METADATA -> userSettings.setAutoSaveMetadata(Boolean.parseBoolean(value));
                    }
                }
            } catch (IllegalArgumentException e) {
                log.debug("Unknown setting key encountered: {}", key);
            } catch (Exception e) {
                log.error("Failed to deserialize setting '{}': {}", key, e.getMessage(), e);
            }
        }

        bookLoreUser.setUserSettings(userSettings);
        if (userEntity.getLibraries() != null) {
            bookLoreUser.setAssignedLibraries(
                    userEntity.getLibraries().stream()
                            .map(libraryMapper::toLibrary)
                            .collect(Collectors.toList())
            );
        } else {
            bookLoreUser.setAssignedLibraries(Collections.emptyList());
        }
        bookLoreUser.setProvisioningMethod(userEntity.getProvisioningMethod());
        return bookLoreUser;
    }
}