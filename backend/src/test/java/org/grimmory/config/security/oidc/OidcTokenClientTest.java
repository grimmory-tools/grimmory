package org.booklore.config.security.oidc;

import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcTokenClientTest {

    @Mock
    private OidcDiscoveryService discoveryService;

    @Mock
    private RestTemplate oidcRestTemplate;

    @InjectMocks
    private OidcTokenClient oidcTokenClient;

    private static final String TOKEN_ENDPOINT = "https://issuer.example.com/oauth/token";
    private static final String USERINFO_ENDPOINT = "https://issuer.example.com/userinfo";
    private static final String ISSUER_URI = "https://issuer.example.com";

    private OidcProviderDetails createProviderDetails(String clientSecret) {
        var details = new OidcProviderDetails();
        details.setProviderName("test-provider");
        details.setClientId("test-client-id");
        details.setClientSecret(clientSecret);
        details.setIssuerUri(ISSUER_URI);
        return details;
    }

    private OidcDiscoveryService.DiscoveryDocument createDiscoveryDocument(String tokenEndpoint, String userinfoEndpoint) {
        return new OidcDiscoveryService.DiscoveryDocument(
                ISSUER_URI,
                "https://issuer.example.com/authorize",
                tokenEndpoint,
                URI.create("https://issuer.example.com/.well-known/jwks.json"),
                userinfoEndpoint,
                "https://issuer.example.com/logout",
                List.of("openid", "profile", "email"),
                List.of("code"),
                List.of("S256"),
                true
        );
    }

    @Test
    void exchangeAuthorizationCode_success_returnsTokenResponse() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("test-secret");

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("access_token", "access-123");
        responseMap.put("id_token", "id-456");
        responseMap.put("refresh_token", "refresh-789");
        responseMap.put("token_type", "Bearer");
        responseMap.put("expires_in", 3600);

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        var result = oidcTokenClient.exchangeAuthorizationCode("auth-code", "verifier", "https://app/callback", providerDetails);

        assertThat(result.accessToken()).isEqualTo("access-123");
        assertThat(result.idToken()).isEqualTo("id-456");
        assertThat(result.refreshToken()).isEqualTo("refresh-789");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);
    }

    @Test
    void exchangeAuthorizationCode_publicClient_omitsClientSecret() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails(null);

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("access_token", "access-123");
        responseMap.put("id_token", "id-456");
        responseMap.put("token_type", "Bearer");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        var result = oidcTokenClient.exchangeAuthorizationCode("auth-code", "verifier", "https://app/callback", providerDetails);

        assertThat(result.accessToken()).isEqualTo("access-123");
        assertThat(result.idToken()).isEqualTo("id-456");
        assertThat(result.refreshToken()).isNull();
        assertThat(result.expiresIn()).isNull();
    }

    @Test
    void exchangeAuthorizationCode_nullResponse_throwsTokenExchangeFailed() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("secret");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> oidcTokenClient.exchangeAuthorizationCode("code", "verifier", "https://app/callback", providerDetails))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Empty response from token endpoint");
    }

    @Test
    void exchangeAuthorizationCode_errorInResponse_throwsTokenExchangeFailed() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("secret");

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("error", "invalid_grant");
        responseMap.put("error_description", "Authorization code expired");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        assertThatThrownBy(() -> oidcTokenClient.exchangeAuthorizationCode("code", "verifier", "https://app/callback", providerDetails))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("invalid_grant");
    }

    @Test
    void exchangeAuthorizationCode_restClientException_throwsProviderUnreachable() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("secret");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> oidcTokenClient.exchangeAuthorizationCode("code", "verifier", "https://app/callback", providerDetails))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void exchangeAuthorizationCode_blankTokenEndpoint_throwsProviderUnreachable() {
        var discovery = createDiscoveryDocument("  ", USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("secret");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);

        assertThatThrownBy(() -> oidcTokenClient.exchangeAuthorizationCode("code", "verifier", "https://app/callback", providerDetails))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Token endpoint not found");
    }

    @Test
    void fetchUserInfo_success_returnsUserInfoMap() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        Map<String, Object> userInfo = Map.of("sub", "user-123", "email", "user@example.com");

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.exchange(eq(USERINFO_ENDPOINT), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userInfo));

        var result = oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI);

        assertThat(result).containsEntry("sub", "user-123").containsEntry("email", "user@example.com");
    }

    @Test
    void fetchUserInfo_noUserinfoEndpoint_returnsEmptyMap() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, null);

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);

        var result = oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchUserInfo_restClientException_returnsEmptyMap() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.exchange(eq(USERINFO_ENDPOINT), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Timeout"));

        var result = oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchUserInfo_nullResponseBody_returnsEmptyMap() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.exchange(eq(USERINFO_ENDPOINT), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null));

        var result = oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI);

        assertThat(result).isEmpty();
    }

    @Test
    void exchangeAuthorizationCode_expiresInAsNumber_parsedCorrectly() {
        var discovery = createDiscoveryDocument(TOKEN_ENDPOINT, USERINFO_ENDPOINT);
        var providerDetails = createProviderDetails("secret");

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("access_token", "access-123");
        responseMap.put("id_token", "id-456");
        responseMap.put("token_type", "Bearer");
        responseMap.put("expires_in", Long.valueOf(7200L));

        when(discoveryService.discover(ISSUER_URI)).thenReturn(discovery);
        when(oidcRestTemplate.postForObject(eq(TOKEN_ENDPOINT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        var result = oidcTokenClient.exchangeAuthorizationCode("code", "verifier", "https://app/callback", providerDetails);

        assertThat(result.expiresIn()).isEqualTo(7200);
    }
}
