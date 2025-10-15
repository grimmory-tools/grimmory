package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.EmailProviderV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.service.email.EmailProviderV2Service;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v2/email/providers")
public class EmailProviderV2Controller {

    private final EmailProviderV2Service service;

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailProviderV2>> getEmailProviders() {
        return ResponseEntity.ok(service.getEmailProviders());
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailProviderV2> getEmailProvider(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEmailProvider(id));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PostMapping
    public ResponseEntity<EmailProviderV2> createEmailProvider(@RequestBody CreateEmailProviderRequest createEmailProviderRequest) {
        return ResponseEntity.ok(service.createEmailProvider(createEmailProviderRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailProviderV2> updateEmailProvider(@PathVariable Long id, @RequestBody CreateEmailProviderRequest updateRequest) {
        return ResponseEntity.ok(service.updateEmailProvider(id, updateRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailProvider(@PathVariable Long id) {
        service.setDefaultEmailProvider(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmailProvider(@PathVariable Long id) {
        service.deleteEmailProvider(id);
        return ResponseEntity.noContent().build();
    }
}