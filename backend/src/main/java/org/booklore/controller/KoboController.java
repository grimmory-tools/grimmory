package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.kobo.*;
import org.booklore.service.ShelfService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.book.BookService;
import org.booklore.service.kobo.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api/kobo/{token}")
@Tag(name = "Kobo Integration", description = "Endpoints for Kobo device and library integration")
public class KoboController {

    private String token;
    private final AppSettingService appSettingService;
    private final KoboServerProxy koboServerProxy;
    private final KoboInitializationService koboInitializationService;
    private final BookService bookService;
    private final KoboReadingStateService koboReadingStateService;
    private final KoboEntitlementService koboEntitlementService;
    private final KoboDeviceAuthService koboDeviceAuthService;
    private final KoboLibrarySyncService koboLibrarySyncService;
    private final KoboThumbnailService koboThumbnailService;
    private final ShelfService shelfService;
    private final BookDownloadService bookDownloadService;

    private boolean isForwardingToKoboStore() {
        return appSettingService.getAppSettings().getKoboSettings().isForwardToKoboStore();
    }

    @ModelAttribute
    public void captureToken(@PathVariable("token") String token) {
        this.token = token;
    }

    @Operation(summary = "Initialize Kobo resources", description = "Initialize Kobo resources for the device.")
    @ApiResponse(responseCode = "200", description = "Initialization successful")
    @GetMapping("/v1/initialization")
    public ResponseEntity<KoboResources> initialization() throws JacksonException {
        return koboInitializationService.initialize(token);
    }

    @Operation(summary = "Sync Kobo library", description = "Sync the user's Kobo library.")
    @ApiResponse(responseCode = "200", description = "Library synced successfully")
    @GetMapping("/v1/library/sync")
    public ResponseEntity<List<Entitlement>> syncLibrary(@AuthenticationPrincipal BookLoreUser user) {
        return koboLibrarySyncService.syncLibrary(user, token);
    }

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a local book.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thumbnail returned successfully"),
            @ApiResponse(responseCode = "307", description = "Thumbnail is at another location"),
    })

    @GetMapping(
            value = {
                    "v1/books/{imageId}/{version}/thumbnail/{width}/{height}/{quality}/{isGreyscale}/image.jpg",
                    "v1/books/{imageId}/{version}/thumbnail/{width}/{height}/{isGreyscale}/image.jpg",
                    "v1/books/{imageId}/thumbnail/{width}/{height}/{quality}/{isGreyscale}/image.jpg",
                    "v1/books/{imageId}/thumbnail/{width}/{height}/{isGreyscale}/image.jpg",
            },
            produces = MediaType.IMAGE_JPEG_VALUE
    )
    public ResponseEntity<Resource> getThumbnail(
            @Parameter(description = "Book ID") @PathVariable String imageId,
            @Parameter(description = "Width of the thumbnail") @PathVariable int width,
            @Parameter(description = "Height of the thumbnail") @PathVariable int height,
            @Parameter(description = "Is greyscale") @PathVariable boolean isGreyscale
    ) {
        if (imageId.startsWith("BL-")) {
            return koboThumbnailService.getThumbnail(imageId);
        }

        if (isForwardingToKoboStore()) {
            return ResponseEntity
                    .status(HttpStatus.TEMPORARY_REDIRECT)
                    .location(koboServerProxy.getKoboCDNCoverUri(imageId, width, height, isGreyscale))
                    .build();
        }

        throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
    }

    @Operation(summary = "Authenticate Kobo device", description = "Authenticate a Kobo device.")
    @ApiResponse(responseCode = "200", description = "Device authenticated successfully")
    @PostMapping("/v1/auth/device")
    public ResponseEntity<?> authenticateDevice(@Parameter(description = "Authentication request body") @RequestBody JsonNode body) {
        if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(body, false);
        }

        return koboDeviceAuthService.authenticateDevice(body);
    }

    @Operation(summary = "Get book metadata", description = "Retrieve metadata for a book in the Kobo library.")
    @ApiResponse(responseCode = "200", description = "Metadata returned successfully")
    @GetMapping("/v1/library/{bookId}/metadata")
    public ResponseEntity<?> getBookMetadata(@Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            KoboBookMetadata metadata = koboEntitlementService.getMetadataForBook(Long.parseLong(bookId), token);

            if (metadata != null) {
                return ResponseEntity.ok(List.of(metadata));
            }
        } else if(isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }

        throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
    }

    @Operation(summary = "Get reading state", description = "Retrieve the reading state for a book.")
    @ApiResponse(responseCode = "200", description = "Reading state returned successfully")
    @GetMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> getState(@Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(new KoboReadingStateList(koboReadingStateService.getReadingState(bookId)));
        } else if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(null, false);
        } else {
            throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
        }
    }

    @Operation(summary = "Update reading state", description = "Update the reading state for a book.")
    @ApiResponse(responseCode = "200", description = "Reading state updated successfully")
    @PutMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> updateState(
            @Parameter(description = "Book ID") @PathVariable String bookId,
            @Parameter(description = "Reading state update body") @RequestBody KoboReadingStateRequest body) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(koboReadingStateService.saveReadingState(body.getReadingStates()));
        } else if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(body, false);
        } else {
            throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
        }
    }

    @Operation(summary = "Get Kobo test analytics", description = "Get test analytics for Kobo.")
    @ApiResponse(responseCode = "200", description = "Test analytics returned successfully")
    @PostMapping("/v1/analytics/gettests")
    public ResponseEntity<?> getTests(@Parameter(description = "Test analytics request body") @RequestBody Object body) {
        return ResponseEntity.ok(KoboTestResponse.builder()
                .result("Success")
                .testKey(RandomStringUtils.secure().nextAlphanumeric(24))
                .build());
    }

    @Operation(summary = "Publish analytics event", description = "Publish an analytics event for Kobo.")
    @ApiResponse(responseCode = "200", description = "Analytics event pushed successfully")
    @PostMapping("/v1/analytics/event")
    public ResponseEntity<?> pushEvent() {
        // Never pass along analytics events.
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "Download Kobo book", description = "Download a book from the Kobo library.")
    @ApiResponse(responseCode = "200", description = "Book downloaded successfully")
    @GetMapping("/v1/books/{bookId}/download")
    public void downloadBook(@Parameter(description = "Book ID") @PathVariable String bookId, HttpServletResponse response) {
        if (!StringUtils.isNumeric(bookId)) {
            throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
        }

        bookDownloadService.downloadKoboBook(Long.parseLong(bookId), response);
    }

    @Operation(summary = "Delete book from Kobo library", description = "Delete a book from the user's Kobo library.")
    @ApiResponse(responseCode = "200", description = "Book deleted successfully")
    @DeleteMapping("/v1/library/{bookId}")
    public ResponseEntity<?> deleteBookFromLibrary(@Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            Shelf userKoboShelf = shelfService.getUserKoboShelf();
            if (userKoboShelf != null) {
                bookService.assignShelvesToBooks(Set.of(Long.valueOf(bookId)), Set.of(), Set.of(userKoboShelf.getId()));
            }
            return ResponseEntity.ok().build();
        } else if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(null, false);
        } else {
            throw ApiError.GENERIC_NOT_FOUND.createException("Not Found");
        }
    }

    @Operation(summary = "Get Kobo Next to Read", description = "Retrieves the next book to read after the specified book, such as with a series.")
    @ApiResponse(responseCode = "200", description = "The next book in a series to read.")
    @PostMapping("/v1/products/{bookId}/nextread")
    public ResponseEntity<?> getNextRead(@Parameter(description = "Book ID") @PathVariable String bookId) {
        if (!StringUtils.isNumeric(bookId)) {
            if (isForwardingToKoboStore()) {
                return koboServerProxy.proxyCurrentRequest();
            }
        }

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get user profile", description = "Get Kobo user configuration.")
    @ApiResponse(responseCode = "200", description = "Retrieved Kobo User configuration")
    @GetMapping("/v1/user/profile")
    public ResponseEntity<?> getUserProfile() {
        if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }

        return ResponseEntity.ok()
                .body(KoboUserProfile.builder().build());

    }

    @Operation(summary = "Get Kobo Deals", description = "Get promotional deals on Kobo entitlements.")
    @ApiResponse(responseCode = "200", description = "Deals Retrieved successfully")
    @GetMapping("/v1/deals")
    public ResponseEntity<?> getDeals() {
        if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }

        return ResponseEntity.ok()
                .body(KoboDeals.builder().build());

    }

    @Operation(summary = "Catch-all for Kobo API", description = "Catch-all endpoint for unhandled Kobo API requests.")
    @ApiResponse(responseCode = "200", description = "Request proxied successfully")
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<JsonNode> catchAll(HttpServletRequest request, @RequestBody(required = false) Object body) {
        if (isForwardingToKoboStore()) {
            return koboServerProxy.proxyCurrentRequest(body, false);
        }

        return ResponseEntity.ok().build();
    }
}
