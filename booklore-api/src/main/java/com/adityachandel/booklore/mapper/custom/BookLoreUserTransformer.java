package com.adityachandel.booklore.mapper.custom;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;

public class BookLoreUserTransformer {

    public static BookLoreUser toDTO(BookLoreUserEntity userEntity) {
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
        bookLoreUser.setBookPreferences(userEntity.getBookPreferences());

        return bookLoreUser;
    }
}
