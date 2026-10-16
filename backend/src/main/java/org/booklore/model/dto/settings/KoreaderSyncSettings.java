package org.booklore.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class KoreaderSyncSettings {
    public static final String DEFAULT_SHELF_NAME = "KOReader";

    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean externalServerEnabled = false;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private String externalServerUrl = "";
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private String shelfName = DEFAULT_SHELF_NAME;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean usersCanEditLogin = false;

    public String effectiveExternalServerUrl() {
        if (!externalServerEnabled || externalServerUrl == null || externalServerUrl.isBlank()) {
            return null;
        }
        return externalServerUrl.trim();
    }

    public String effectiveShelfName() {
        if (shelfName == null || shelfName.isBlank()) {
            return DEFAULT_SHELF_NAME;
        }
        return shelfName.trim();
    }
}
