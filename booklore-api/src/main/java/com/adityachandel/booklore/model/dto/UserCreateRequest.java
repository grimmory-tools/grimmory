package com.adityachandel.booklore.model.dto;

import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {
    private String username;
    private String password;
    private String name;
    private String email;

    private boolean permissionUpload;
    private boolean permissionDownload;
    private boolean permissionEditMetadata;
    private boolean permissionManipulateLibrary;
    private boolean permissionEmailBook;
    private boolean permissionDeleteBook;
    private boolean permissionAccessOpds;
    private boolean permissionSyncKoreader;
    private boolean permissionSyncKobo;
    private boolean permissionAdmin;

    private Set<Long> selectedLibraries;
}