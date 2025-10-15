package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.EmailRecipientV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailRecipientRequest;
import com.adityachandel.booklore.service.email.EmailRecipientV2Service;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v2/email/recipients")
public class EmailRecipientV2Controller {

    private final EmailRecipientV2Service service;

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailRecipientV2>> getEmailRecipients() {
        return ResponseEntity.ok(service.getEmailRecipients());
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailRecipientV2> getEmailRecipient(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEmailRecipient(id));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PostMapping
    public ResponseEntity<EmailRecipientV2> createEmailRecipient(@RequestBody CreateEmailRecipientRequest createEmailRecipientRequest) {
        return ResponseEntity.ok(service.createEmailRecipient(createEmailRecipientRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailRecipientV2> updateEmailRecipient(@PathVariable Long id, @RequestBody CreateEmailRecipientRequest updateRequest) {
        return ResponseEntity.ok(service.updateEmailRecipient(id, updateRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailRecipient(@PathVariable Long id) {
        service.setDefaultRecipient(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailRecipient(@PathVariable Long id) {
        service.deleteEmailRecipient(id);
        return ResponseEntity.noContent().build();
    }
}