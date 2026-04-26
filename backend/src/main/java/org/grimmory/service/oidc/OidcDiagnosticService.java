package org.grimmory.service.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.config.security.oidc.OidcDiscoveryService;
import org.grimmory.model.dto.settings.OidcProviderDetails;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.grimmory.util.FileUtils;

@Slf4j
@Service
@AllArgsConstructor
public class OidcDiagnosticService {

    public record OidcTestResult(boolean success, List<OidcTestCheck> checks) {}

    public record OidcTestCheck(String name, CheckStatus status, String message) {}

    public enum CheckStatus { PASS, FAIL, WARN, SKIP }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @SuppressWarnings("unchecked")
    public OidcTestResult testConnection(OidcProviderDetails providerDetails) {
        List<OidcTestCheck> checks = new ArrayList<>();
        boolean hasFailure = false;

        // 1. Fetch discovery document (uncached)
        Map<String, Object> doc;
        try {
            String issuerUri = FileUtils.trimTrailingSlashes(providerDetails.getIssuerUri());
            String discoveryUrl = issuerUri + "/.well-known/openid-configuration";

            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);

            var restClient = RestClient.builder().requestFactory(factory).build();
            doc = restClient.get().uri(discoveryUrl).retrieve().body(Map.class);

            if (doc == null) {
                checks.add(new OidcTestCheck("Discovery Document", CheckStatus.FAIL, "Empty response from discovery endpoint"));
                return new OidcTestResult(false, checks);
            }
            checks.add(new OidcTestCheck("Discovery Document", CheckStatus.PASS, "Successfully fetched from " + discoveryUrl));
        } catch (Exception e) {
            checks.add(new OidcTestCheck("Discovery Document", CheckStatus.FAIL, "Failed to fetch: " + e.getMessage()));
            return new OidcTestResult(false, checks);
        }

        // 2. Check required endpoints
        String authEndpoint = (String) doc.get("authorization_endpoint");
        if (authEndpoint != null && !authEndpoint.isBlank()) {
            checks.add(new OidcTestCheck("Authorization Endpoint", CheckStatus.PASS, authEndpoint));
        } else {
            checks.add(new OidcTestCheck("Authorization Endpoint", CheckStatus.FAIL, "Not found in discovery document"));
            hasFailure = true;
        }

        String tokenEndpoint = (String) doc.get("token_endpoint");
        if (tokenEndpoint != null && !tokenEndpoint.isBlank()) {
            checks.add(new OidcTestCheck("Token Endpoint", CheckStatus.PASS, tokenEndpoint));
        } else {
            checks.add(new OidcTestCheck("Token Endpoint", CheckStatus.FAIL, "Not found in discovery document"));
            hasFailure = true;
        }

        String jwksUri = (String) doc.get("jwks_uri");
        if (jwksUri != null && !jwksUri.isBlank()) {
            checks.add(new OidcTestCheck("JWKS URI", CheckStatus.PASS, jwksUri));
        } else {
            checks.add(new OidcTestCheck("JWKS URI", CheckStatus.FAIL, "Not found in discovery document"));
            hasFailure = true;
        }

        // 3. Fetch JWKS
        if (jwksUri != null && !jwksUri.isBlank()) {
            try {
                JWKSet jwkSet = JWKSet.load(URI.create(jwksUri).toURL());
                int keyCount = jwkSet.getKeys().size();
                checks.add(new OidcTestCheck("JWKS Keys", CheckStatus.PASS, keyCount + " key(s) found"));
            } catch (Exception e) {
                checks.add(new OidcTestCheck("JWKS Keys", CheckStatus.FAIL, "Failed to fetch JWKS: " + e.getMessage()));
                hasFailure = true;
            }
        } else {
            checks.add(new OidcTestCheck("JWKS Keys", CheckStatus.SKIP, "Skipped (no JWKS URI)"));
        }

        // 4. Check scopes
        List<String> scopes = (List<String>) doc.get("scopes_supported");
        if (scopes != null) {
            List<String> required = List.of("openid", "profile", "email");
            List<String> missing = required.stream().filter(s -> !scopes.contains(s)).toList();
            if (missing.isEmpty()) {
                checks.add(new OidcTestCheck("Required Scopes", CheckStatus.PASS, "openid, profile, email all supported"));
            } else {
                checks.add(new OidcTestCheck("Required Scopes", CheckStatus.WARN, "Missing scopes: " + String.join(", ", missing)));
            }
        } else {
            checks.add(new OidcTestCheck("Required Scopes", CheckStatus.WARN, "scopes_supported not listed in discovery document"));
        }

        // 5. Check response types
        List<String> responseTypes = (List<String>) doc.get("response_types_supported");
        if (responseTypes != null && responseTypes.contains("code")) {
            checks.add(new OidcTestCheck("Response Type 'code'", CheckStatus.PASS, "Authorization code flow supported"));
        } else if (responseTypes != null) {
            checks.add(new OidcTestCheck("Response Type 'code'", CheckStatus.FAIL, "Authorization code flow not supported"));
            hasFailure = true;
        } else {
            checks.add(new OidcTestCheck("Response Type 'code'", CheckStatus.WARN, "response_types_supported not listed"));
        }

        // 6. Check PKCE
        List<String> codeChallengeMethodsSupported = (List<String>) doc.get("code_challenge_methods_supported");
        if (codeChallengeMethodsSupported != null && codeChallengeMethodsSupported.contains("S256")) {
            checks.add(new OidcTestCheck("PKCE (S256)", CheckStatus.PASS, "S256 code challenge method supported"));
        } else if (codeChallengeMethodsSupported != null) {
            checks.add(new OidcTestCheck("PKCE (S256)", CheckStatus.WARN, "S256 not listed, available: " + String.join(", ", codeChallengeMethodsSupported)));
        } else {
            checks.add(new OidcTestCheck("PKCE (S256)", CheckStatus.WARN, "code_challenge_methods_supported not listed (PKCE may still work)"));
        }

        // 7. Logout endpoints (informational)
        String endSessionEndpoint = (String) doc.get("end_session_endpoint");
        if (endSessionEndpoint != null && !endSessionEndpoint.isBlank()) {
            checks.add(new OidcTestCheck("End Session Endpoint", CheckStatus.PASS, endSessionEndpoint));
        } else {
            checks.add(new OidcTestCheck("End Session Endpoint", CheckStatus.WARN, "Not available (RP-initiated logout won't work)"));
        }

        Object backchannelLogout = doc.get("backchannel_logout_supported");
        if (Boolean.TRUE.equals(backchannelLogout)) {
            checks.add(new OidcTestCheck("Back-Channel Logout", CheckStatus.PASS, "Supported by provider"));
        } else {
            checks.add(new OidcTestCheck("Back-Channel Logout", CheckStatus.WARN, "Not supported or not advertised"));
        }

        return new OidcTestResult(!hasFailure, checks);
    }
}
