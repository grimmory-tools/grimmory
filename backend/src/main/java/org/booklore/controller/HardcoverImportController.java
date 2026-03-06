package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.service.book.BookService;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hardcover-import")
@RequiredArgsConstructor
@Tag(name = "Hardcover import", description = "Endpoints to trigger a hardcover import")
public class HardcoverImportController {
    private final HardcoverSyncService hardcoverSyncService;
    private final BookService bookService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Trigger hardcover sync", description = "Trigger a hardcover import")
    @ApiResponse(responseCode = "200", description = "Hardcover import triggered")
    @PutMapping
    @PreAuthorize("@securityUtil.canSyncKobo() or @securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> updateSettings(@RequestBody boolean overwrite)
            throws ExecutionException, InterruptedException {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        hardcoverSyncService.importHardcoverData(user.getId(), overwrite);

        return ResponseEntity.ok().build();
    }
}
