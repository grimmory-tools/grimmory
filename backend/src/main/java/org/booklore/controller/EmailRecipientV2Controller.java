package org.booklore.controller;

import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.service.email.EmailRecipientV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email/recipients")
@Tag(name = "Email Recipients", description = "Endpoints for managing email recipients for book delivery")
public class EmailRecipientV2Controller {

    private final EmailRecipientV2Service service;

    @Operation(summary = "Get all email recipients", description = "Retrieve a list of all configured email recipients.")
    @ApiResponse(responseCode = "200", description = "Email recipients returned successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailRecipientV2>> getEmailRecipients() {
        return ResponseEntity.ok(service.getEmailRecipients());
    }

    @Operation(summary = "Get an email recipient by ID", description = "Retrieve details of a specific email recipient.")
    @ApiResponse(responseCode = "200", description = "Email recipient returned successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailRecipientV2> getEmailRecipient(
            @Parameter(description = "ID of the email recipient") @PathVariable Long id) {
        return ResponseEntity.ok(service.getEmailRecipient(id));
    }

    @Operation(summary = "Create an email recipient", description = "Create a new email recipient configuration.")
    @ApiResponse(responseCode = "200", description = "Email recipient created successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PostMapping
    public ResponseEntity<EmailRecipientV2> createEmailRecipient(
            @Parameter(description = "Email recipient creation request") @RequestBody @Valid CreateEmailRecipientRequest createEmailRecipientRequest) {
        return ResponseEntity.ok(service.createEmailRecipient(createEmailRecipientRequest));
    }

    @Operation(summary = "Update an email recipient", description = "Update an existing email recipient configuration.")
    @ApiResponse(responseCode = "200", description = "Email recipient updated successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailRecipientV2> updateEmailRecipient(
            @Parameter(description = "ID of the email recipient") @PathVariable Long id,
            @Parameter(description = "Email recipient update request") @RequestBody @Valid CreateEmailRecipientRequest updateRequest) {
        return ResponseEntity.ok(service.updateEmailRecipient(id, updateRequest));
    }

    @Operation(summary = "Set default email recipient", description = "Set a specific email recipient as the default.")
    @ApiResponse(responseCode = "204", description = "Default email recipient set successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailRecipient(
            @Parameter(description = "ID of the email recipient") @PathVariable Long id) {
        service.setDefaultRecipient(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an email recipient", description = "Delete a specific email recipient configuration.")
    @ApiResponse(responseCode = "204", description = "Email recipient deleted successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailRecipient(
            @Parameter(description = "ID of the email recipient") @PathVariable Long id) {
        service.deleteEmailRecipient(id);
        return ResponseEntity.noContent().build();
    }
}