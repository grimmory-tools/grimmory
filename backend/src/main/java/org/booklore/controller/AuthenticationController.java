package org.booklore.controller;

import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.UserCreateRequest;
import org.booklore.model.dto.request.RefreshTokenRequest;
import org.booklore.model.dto.request.UserLoginRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.booklore.service.user.UserProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, and token management")
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AppProperties appProperties;
    private final UserProvisioningService userProvisioningService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @Operation(summary = "Register a new user", description = "Register a new user. Only admins can register users.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User registered successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/register")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<?> registerUser(
            @Parameter(description = "User registration request") @RequestBody @Valid UserCreateRequest userCreateRequest) {
        userProvisioningService.provisionInternalUser(userCreateRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Login user", description = "Authenticate a user and return JWT tokens.")
    @ApiResponse(responseCode = "200", description = "User authenticated successfully")
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> loginUser(
            @Parameter(description = "User login request") @RequestBody @Valid UserLoginRequest loginRequest) {
        return authenticationService.loginUser(loginRequest);
    }

    @Operation(summary = "Refresh JWT token", description = "Refresh the JWT token using a valid refresh token.")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        log.debug("[Auth] Refresh request received. Request object exists: {}", request != null);
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            log.warn("[Auth] Refresh request failed: missing or empty refresh token");
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token is missing"));
        }
        
        try {
            return authenticationService.refreshToken(request.getRefreshToken());
        } catch (Exception e) {
            log.error("[Auth] Token refresh failed: {}", e.getMessage());
            throw e;
        }
    }

    @Operation(summary = "Remote login", description = "Authenticate a user using remote authentication headers.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User authenticated successfully"),
        @ApiResponse(responseCode = "403", description = "Remote authentication is disabled")
    })
    @GetMapping("/remote")
    public ResponseEntity<Map<String, String>> loginRemote(
            @Parameter(description = "Authentication headers") @RequestHeader HttpHeaders headers) {
        if (!appProperties.getRemoteAuth().isEnabled()) {
            throw ApiError.REMOTE_AUTH_DISABLED.createException();
        }

        String name = headers.getFirst(appProperties.getRemoteAuth().getHeaderName());
        String username = headers.getFirst(appProperties.getRemoteAuth().getHeaderUser());
        String email = headers.getFirst(appProperties.getRemoteAuth().getHeaderEmail());
        String groups = headers.getFirst(appProperties.getRemoteAuth().getHeaderGroups());
        log.debug("Remote-Auth: header values present name: {}, username: {}, email: {}, groups: {}",
                name != null, username != null, email != null, groups != null);

        if ((username == null || username.isEmpty()) && (email != null && !email.isEmpty())) {
            log.debug("Remote-Auth: username is empty, trying to find user by email");
            Optional<BookLoreUserEntity> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                username = user.get().getUsername();
                log.debug("Remote-Auth: found user by email");
            }
        }

        return authenticationService.loginRemote(name, username, email, groups);
    }
}
