package org.booklore.model.dto.request;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String name;
    private String email;
    private String locale;
    private String theme;
    private String themeAccent;
    private Boolean themeSyncEnabled;
    private String uiFont;
}
