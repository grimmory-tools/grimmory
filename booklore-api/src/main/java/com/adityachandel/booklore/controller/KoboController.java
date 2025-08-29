package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.kobo.KoboAuthentication;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingStateWrapper;
import com.adityachandel.booklore.model.dto.kobo.KoboResources;
import com.adityachandel.booklore.model.dto.kobo.KoboTestResponse;
import com.adityachandel.booklore.service.*;
import com.adityachandel.booklore.service.kobo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api/kobo/{token}")
public class KoboController {

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

    @GetMapping("/v1/initialization")
    public ResponseEntity<KoboResources> initialization() throws JsonProcessingException {
        return koboInitializationService.initialize(token);
    }

    @GetMapping("/v1/library/sync")
    public ResponseEntity<?> syncLibrary(@AuthenticationPrincipal BookLoreUser user) {
        return koboLibrarySyncService.syncLibrary(user, token);
    }

    @GetMapping("/v1/books/{imageId}/thumbnail/{width}/{height}/false/image.jpg")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable String imageId,
            @PathVariable int width,
            @PathVariable int height) {

        if (StringUtils.isNumeric(imageId)) {
            return koboThumbnailService.getThumbnail(Long.valueOf(imageId));
        } else {
            String cdnUrl = String.format("https://cdn.kobo.com/book-images/%s/%d/%d/image.jpg", imageId, width, height);
            return koboServerProxy.proxyExternalUrl(cdnUrl);
        }
    }

    @GetMapping("/v1/books/{bookId}/thumbnail/{width}/{height}/{quality}/{isGreyscale}/image.jpg")
    public ResponseEntity<Resource> getGreyThumbnail(
            @PathVariable String bookId,
            @PathVariable int width,
            @PathVariable int height,
            @PathVariable int quality,
            @PathVariable boolean isGreyscale) {

        if (StringUtils.isNumeric(bookId)) {
            return koboThumbnailService.getThumbnail(Long.valueOf(bookId));
        } else {
            String cdnUrl = String.format("https://cdn.kobo.com/book-images/%s/%d/%d/%d/%b/image.jpg", bookId, width, height, quality, isGreyscale);
            return koboServerProxy.proxyExternalUrl(cdnUrl);
        }
    }

    @PostMapping("/v1/auth/device")
    public ResponseEntity<KoboAuthentication> authenticateDevice(@RequestBody JsonNode body) {
        return koboDeviceAuthService.authenticateDevice(body);
    }

    @GetMapping("/v1/library/{bookId}/metadata")
    public ResponseEntity<?> getBookMetadata(@PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(List.of(koboEntitlementService.getMetadataForBook(Long.parseLong(bookId), token)));
        } else {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @GetMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> getState(@PathVariable String bookId) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(koboReadingStateService.getReadingState(bookId));
        } else {
            return koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @PutMapping("/v1/library/{bookId}/state")
    public ResponseEntity<?> updateState(@PathVariable String bookId, @RequestBody KoboReadingStateWrapper body) {
        if (StringUtils.isNumeric(bookId)) {
            return ResponseEntity.ok(koboReadingStateService.saveReadingState(body.getReadingStates()));
        } else {
            return koboServerProxy.proxyCurrentRequest(body, false);
        }
    }

    @PostMapping("/v1/analytics/gettests")
    public ResponseEntity<?> getTests(@RequestBody Object body) {
        return ResponseEntity.ok(KoboTestResponse.builder()
                .result("Success")
                .testKey(RandomStringUtils.secure().nextAlphanumeric(24))
                .build());
    }

    @GetMapping("/v1/books/{bookId}/download")
    public void downloadBook(@PathVariable String bookId, HttpServletResponse response) throws IOException {
        if (StringUtils.isNumeric(bookId)) {
            bookDownloadService.downloadKoboBook(Long.parseLong(bookId), response);
        } else {
            koboServerProxy.proxyCurrentRequest(null, false);
        }
    }

    @DeleteMapping("/v1/library/{bookId}")
    public ResponseEntity<?> deleteBookFromLibrary(@PathVariable String bookId) {
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

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<JsonNode> catchAll(HttpServletRequest request, @RequestBody(required = false) Object body) {
        String path = request.getRequestURI();
        if (path.contains("/v1/analytics/event")) {
            return ResponseEntity.ok().build();
        }
        if (path.matches(".*/v1/products/\\d+/nextread.*")) {
            return ResponseEntity.ok().build();
        }
        return koboServerProxy.proxyCurrentRequest(body, false);
    }
}