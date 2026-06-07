package org.booklore.grimmlink.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkBookSummary;
import org.booklore.grimmlink.dto.GrimmlinkShelfRemovalResponse;
import org.booklore.grimmlink.dto.GrimmlinkShelfSummary;
import org.booklore.grimmlink.facade.GrimmlinkFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/shelves")
public class GrimmlinkV1ShelfController {

    private final GrimmlinkFacade grimmlinkFacade;

    @GetMapping
    public ResponseEntity<List<GrimmlinkShelfSummary>> listShelves(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(grimmlinkFacade.listShelves(type));
    }

    @GetMapping("/{shelfId}/books")
    public ResponseEntity<List<GrimmlinkBookSummary>> listShelfBooks(@PathVariable Long shelfId) {
        return ResponseEntity.ok(grimmlinkFacade.listShelfBooks("regular", shelfId));
    }

    @GetMapping("/{shelfType}/{shelfId}/books")
    public ResponseEntity<List<GrimmlinkBookSummary>> listShelfBooksByType(@PathVariable String shelfType,
                                                                          @PathVariable Long shelfId) {
        String debugId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            return ResponseEntity.ok(grimmlinkFacade.listShelfBooks(shelfType, shelfId));
        } catch (APIException ex) {
            log.error("GrimmLink shelf fetch failed debugId={} shelfType={} shelfId={} status={} message={}",
                    debugId, shelfType, shelfId, ex.getStatus(), ex.getMessage(), ex);
            throw new APIException(ex.getMessage() + " [debugId=" + debugId + "]", ex.getStatus());
        } catch (Exception ex) {
            log.error("GrimmLink shelf fetch crashed debugId={} shelfType={} shelfId={}: {}",
                    debugId, shelfType, shelfId, ex.getMessage(), ex);
            throw new APIException("Failed to fetch shelf books (debugId=" + debugId + ")", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<GrimmlinkShelfRemovalResponse> removeBookFromShelf(@PathVariable Long shelfId,
                                                                             @PathVariable Long bookId) {
        return ResponseEntity.ok(grimmlinkFacade.removeBookFromShelf("regular", shelfId, bookId));
    }

    @PostMapping("/{shelfType}/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<GrimmlinkShelfRemovalResponse> removeBookFromShelfByType(@PathVariable String shelfType,
                                                                                   @PathVariable Long shelfId,
                                                                                   @PathVariable Long bookId) {
        return ResponseEntity.ok(grimmlinkFacade.removeBookFromShelf(shelfType, shelfId, bookId));
    }
}
