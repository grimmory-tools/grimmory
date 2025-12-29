package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.kobo.KoboAuthentication;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingStateWrapper;
import com.adityachandel.booklore.model.dto.kobo.KoboResources;
import com.adityachandel.booklore.model.dto.kobo.KoboTestResponse;
import com.adityachandel.booklore.service.*;
import com.adityachandel.booklore.service.book.BookDownloadService;
import com.adityachandel.booklore.service.book.BookService;
import com.adityachandel.booklore.service.kobo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api/kobo/{token}")
@Tag(name = "Kobo Integration", description = "Endpoints for Kobo device and library integration")
public class KoboController {

    private static final Pattern KOBO_V1_PRODUCTS_NEXTREAD_PATTERN = Pattern.compile(".*/v1/products/\\d+/nextread.*");
    private String token;
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

    @ModelAttribute
    public void captureToken(@PathVariable("token") String token) {
        this.token = token;
    }

    @Operation(summary = "Initialize Kobo resources", description = "Initialize Kobo resources for the device.")
    @ApiResponse(responseCode = "200", description = "Initialization successful")
    @GetMapping("/v1/initialization")
    public ResponseEntity<KoboResources> initialization() throws JsonProcessingException {
        return koboInitializationService.initialize(token);
    }

    @Operation(summary = "Sync Kobo library", description = "Sync the user's Kobo library.")
    @ApiResponse(responseCode = "200", description = "Library synced successfully")
    @GetMapping("/v1/library/sync")
    public ResponseEntity<?> syncLibrary(@AuthenticationPrincipal BookLoreUser user) {
        return koboLibrarySyncService.syncLibrary(user, token);
    }

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a book.")
    @ApiResponse(responseCode = "200", description = "Thumbnail returned successfully")
    @GetMapping("/v1/books/{imageId}/thumbnail/{width}/{height}/false/image.jpg")
    public ResponseEntity<Resource> getThumbnail(
            @Parameter(description = "Image ID") @PathVariable String imageId,
            @Parameter(description = "Width of the thumbnail") @PathVariable int width,
            @Parameter(description = "Height of the thumbnail") @PathVariable int height) {

        if (StringUtils.isNumeric(imageId)) {
            return koboThumbnailService.getThumbnail(Long.valueOf(imageId));
        } else {
            String cdnUrl = String.format("https://cdn.kobo.com/book-images/%s/%d/%d/image.jpg", imageId, width, height);
            return koboServerProxy.proxyExternalUrl(cdnUrl);
        }
    }

    @Operation(summary = "Get greyscale book thumbnail", description = "Retrieve a greyscale thumbnail image for a book.")
    @ApiResponse(responseCode = "200", description = "Greyscale thumbnail returned successfully")
    @GetMapping("/v1/books/{bookId}/thumbnail/{width}/{height}/{quality}/{isGreyscale}/image.jpg")
    public ResponseEntity<Resource> getGreyThumbnail(
            @Parameter(description = "Book ID") @PathVariable String bookId,
            @Parameter(description = "Width of the thumbnail") @PathVariable int width,
            @Parameter(description = "Height of the thumbnail") @PathVariable int height,
            @Parameter(description = "Quality of the thumbnail") @PathVariable int quality,
            @Parameter(description = "Is greyscale") @PathVariable boolean isGreyscale) {

        if (StringUtils.isNumeric(bookId)) {
            return koboThumbnailService.getThumbnail(Long.valueOf(bookId));
        } else {
            String cdnUrl = String.format("https://cdn.kobo.com/book-images/%s/%d/%d/%d/%b/image.jpg", bookId, width, height, quality, isGreyscale);
            return koboServerProxy.proxyExternalUrl(cdnUrl);
        }
    }

    @Operation(summary = "Authenticate Kobo device", description = "Authenticate a Kobo device.")
    @ApiResponse(responseCode = "200", description = "Device authenticated successfully")
    @PostMapping("/v1/auth/device")
    public ResponseEntity<KoboAuthentication> authenticateDevice(
            @Parameter(description = "Authentication request body") @RequestBody JsonNode body) {
        return koboDeviceAuthService.authenticateDevice(body);
    }

    @Operation(summary = "Get book metadata", description = "Retrieve metadata for a book in the Kobo library.")
    @ApiResponse(responseCode = "200", description = "Metadata returned successfully")
    @GetMapping("/v1/library/{bookId}/metadata")
    public ResponseEntity<?> getBookMetadata(
            @Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(List.of(koboEntitlementService.getMetadataForBook(Long.parseLong(bookId), token)));
        } else {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @Operation(summary = "Get reading state", description = "Retrieve the reading state for a book.")
    @ApiResponse(responseCode = "200", description = "Reading state returned successfully")
    @GetMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> getState(
            @Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(koboReadingStateService.getReadingState(bookId));
        } else {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @Operation(summary = "Update reading state", description = "Update the reading state for a book.")
    @ApiResponse(responseCode = "200", description = "Reading state updated successfully")
    @PutMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> updateState(
            @Parameter(description = "Book ID") @PathVariable String bookId,
            @Parameter(description = "Reading state update body") @RequestBody KoboReadingStateWrapper body) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(koboReadingStateService.saveReadingState(body.getReadingStates()));
        } else {
            return koboServerProxy.proxyCurrentRequest(body, false);
        }
    }

    @Operation(summary = "Get Kobo test analytics", description = "Get test analytics for Kobo.")
    @ApiResponse(responseCode = "200", description = "Test analytics returned successfully")
    @PostMapping("/v1/analytics/gettests")
    public ResponseEntity<?> getTests(
            @Parameter(description = "Test analytics request body") @RequestBody Object body) {
        return ResponseEntity.ok(KoboTestResponse.builder()
                .result("Success")
                .testKey(RandomStringUtils.secure().nextAlphanumeric(24))
                .build());
    }

    @Operation(summary = "Download Kobo book", description = "Download a book from the Kobo library.")
    @ApiResponse(responseCode = "200", description = "Book downloaded successfully")
    @GetMapping("/v1/books/{bookId}/download")
    public void downloadBook(
            @Parameter(description = "Book ID") @PathVariable String bookId, HttpServletResponse response) {
        if (StringUtils.isNumeric(bookId)) {
            bookDownloadService.downloadKoboBook(Long.parseLong(bookId), response);
        } else {
            koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @Operation(summary = "Delete book from Kobo library", description = "Delete a book from the user's Kobo library.")
    @ApiResponse(responseCode = "200", description = "Book deleted successfully")
    @DeleteMapping("/v1/library/{bookId}")
    public ResponseEntity<?> deleteBookFromLibrary(
            @Parameter(description = "Book ID") @PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            Shelf userKoboShelf = shelfService.getUserKoboShelf();
            if (userKoboShelf != null) {
                bookService.assignShelvesToBooks(Set.of(Long.valueOf(bookId)), Set.of(), Set.of(userKoboShelf.getId()));
            }
            return ResponseEntity.ok().build();
        } else {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @Operation(summary = "Catch-all for Kobo API", description = "Catch-all endpoint for unhandled Kobo API requests.")
    @ApiResponse(responseCode = "200", description = "Request proxied successfully")
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<JsonNode> catchAll(HttpServletRequest request, @RequestBody(required = false) Object body) {
        String path = request.getRequestURI();
        if (path.contains("/v1/analytics/event")) {
            return ResponseEntity.ok().build();
        }
        if (KOBO_V1_PRODUCTS_NEXTREAD_PATTERN.matcher(path).matches()) {
            return ResponseEntity.ok().build();
        }
        return koboServerProxy.proxyCurrentRequest(body, false);
    }
}