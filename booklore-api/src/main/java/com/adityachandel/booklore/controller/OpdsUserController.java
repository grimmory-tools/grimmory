package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.OpdsUser;
import com.adityachandel.booklore.model.dto.request.OpdsUserCreateRequest;
import com.adityachandel.booklore.model.dto.request.PasswordResetRequest;
import com.adityachandel.booklore.service.OpdsUserService;
import com.adityachandel.booklore.service.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/opds-users")
public class OpdsUserController {

    private final OpdsUserService opdsUserService;

    @PreAuthorize("@securityUtil.isAdmin()")
    @PostMapping
    public ResponseEntity<Void> createOpdsUser(@RequestBody OpdsUserCreateRequest request) {
        opdsUserService.createOpdsUser(request.getUsername(), request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @GetMapping
    public ResponseEntity<List<OpdsUser>> listOpdsUsers() {
        List<OpdsUser> users = opdsUserService.getOpdsUsers();
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("@securityUtil.isAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOpdsUser(@PathVariable Long id) {
        opdsUserService.deleteOpdsUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable("userId") Long userId, @RequestBody PasswordResetRequest resetRequest) {
        opdsUserService.resetPassword(userId, resetRequest.getPassword());
        return ResponseEntity.ok().build();
    }
}
