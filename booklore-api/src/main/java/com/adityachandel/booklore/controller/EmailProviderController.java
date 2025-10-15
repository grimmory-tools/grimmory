package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.EmailProvider;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.service.email.EmailProviderService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Deprecated
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email/providers")
public class EmailProviderController {

    private final EmailProviderService emailProviderService;

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailProvider>> getEmailProviders() {
        return ResponseEntity.ok(emailProviderService.getEmailProviders());
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailProvider> getEmailProvider(@PathVariable Long id) {
        return ResponseEntity.ok(emailProviderService.getEmailProvider(id));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PostMapping
    public ResponseEntity<EmailProvider> createEmailProvider(@RequestBody CreateEmailProviderRequest createEmailProviderRequest) {
        return ResponseEntity.ok(emailProviderService.createEmailProvider(createEmailProviderRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailProvider> updateEmailProvider(@PathVariable Long id, @RequestBody CreateEmailProviderRequest updateRequest) {
        return ResponseEntity.ok(emailProviderService.updateEmailProvider(id, updateRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailProvider(@PathVariable Long id) {
        emailProviderService.setDefaultEmailProvider(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmailProvider(@PathVariable Long id) {
        emailProviderService.deleteEmailProvider(id);
        return ResponseEntity.noContent().build();
    }
}