package org.booklore.controller;

import jakarta.validation.Valid;
import org.booklore.exception.ErrorResponse;
import org.booklore.model.dto.request.InitialUserRequest;
import org.booklore.model.dto.response.SuccessResponse;
import org.booklore.service.user.UserProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
@Tag(name = "Setup", description = "Endpoints for initial application setup and user provisioning")
public class SetupController {

    private final UserProvisioningService userProvisioningService;

    @Operation(summary = "Get setup status", description = "Check if initial setup has been completed.")
    @ApiResponse(responseCode = "200", description = "Setup status returned successfully")
    @GetMapping("/status")
    public ResponseEntity<?> getSetupStatus() {
        boolean isCompleted = userProvisioningService.isInitialUserAlreadyProvisioned();
        String message = isCompleted
                ? "Initial setup has already been completed."
                : "Initial setup is pending. No users have been created yet.";
        return ResponseEntity.ok(new SuccessResponse<>(200, message, isCompleted));
    }

    @Operation(summary = "Setup first user", description = "Provision the initial admin user during setup.")
    @ApiResponse(responseCode = "200", description = "Admin user created successfully")
    @PostMapping
    public ResponseEntity<?> setupFirstUser(
            @Parameter(description = "Initial user request") @RequestBody @Valid InitialUserRequest request) {
        if (userProvisioningService.isInitialUserAlreadyProvisioned()) {
            return ResponseEntity.status(403).body(new ErrorResponse(403, "Setup is disabled after the first user is created."));
        }
        userProvisioningService.provisionInitialUser(request);
        return ResponseEntity.ok(new SuccessResponse<>(200, "Admin user created successfully."));
    }
}