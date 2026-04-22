package org.booklore.controller;

import org.booklore.model.dto.EmailProviderV2;
import org.booklore.model.dto.request.CreateEmailProviderRequest;
import org.booklore.service.email.EmailProviderV2Service;
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
@RequestMapping("/api/v1/email/providers")
@Tag(name = "Email Providers", description = "Endpoints for managing email providers for book delivery")
public class EmailProviderV2Controller {

    private final EmailProviderV2Service service;

    @Operation(summary = "Get all email providers", description = "Retrieve a list of all configured email providers.")
    @ApiResponse(responseCode = "200", description = "Email providers returned successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailProviderV2>> getEmailProviders() {
        return ResponseEntity.ok(service.getEmailProviders());
    }

    @Operation(summary = "Get an email provider by ID", description = "Retrieve details of a specific email provider.")
    @ApiResponse(responseCode = "200", description = "Email provider returned successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailProviderV2> getEmailProvider(
            @Parameter(description = "ID of the email provider") @PathVariable Long id) {
        return ResponseEntity.ok(service.getEmailProvider(id));
    }

    @Operation(summary = "Create an email provider", description = "Create a new email provider configuration.")
    @ApiResponse(responseCode = "200", description = "Email provider created successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PostMapping
    public ResponseEntity<EmailProviderV2> createEmailProvider(
            @Parameter(description = "Email provider creation request") @RequestBody @Valid CreateEmailProviderRequest createEmailProviderRequest) {
        return ResponseEntity.ok(service.createEmailProvider(createEmailProviderRequest));
    }

    @Operation(summary = "Update an email provider", description = "Update an existing email provider configuration.")
    @ApiResponse(responseCode = "200", description = "Email provider updated successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailProviderV2> updateEmailProvider(
            @Parameter(description = "ID of the email provider") @PathVariable Long id,
            @Parameter(description = "Email provider update request") @RequestBody @Valid CreateEmailProviderRequest updateRequest) {
        return ResponseEntity.ok(service.updateEmailProvider(id, updateRequest));
    }

    @Operation(summary = "Set default email provider", description = "Set a specific email provider as the default.")
    @ApiResponse(responseCode = "204", description = "Default email provider set successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailProvider(
            @Parameter(description = "ID of the email provider") @PathVariable Long id) {
        service.setDefaultEmailProvider(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an email provider", description = "Delete a specific email provider configuration.")
    @ApiResponse(responseCode = "204", description = "Email provider deleted successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmailProvider(
            @Parameter(description = "ID of the email provider") @PathVariable Long id) {
        service.deleteEmailProvider(id);
        return ResponseEntity.noContent().build();
    }
}