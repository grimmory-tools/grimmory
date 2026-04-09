package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.config.security.service.LogoutService;
import org.booklore.model.dto.request.LogoutRequest;
import org.booklore.model.dto.response.LogoutResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, and token management")
public class LogoutController {

    private final LogoutService logoutService;

    @Operation(
            summary = "Logout user",
            description = "Logout the current user and invalidate refresh token state when provided.",
            operationId = "logout"
    )
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(Authentication auth,
                                                  @RequestBody(required = false) LogoutRequest request,
                                                  @RequestHeader(value = "Origin", required = false) String origin) {
        String refreshToken = request != null ? request.refreshToken() : null;
        LogoutResponse response = logoutService.logout(auth, refreshToken, origin);
        return ResponseEntity.ok(response);
    }
}
