package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.EmailRecipient;
import com.adityachandel.booklore.model.dto.request.CreateEmailRecipientRequest;
import com.adityachandel.booklore.service.email.EmailRecipientService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Deprecated
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email/recipients")
public class EmailRecipientController {

    private final EmailRecipientService emailRecipientService;

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping
    public ResponseEntity<List<EmailRecipient>> getEmailRecipients() {
        return ResponseEntity.ok(emailRecipientService.getEmailRecipients());
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canEmailBook()")
    @GetMapping("/{id}")
    public ResponseEntity<EmailRecipient> getEmailRecipient(@PathVariable Long id) {
        return ResponseEntity.ok(emailRecipientService.getEmailRecipient(id));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PostMapping
    public ResponseEntity<EmailRecipient> createEmailRecipient(@RequestBody CreateEmailRecipientRequest createEmailRecipientRequest) {
        return ResponseEntity.ok(emailRecipientService.createEmailRecipient(createEmailRecipientRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<EmailRecipient> updateEmailRecipient(@PathVariable Long id, @RequestBody CreateEmailRecipientRequest updateRequest) {
        return ResponseEntity.ok(emailRecipientService.updateEmailRecipient(id, updateRequest));
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultEmailRecipient(@PathVariable Long id) {
        emailRecipientService.setDefaultRecipient(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailRecipient(@PathVariable Long id) {
        emailRecipientService.deleteEmailRecipient(id);
        return ResponseEntity.noContent().build();
    }
}