package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.VersionInfo;
import org.booklore.model.dto.settings.PublicAppSetting;
import org.booklore.model.dto.response.MenuCountsResponse;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppBootstrapResponse {
    private BookLoreUser user;
    private PublicAppSetting publicSettings;
    private VersionInfo version;
    private MenuCountsResponse menuCounts;
    private List<Library> libraries;
    private List<Shelf> shelves;
}
