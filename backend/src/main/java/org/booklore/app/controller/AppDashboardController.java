package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppDashboardResponse;
import org.booklore.app.service.AppBookService;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/dashboard")
@Tag(name = "App Dashboard", description = "Consolidated endpoints for the app dashboard experience")
@Slf4j
public class AppDashboardController {
    private static final int DEFAULT_MAX_ITEMS = 20;

    private final AppBookService mobileBookService;
    private final AuthenticationService authenticationService;

    @Operation(
            summary = "Get consolidated dashboard data",
            description = "Retrieve all data needed for the dashboard scrollers in a single request.",
            operationId = "appGetDashboard"
    )
    @GetMapping
    public ResponseEntity<AppDashboardResponse> getDashboard() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUser.UserSettings.DashboardConfig config = resolveDashboardConfig(user);

        Map<String, List<AppBookSummary>> scrollerData = new HashMap<>();

        for (BookLoreUser.UserSettings.ScrollerConfig scroller : config.getScrollers()) {
            if (!scroller.isEnabled()) {
                log.debug("[Dashboard] Scroller {} ({}) is disabled, skipping", scroller.getId(), scroller.getType());
                continue;
            }

            String type = scroller.getType();
            if (type == null) {
                log.warn("[Dashboard] Scroller {} has null type, skipping", scroller.getId());
                continue;
            }

            log.debug("[Dashboard] Fetching books for scroller {} (type: {}, max: {})", scroller.getId(), type, scroller.getMaxItems());
            List<AppBookSummary> books = switch (type) {
                case "lastRead", "LAST_READ" -> mobileBookService.getContinueReading(scroller.getMaxItems());
                case "lastListened", "LAST_LISTENED" -> mobileBookService.getContinueListening(scroller.getMaxItems());
                case "latestAdded", "LATEST_ADDED", "RECENTLY_ADDED" -> mobileBookService.getRecentlyAdded(scroller.getMaxItems());
                case "recentlyScanned", "RECENTLY_SCANNED" -> mobileBookService.getRecentlyScanned(scroller.getMaxItems());
                case "random", "RANDOM" -> mobileBookService.getRandomBooks(0, scroller.getMaxItems(), null).getContent();
                case "magicShelf", "MAGIC_SHELF" -> {
                    if (scroller.getMagicShelfId() != null) {
                        yield mobileBookService.getBooksByMagicShelf(scroller.getMagicShelfId(), 0, scroller.getMaxItems()).getContent();
                    }
                    log.warn("[Dashboard] Magic shelf scroller {} missing magicShelfId", scroller.getId());
                    yield List.of();
                }
                default -> {
                    log.warn("[Dashboard] Unknown scroller type: {}", type);
                    yield List.of();
                }
            };

            log.debug("[Dashboard] Scroller {} (type: {}) returned {} books", scroller.getId(), type, books.size());
            scrollerData.put(scroller.getId(), books);
        }

        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=60")
                .body(new AppDashboardResponse(scrollerData));
    }

    private BookLoreUser.UserSettings.DashboardConfig resolveDashboardConfig(BookLoreUser user) {
        if (user == null || user.getUserSettings() == null) {
            return buildDefaultDashboardConfig();
        }

        BookLoreUser.UserSettings.DashboardConfig config = user.getUserSettings().getDashboardConfig();
        if (config == null || config.getScrollers() == null || config.getScrollers().isEmpty()) {
            return buildDefaultDashboardConfig();
        }
        return config;
    }

    private BookLoreUser.UserSettings.DashboardConfig buildDefaultDashboardConfig() {
        List<BookLoreUser.UserSettings.ScrollerConfig> scrollers = new ArrayList<>();
        scrollers.add(defaultScroller("1", "lastListened", 1));
        scrollers.add(defaultScroller("2", "lastRead", 2));
        scrollers.add(defaultScroller("3", "latestAdded", 3));
        scrollers.add(defaultScroller("4", "random", 4));
        return BookLoreUser.UserSettings.DashboardConfig.builder().scrollers(scrollers).build();
    }

    private BookLoreUser.UserSettings.ScrollerConfig defaultScroller(String id, String type, int order) {
        return BookLoreUser.UserSettings.ScrollerConfig.builder()
                .id(id)
                .type(type)
                .enabled(true)
                .order(order)
                .maxItems(DEFAULT_MAX_ITEMS)
                .build();
    }
}
