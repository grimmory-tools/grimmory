package org.booklore.controller;

import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.service.email.SendEmailV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email")
@Tag(name = "Send Email", description = "Endpoints for sending books via email")
public class SendEmailV2Controller {

    private final SendEmailV2Service service;

    @Operation(summary = "Send book by email", description = "Send a book to a recipient via email. Requires email permission or admin.")
    @ApiResponse(responseCode = "204", description = "Book sent successfully")
    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @PostMapping("/book")
    public ResponseEntity<?> sendEmail(
            @Parameter(description = "Send book by email request") @Validated @RequestBody SendBookByEmailRequest request) {
        service.emailBook(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Quick send book by email", description = "Quickly send a book by its ID via email. Requires email permission or admin.")
    @ApiResponse(responseCode = "204", description = "Book sent successfully")
    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @PostMapping("/book/{bookId}")
    public ResponseEntity<?> emailBookQuick(
            @Parameter(description = "ID of the book to send") @PathVariable Long bookId) {
        service.emailBookQuick(bookId);
        return ResponseEntity.noContent().build();
    }
}