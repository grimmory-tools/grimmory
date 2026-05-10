package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.oidc.BackchannelLogoutService;
import org.booklore.config.security.oidc.OidcAuthService;
import org.booklore.config.security.oidc.OidcCallbackRequest;
import org.booklore.config.security.oidc.OidcStateService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.AccessTokenDto;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/oidc")
@Tag(name = "OIDC Authentication", description = "Endpoints for OIDC state generation, callbacks, and logout flows")
public class OidcAuthController {

    private final OidcAuthService oidcAuthService;
    private final BackchannelLogoutService backchannelLogoutService;
    private final OidcStateService oidcStateService;
    private final AuditService auditService;

    @Operation(
            summary = "Generate OIDC state",
            description = "Generate a one-time state value for initiating an OIDC authentication flow.",
            operationId = "oidcGenerateState"
    )
    @GetMapping("/state")
    public ResponseEntity<Map<String, String>> generateState() {
        return ResponseEntity.ok(Map.of("state", oidcStateService.generateState()));
    }

    @Operation(
            summary = "Handle OIDC callback",
            description = "Process the OIDC callback payload and exchange authorization code for tokens.",
            operationId = "oidcHandleCallback"
    )
    @PostMapping("/callback")
    public ResponseEntity<AccessTokenDto> handleCallback(
            @RequestBody @Valid OidcCallbackRequest request,
            HttpServletRequest httpRequest) {
        log.info("OIDC callback received");
        oidcStateService.validateAndConsume(request.state());
        try {
            return oidcAuthService.exchangeCodeForTokens(
                    request.code(),
                    request.codeVerifier(),
                    request.redirectUri(),
                    request.nonce(),
                    httpRequest
            );
        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC callback login failed");
            throw e;
        }
    }

    @Operation(
            summary = "Handle OIDC redirect callback",
            description = "Handle redirect-based OIDC callback and redirect back to the app with token information.",
            operationId = "oidcHandleRedirect"
    )
    @GetMapping("/redirect")
    public ResponseEntity<Void> handleRedirect(
            @RequestParam("code") String code,
            @RequestParam("code_verifier") String codeVerifier,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("nonce") String nonce,
            @RequestParam("state") String state,
            @RequestParam("app_redirect_uri") String appRedirectUri,
            HttpServletRequest httpRequest) {

        oidcStateService.validateAndConsume(state);
        oidcAuthService.validateAppRedirectUri(appRedirectUri);

        try {
            ResponseEntity<AccessTokenDto> tokenResponse = oidcAuthService.exchangeCodeForTokens(
                    code, codeVerifier, redirectUri, nonce, httpRequest);
            AccessTokenDto tokens = tokenResponse.getBody();

            if (tokens == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Failed to obtain tokens");
            }

            StringBuilder fragment = new StringBuilder();
            fragment.append("access_token=").append(URLEncoder.encode(tokens.getAccessToken(), StandardCharsets.UTF_8));
            fragment.append("&refresh_token=").append(URLEncoder.encode(tokens.getRefreshToken(), StandardCharsets.UTF_8));

            if (tokens.getIsDefaultPassword() != null) {
                fragment.append("&is_default_password=").append(URLEncoder.encode(tokens.getIsDefaultPassword().toString(), StandardCharsets.UTF_8));
            }

            if (tokens.getExpires() != null) {
                fragment.append("&expires_in=").append(URLEncoder.encode(tokens.getExpires().toString(), StandardCharsets.UTF_8));
            }

            String redirectUrl = appRedirectUri + "#" + fragment;

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC redirect login failed");
            String errorRedirect = appRedirectUri + "#error=" + URLEncoder.encode("Authentication failed", StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirect));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @Operation(
            summary = "Handle OIDC mobile callback",
            description = "Process mobile OIDC callback parameters and exchange authorization code for tokens.",
            operationId = "oidcHandleMobileCallback"
    )
    @PostMapping("/mobile/callback")
    public ResponseEntity<AccessTokenDto> handleMobileCallback(
            @RequestParam("code") String code,
            @RequestParam("code_verifier") String codeVerifier,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("nonce") String nonce,
            @RequestParam("state") String state,
            HttpServletRequest httpRequest) {

        oidcStateService.validateAndConsume(state);
        try {
            return oidcAuthService.exchangeCodeForTokens(code, codeVerifier, redirectUri, nonce, httpRequest);
        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC mobile callback login failed");
            throw e;
        }
    }

    @Operation(
            summary = "Handle OIDC backchannel logout",
            description = "Process OIDC backchannel logout token and invalidate matching session state.",
            operationId = "oidcBackchannelLogout"
    )
    @PostMapping(value = "/backchannel-logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> backchannelLogout(@RequestParam("logout_token") String logoutToken) {
        try {
            backchannelLogoutService.handleLogoutToken(logoutToken);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Back-channel logout failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
