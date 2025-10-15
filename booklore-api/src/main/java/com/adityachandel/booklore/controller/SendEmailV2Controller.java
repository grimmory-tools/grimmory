package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.SendBookByEmailRequest;
import com.adityachandel.booklore.service.email.EmailService;
import com.adityachandel.booklore.service.email.SendEmailV2Service;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v2/email")
public class SendEmailV2Controller {

    private final SendEmailV2Service service;

    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @PostMapping("/book")
    public ResponseEntity<?> sendEmail(@Validated @RequestBody SendBookByEmailRequest request) {
        service.emailBook(request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.canEmailBook() or @securityUtil.isAdmin()")
    @PostMapping("/book/{bookId}")
    public ResponseEntity<?> emailBookQuick(@PathVariable Long bookId) {
        service.emailBookQuick(bookId);
        return ResponseEntity.noContent().build();
    }
}