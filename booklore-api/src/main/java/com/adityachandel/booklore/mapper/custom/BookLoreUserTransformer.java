package com.adityachandel.booklore.mapper.custom;

import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.SidebarSortOption;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserSettingEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class BookLoreUserTransformer {

    private final ObjectMapper objectMapper;
    private final LibraryMapper libraryMapper;

    public BookLoreUser toDTO(BookLoreUserEntity userEntity) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(userEntity.getPermissions().isPermissionAdmin());
        permissions.setCanUpload(userEntity.getPermissions().isPermissionUpload());
        permissions.setCanDownload(userEntity.getPermissions().isPermissionDownload());
        permissions.setCanEditMetadata(userEntity.getPermissions().isPermissionEditMetadata());
        permissions.setCanEmailBook(userEntity.getPermissions().isPermissionEmailBook());
        permissions.setCanManipulateLibrary(userEntity.getPermissions().isPermissionManipulateLibrary());

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
                        case CBX_READER_SETTING -> userSettings.setCbxReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.CbxReaderSetting.class));
                        case NEW_PDF_READER_SETTING -> userSettings.setNewPdfReaderSetting(objectMapper.readValue(value, BookLoreUser.UserSettings.NewPdfReaderSetting.class));
                        case SIDEBAR_LIBRARY_SORTING -> userSettings.setSidebarLibrarySorting(objectMapper.readValue(value, SidebarSortOption.class));
                        case SIDEBAR_SHELF_SORTING -> userSettings.setSidebarShelfSorting(objectMapper.readValue(value, SidebarSortOption.class));
                        case ENTITY_VIEW_PREFERENCES -> userSettings.setEntityViewPreferences(objectMapper.readValue(value, BookLoreUser.UserSettings.EntityViewPreferences.class));
                    }
                } else {
                    if (settingKey == UserSettingKey.FILTER_SORTING_MODE) {
                        userSettings.setFilterSortingMode(value);
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Unknown setting key encountered: {}", key);
            } catch (Exception e) {
                log.error("Failed to deserialize setting '{}': {}", key, e.getMessage(), e);
            }
        }

        bookLoreUser.setUserSettings(userSettings);
        bookLoreUser.setAssignedLibraries(userEntity.getLibraries().stream().map(libraryMapper::toLibrary).collect(Collectors.toList()));
        bookLoreUser.setProvisioningMethod(userEntity.getProvisioningMethod());
        return bookLoreUser;
    }
}