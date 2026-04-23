package org.booklore.app.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.app.dto.AppBootstrapResponse;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.service.MenuCountsService;
import org.booklore.service.ShelfService;
import org.booklore.service.VersionService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.library.LibraryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@AllArgsConstructor
@Service
public class AppBootstrapService {

    private final AppSettingService appSettingService;
    private final VersionService versionService;
    private final MenuCountsService menuCountsService;
    private final LibraryService libraryService;
    private final ShelfService shelfService;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public AppBootstrapResponse getBootstrapData() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        AppBootstrapResponse.AppBootstrapResponseBuilder builder = AppBootstrapResponse.builder()
                .publicSettings(appSettingService.getPublicSettings())
                .version(versionService.getVersionInfo());

        if (user != null && user.getId() != null && user.getId() != -1L) {
            try {
                builder.user(user)
                        .menuCounts(menuCountsService.getMenuCounts(user))
                        .libraries(libraryService.getLibraries(user))
                        .shelves(shelfService.getShelves(user));
            } catch (Exception e) {
                log.error("[Bootstrap] Failed to fetch complete bootstrap data for user {}: {}", user.getUsername(), e.getMessage(), e);
                // Proceed with partial data if possible
                builder.user(user);
            }
        }

        return builder.build();
    }
}
