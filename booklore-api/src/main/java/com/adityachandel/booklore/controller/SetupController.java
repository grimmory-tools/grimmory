package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.exception.ErrorResponse;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.dto.response.SuccessResponse;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final UserProvisioningService userProvisioningService;

    @GetMapping("/status")
    public ResponseEntity<?> getSetupStatus() {
        boolean isCompleted = userProvisioningService.isInitialUserAlreadyProvisioned();
        String message = isCompleted
                ? "Initial setup has already been completed."
                : "Initial setup is pending. No users have been created yet.";
        return ResponseEntity.ok(new SuccessResponse<>(200, message, isCompleted));
    }

    @PostMapping
    public ResponseEntity<?> setupFirstUser(@RequestBody InitialUserRequest request) {
        if (userProvisioningService.isInitialUserAlreadyProvisioned()) {
            return ResponseEntity.status(403).body(new ErrorResponse(403, "Setup is disabled after the first user is created."));
        }
        userProvisioningService.provisionInitialUser(request);
        return ResponseEntity.ok(new SuccessResponse<>(200, "Admin user created successfully."));
    }
}