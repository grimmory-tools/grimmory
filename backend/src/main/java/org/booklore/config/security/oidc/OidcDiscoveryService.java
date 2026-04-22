package org.booklore.config.security.oidc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OidcDiscoveryService {

    private static final Pattern TRAILING_SLASH = Pattern.compile("/+$");
    private static final long CACHE_TTL_MS = 3_600_000; // 1 hour
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final ConcurrentMap<String, CachedDiscovery> cache = new ConcurrentHashMap<>();

    public record DiscoveryDocument(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            URI jwksUri,
            String userinfoEndpoint,
            String endSessionEndpoint,
            List<String> scopesSupported,
            List<String> responseTypesSupported,
            List<String> codeChallengeMethodsSupported,
            Boolean backchannelLogoutSupported
    ) {}

    private record CachedDiscovery(DiscoveryDocument document, Instant fetchedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plusMillis(CACHE_TTL_MS));
        }
    }

    public DiscoveryDocument discover(String issuerUri) {
        String normalizedIssuer = TRAILING_SLASH.matcher(issuerUri).replaceAll("");

        return cache.compute(normalizedIssuer, (key, cached) -> {
            if (cached != null && !cached.isExpired()) {
                return cached;
            }
            return new CachedDiscovery(fetchDiscoveryDocument(key), Instant.now());
        }).document();
    }

    public void invalidate(String issuerUri) {
        cache.remove(TRAILING_SLASH.matcher(issuerUri).replaceAll(""));
    }

    @SuppressWarnings("unchecked")
    private DiscoveryDocument fetchDiscoveryDocument(String issuerUri) {
        String discoveryUrl = issuerUri + "/.well-known/openid-configuration";
        log.info("Fetching OIDC discovery document from {}", discoveryUrl);

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        var restClient = RestClient.builder().requestFactory(factory).build();

        Map<String, Object> doc = restClient.get()
                .uri(discoveryUrl)
                .retrieve()
                .body(Map.class);

        if (doc == null) {
            throw new IllegalStateException("Failed to fetch OIDC discovery document from " + discoveryUrl);
        }

        String jwksUriStr = (String) doc.get("jwks_uri");
        if (jwksUriStr == null || jwksUriStr.isBlank()) {
            throw new IllegalStateException("jwks_uri not found in discovery document");
        }

        return new DiscoveryDocument(
                (String) doc.get("issuer"),
                (String) doc.get("authorization_endpoint"),
                (String) doc.get("token_endpoint"),
                URI.create(jwksUriStr),
                (String) doc.get("userinfo_endpoint"),
                (String) doc.get("end_session_endpoint"),
                (List<String>) doc.get("scopes_supported"),
                (List<String>) doc.get("response_types_supported"),
                (List<String>) doc.get("code_challenge_methods_supported"),
                doc.get("backchannel_logout_supported") instanceof Boolean b ? b : null
        );
    }
}
