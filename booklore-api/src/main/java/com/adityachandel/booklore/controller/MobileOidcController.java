package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Controller for handling OIDC authentication for mobile applications.
 *
 * Mobile apps cannot use the standard web-based OIDC callback because they need
 * to receive tokens via a custom URL scheme (e.g., booknexus://callback).
 *
 * This controller provides endpoints that:
 * 1. Accept the OIDC authorization code from the mobile app
 * 2. Exchange it for OIDC tokens with the identity provider
 * 3. Validate the tokens and provision/authenticate the user
 * 4. Return Booklore JWT tokens to the mobile app
 */
@Tag(name = "Mobile OIDC", description = "Endpoints for mobile app OIDC authentication")
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/mobile")
public class MobileOidcController {

    private static final Pattern TRAILING_SLASHES_PATTERN = Pattern.compile("/+$");
    private final AppSettingService appSettingService;
    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    private static final ConcurrentMap<String, Object> userLocks = new ConcurrentHashMap<>();

    @Operation(
        summary = "Exchange OIDC authorization code for tokens",
        description = "Exchanges an OIDC authorization code for Booklore JWT tokens. " +
                      "The mobile app should call this endpoint after receiving the authorization code " +
                      "from the OIDC provider. This endpoint will exchange the code for OIDC tokens, " +
                      "validate them, provision the user if needed, and return Booklore JWT tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens issued successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or OIDC error"),
        @ApiResponse(responseCode = "401", description = "Authentication failed"),
        @ApiResponse(responseCode = "403", description = "OIDC is not enabled")
    })
    @PostMapping("/oidc/callback")
    public ResponseEntity<Map<String, String>> handleOidcCallback(
            @Parameter(description = "Authorization code from OIDC provider")
            @RequestParam("code") String code,
            @Parameter(description = "PKCE code verifier used when initiating the auth request")
            @RequestParam("code_verifier") String codeVerifier,
            @Parameter(description = "Redirect URI that was used in the authorization request")
            @RequestParam("redirect_uri") String redirectUri) {

        log.info("Mobile OIDC callback received");

        // Verify OIDC is enabled
        if (!appSettingService.getAppSettings().isOidcEnabled()) {
            throw ApiError.FORBIDDEN.createException("OIDC is not enabled on this server");
        }

        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        if (providerDetails == null || providerDetails.getIssuerUri() == null) {
            throw ApiError.FORBIDDEN.createException("OIDC is not properly configured");
        }

        try {
            // Discover token endpoint
            String tokenEndpoint = discoverTokenEndpoint(providerDetails.getIssuerUri());

            // Exchange authorization code for tokens
            Map<String, Object> tokenResponse = exchangeCodeForTokens(
                tokenEndpoint,
                code,
                codeVerifier,
                redirectUri,
                providerDetails.getClientId()
            );

            // Extract and validate ID token
            String idToken = (String) tokenResponse.get("id_token");
            if (idToken == null) {
                // Some providers may only return access_token, try to use that
                idToken = (String) tokenResponse.get("access_token");
            }

            if (idToken == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("No token received from OIDC provider");
            }

            // Parse the JWT to extract claims
            SignedJWT signedJWT = SignedJWT.parse(idToken);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Extract user information using claim mappings
            OidcProviderDetails.ClaimMapping claimMapping = providerDetails.getClaimMapping();
            String username = claims.getStringClaim(claimMapping.getUsername());
            String email = claims.getStringClaim(claimMapping.getEmail());
            String name = claims.getStringClaim(claimMapping.getName());

            if (username == null || username.isEmpty()) {
                // Fall back to email or subject if username claim is not available
                username = email != null ? email : claims.getSubject();
            }

            log.info("Mobile OIDC: Authenticating user '{}'", username);

            // Find or provision user
            BookLoreUserEntity userEntity = findOrProvisionUser(username, email, name);

            // Generate Booklore JWT tokens
            return authenticationService.loginUser(userEntity);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            log.error("Mobile OIDC authentication failed", e);
            throw ApiError.GENERIC_UNAUTHORIZED.createException("OIDC authentication failed: " + e.getMessage());
        }
    }

    @Operation(
        summary = "Mobile OIDC redirect callback",
        description = "Alternative endpoint that redirects to a mobile app URL scheme with tokens. " +
                      "Use this if your mobile app prefers to receive tokens via URL redirect rather than API response."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirect to mobile app with tokens"),
        @ApiResponse(responseCode = "400", description = "Invalid request or OIDC error"),
        @ApiResponse(responseCode = "403", description = "OIDC is not enabled")
    })
    @GetMapping("/oidc/redirect")
    public ResponseEntity<Void> handleOidcRedirect(
            @Parameter(description = "Authorization code from OIDC provider")
            @RequestParam("code") String code,
            @Parameter(description = "PKCE code verifier used when initiating the auth request")
            @RequestParam("code_verifier") String codeVerifier,
            @Parameter(description = "Redirect URI that was used in the authorization request")
            @RequestParam("redirect_uri") String redirectUri,
            @Parameter(description = "Mobile app URL scheme to redirect to (e.g., booknexus://callback)")
            @RequestParam("app_redirect_uri") String appRedirectUri) {

        try {
            // Use the callback handler to get tokens
            ResponseEntity<Map<String, String>> tokenResponse = handleOidcCallback(code, codeVerifier, redirectUri);
            Map<String, String> tokens = tokenResponse.getBody();

            if (tokens == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Failed to obtain tokens");
            }

            // Build redirect URL with tokens as query parameters
            StringBuilder redirectUrl = new StringBuilder(appRedirectUri);
            redirectUrl.append(appRedirectUri.contains("?") ? "&" : "?");
            redirectUrl.append("access_token=").append(URLEncoder.encode(tokens.get("accessToken"), StandardCharsets.UTF_8));
            redirectUrl.append("&refresh_token=").append(URLEncoder.encode(tokens.get("refreshToken"), StandardCharsets.UTF_8));

            if (tokens.containsKey("isDefaultPassword")) {
                redirectUrl.append("&is_default_password=").append(URLEncoder.encode(tokens.get("isDefaultPassword"), StandardCharsets.UTF_8));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl.toString()));

            log.info("Mobile OIDC: Redirecting to app with tokens");
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (APIException e) {
            // Redirect to app with error
            String errorRedirect = appRedirectUri +
                (appRedirectUri.contains("?") ? "&" : "?") +
                "error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirect));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    /**
     * Discover the token endpoint from the OIDC provider's well-known configuration.
     */
    private String discoverTokenEndpoint(String issuerUri) throws Exception {
        String discoveryUrl = TRAILING_SLASHES_PATTERN.matcher(issuerUri).replaceAll("") + "/.well-known/openid-configuration";

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(discoveryUrl, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch OIDC discovery document");
        }

        JsonNode discoveryDoc = objectMapper.readTree(response.getBody());
        JsonNode tokenEndpointNode = discoveryDoc.get("token_endpoint");

        if (tokenEndpointNode == null || tokenEndpointNode.isNull()) {
            // Fall back to standard path
            return TRAILING_SLASHES_PATTERN.matcher(issuerUri).replaceAll("") + "/protocol/openid-connect/token";
        }

        return tokenEndpointNode.asText();
    }

    /**
     * Exchange the authorization code for tokens with the OIDC provider.
     */
    private Map<String, Object> exchangeCodeForTokens(
            String tokenEndpoint,
            String code,
            String codeVerifier,
            String redirectUri,
            String clientId) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", codeVerifier);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenEndpoint, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Token exchange failed with status: " + response.getStatusCode());
            }

            return objectMapper.readValue(response.getBody(), Map.class);

        } catch (Exception e) {
            log.error("Token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    /**
     * Find existing user or provision a new one based on OIDC claims.
     */
    private BookLoreUserEntity findOrProvisionUser(String username, String email, String name) {
        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();
        boolean autoProvision = provisionDetails != null && provisionDetails.isEnableAutoProvisioning();

        return userRepository.findByUsername(username)
            .orElseGet(() -> {
                if (!autoProvision) {
                    log.warn("User '{}' not found and auto-provisioning is disabled.", username);
                    throw ApiError.GENERIC_UNAUTHORIZED.createException("User not found and auto-provisioning is disabled.");
                }

                Object lock = userLocks.computeIfAbsent(username, k -> new Object());
                try {
                    synchronized (lock) {
                        return userRepository.findByUsername(username)
                            .orElseGet(() -> {
                                log.info("Mobile OIDC: Provisioning new user '{}'", username);
                                return userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);
                            });
                    }
                } finally {
                    userLocks.remove(username);
                }
            });
    }
}
