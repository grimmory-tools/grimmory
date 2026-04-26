package org.booklore.config.security.oidc;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class OidcTokenClient {

    private final OidcDiscoveryService discoveryService;
    private final RestTemplate oidcRestTemplate;

    public record TokenResponse(
            String accessToken,
            String idToken,
            String refreshToken,
            String tokenType,
            Integer expiresIn
    ) {}

    public TokenResponse exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri, OidcProviderDetails providerDetails) {
        var discovery = discoveryService.discover(providerDetails.getIssuerUri());
        String tokenEndpoint = discovery.tokenEndpoint();

        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            throw ApiError.OIDC_PROVIDER_UNREACHABLE.createException("Token endpoint not found in discovery document");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", providerDetails.getClientId());
        if (providerDetails.getClientSecret() != null && !providerDetails.getClientSecret().isBlank()) {
            body.add("client_secret", providerDetails.getClientSecret());
        }
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", codeVerifier);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = oidcRestTemplate
                    .postForObject(tokenEndpoint, new HttpEntity<>(body, headers), Map.class);

            if (response == null) {
                throw ApiError.OIDC_TOKEN_EXCHANGE_FAILED.createException("Empty response from token endpoint");
            }

            if (response.containsKey("error")) {
                String error = (String) response.get("error");
                String description = (String) response.getOrDefault("error_description", "");
                log.error("OIDC token exchange error: {} - {}", error, description);
                throw ApiError.OIDC_TOKEN_EXCHANGE_FAILED.createException(error + " " + description);
            }

            return new TokenResponse(
                    (String) response.get("access_token"),
                    (String) response.get("id_token"),
                    (String) response.get("refresh_token"),
                    (String) response.get("token_type"),
                    response.get("expires_in") instanceof Number n ? n.intValue() : null
            );
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("OIDC token exchange failed: {}", e.getMessage());
            throw ApiError.OIDC_PROVIDER_UNREACHABLE.createException(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchUserInfo(String accessToken, String issuerUri) {
        var discovery = discoveryService.discover(issuerUri);
        String userinfoEndpoint = discovery.userinfoEndpoint();

        if (userinfoEndpoint == null || userinfoEndpoint.isBlank()) {
            log.debug("No userinfo endpoint available, skipping userinfo fetch");
            return Map.of();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            var response = oidcRestTemplate
                    .exchange(userinfoEndpoint, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("Failed to fetch userinfo: {}", e.getMessage());
            return Map.of();
        }
    }
}
