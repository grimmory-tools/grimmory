package org.grimmory.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.app.dto.AppBookSummary;
import org.grimmory.app.dto.AppMagicShelfSummary;
import org.grimmory.app.dto.AppPageResponse;
import org.grimmory.app.dto.AppShelfSummary;
import org.grimmory.app.mapper.AppBookMapper;
import org.grimmory.app.service.AppBookService;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.entity.MagicShelfEntity;
import org.grimmory.model.entity.ShelfEntity;
import org.grimmory.repository.MagicShelfRepository;
import org.grimmory.repository.ShelfRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/shelves")
@Tag(name = "App Shelves", description = "Endpoints for browsing shelves and magic shelves in the app experience")
public class AppShelfController {

    private final AuthenticationService authenticationService;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final AppBookMapper mobileBookMapper;
    private final AppBookService mobileBookService;

    @Operation(
            summary = "List app shelves",
            description = "Retrieve all regular shelves visible to the current app user.",
            operationId = "appGetShelves"
    )
    @GetMapping
    public ResponseEntity<List<AppShelfSummary>> getShelves() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        List<ShelfEntity> shelves = shelfRepository.findByUserIdOrPublicShelfTrue(userId);

        List<AppShelfSummary> summaries = shelves.stream()
                .map(mobileBookMapper::toShelfSummaryFromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    @Operation(
            summary = "List app magic shelves",
            description = "Retrieve all magic shelves visible to the current app user.",
            operationId = "appGetMagicShelves"
    )
    @GetMapping("/magic")
    public ResponseEntity<List<AppMagicShelfSummary>> getMagicShelves() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        // Get user's own magic shelves
        List<MagicShelfEntity> userShelves = magicShelfRepository.findAllByUserId(userId);

        // Get public magic shelves
        List<MagicShelfEntity> publicShelves = magicShelfRepository.findAllByIsPublicIsTrue();

        // Combine and deduplicate (user's shelves that are also public shouldn't appear twice)
        Set<Long> seenIds = new HashSet<>();
        List<MagicShelfEntity> allShelves = new ArrayList<>();

        for (MagicShelfEntity shelf : userShelves) {
            if (seenIds.add(shelf.getId())) {
                allShelves.add(shelf);
            }
        }
        for (MagicShelfEntity shelf : publicShelves) {
            if (seenIds.add(shelf.getId())) {
                allShelves.add(shelf);
            }
        }

        List<AppMagicShelfSummary> summaries = allShelves.stream()
                .map(mobileBookMapper::toMagicShelfSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    @Operation(
            summary = "List books in app magic shelf",
            description = "Retrieve paginated books contained in a specific magic shelf for the app.",
            operationId = "appGetBooksByMagicShelf"
    )
    @GetMapping("/magic/{magicShelfId}/books")
    public ResponseEntity<AppPageResponse<AppBookSummary>> getBooksByMagicShelf(
            @PathVariable Long magicShelfId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(mobileBookService.getBooksByMagicShelf(magicShelfId, page, size));
    }
}
