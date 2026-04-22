package org.booklore.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserInfo {
    private boolean isAdmin;
    private boolean canUpload;
    private boolean canDownload;
    private boolean canAccessBookdrop;
    private int maxFileUploadSizeMb;
}
