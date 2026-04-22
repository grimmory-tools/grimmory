package org.booklore.config.security.oidc;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@AllArgsConstructor
public class OidcTokenValidator {

    private static final int CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_IAT_AGE_SECONDS = 300;
    private static final long JWKS_CACHE_TTL_MS = 21_600_000; // 6 hours
    private static final long JWKS_REFRESH_MS = 3_600_000; // 1 hour

    private final OidcDiscoveryService discoveryService;

    private final ConcurrentMap<String, ConfigurableJWTProcessor<SecurityContext>> processorCache = new ConcurrentHashMap<>();

    public JWTClaimsSet validateIdToken(String idTokenStr, String issuerUri, String clientId, String expectedNonce, String accessToken) {
        try {
            var processor = getOrCreateProcessor(issuerUri);
            JWTClaimsSet claims = processor.process(idTokenStr, null);

            validateIssuer(claims, issuerUri);
            validateAudience(claims, clientId);
            validateAuthorizedParty(claims, clientId);
            validateExpiration(claims);
            validateIssuedAt(claims);
            validateNonce(claims, expectedNonce);
            validateAccessTokenHash(claims, accessToken, idTokenStr);

            return claims;
        } catch (org.booklore.exception.APIException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OIDC ID token validation failed: {}", e.getMessage());
            throw ApiError.OIDC_INVALID_TOKEN.createException("Invalid OIDC ID token");
        }
    }

    public JWTClaimsSet validateIdToken(String idTokenStr, String issuerUri, String clientId, String expectedNonce) {
        return validateIdToken(idTokenStr, issuerUri, clientId, expectedNonce, null);
    }

    public void invalidateProcessor(String issuerUri) {
        processorCache.remove(issuerUri);
    }

    private ConfigurableJWTProcessor<SecurityContext> getOrCreateProcessor(String issuerUri) {
        return processorCache.computeIfAbsent(issuerUri, this::buildProcessor);
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(String issuerUri) {
        var discovery = discoveryService.discover(issuerUri);
        URI jwksUri = discovery.jwksUri();

        var resourceRetriever = new DefaultResourceRetriever(10_000, 10_000);
        var jwkSetCache = new DefaultJWKSetCache(JWKS_CACHE_TTL_MS, JWKS_REFRESH_MS, TimeUnit.MILLISECONDS);
        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(uriToUrl(jwksUri), resourceRetriever, jwkSetCache);

        Set<JWSAlgorithm> jwsAlgs = new HashSet<>();
        jwsAlgs.addAll(JWSAlgorithm.Family.RSA);
        jwsAlgs.addAll(JWSAlgorithm.Family.EC);

        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(jwsAlgs, jwkSource);
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT, new JOSEObjectType("logout+jwt"), null));

        return processor;
    }

    private void validateIssuer(JWTClaimsSet claims, String expectedIssuer) {
        String issuer = claims.getIssuer();
        String normalizedExpected = FileUtils.trimTrailingSlashes(expectedIssuer);
        String normalizedActual = issuer != null ? FileUtils.trimTrailingSlashes(issuer) : "";

        if (!normalizedExpected.equals(normalizedActual)) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token issuer mismatch");
        }
    }

    private void validateAudience(JWTClaimsSet claims, String clientId) {
        if (claims.getAudience() == null || !claims.getAudience().contains(clientId)) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token audience does not contain client_id");
        }
    }

    private void validateAuthorizedParty(JWTClaimsSet claims, String clientId) {
        if (claims.getAudience() != null && claims.getAudience().size() > 1) {
            try {
                String azp = claims.getStringClaim("azp");
                if (azp != null && !azp.equals(clientId)) {
                    throw ApiError.OIDC_INVALID_TOKEN.createException("ID token authorized party mismatch");
                }
            } catch (java.text.ParseException e) {
                throw ApiError.OIDC_INVALID_TOKEN.createException("Failed to parse azp claim");
            }
        }
    }

    private void validateExpiration(JWTClaimsSet claims) {
        if (claims.getExpirationTime() == null) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token missing expiration claim");
        }
        Instant exp = claims.getExpirationTime().toInstant().plusSeconds(CLOCK_SKEW_SECONDS);
        if (Instant.now().isAfter(exp)) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token has expired");
        }
    }

    private void validateIssuedAt(JWTClaimsSet claims) {
        if (claims.getIssueTime() == null) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token missing iat claim");
        }
        Instant iat = claims.getIssueTime().toInstant();
        if (Instant.now().isAfter(iat.plusSeconds(MAX_IAT_AGE_SECONDS + CLOCK_SKEW_SECONDS))) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("ID token was issued too long ago");
        }
    }

    private void validateNonce(JWTClaimsSet claims, String expectedNonce) {
        try {
            String tokenNonce = claims.getStringClaim("nonce");
            if (!expectedNonce.equals(tokenNonce)) {
                throw ApiError.OIDC_INVALID_TOKEN.createException("ID token nonce mismatch");
            }
        } catch (java.text.ParseException e) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("Failed to parse nonce claim");
        }
    }

    private void validateAccessTokenHash(JWTClaimsSet claims, String accessToken, String idTokenStr) {
        try {
            String atHash = claims.getStringClaim("at_hash");
            if (atHash == null || accessToken == null) {
                return;
            }

            SignedJWT signedJWT = SignedJWT.parse(idTokenStr);
            String alg = signedJWT.getHeader().getAlgorithm().getName();
            String hashAlgorithm = mapAlgToHashAlgorithm(alg);

            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            byte[] leftHalf = new byte[hash.length / 2];
            System.arraycopy(hash, 0, leftHalf, 0, leftHalf.length);

            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
            if (!computed.equals(atHash)) {
                throw ApiError.OIDC_INVALID_TOKEN.createException("ID token at_hash mismatch");
            }
        } catch (org.booklore.exception.APIException e) {
            throw e;
        } catch (java.text.ParseException | NoSuchAlgorithmException e) {
            log.warn("Failed to validate at_hash: {}", e.getMessage());
        }
    }

    private String mapAlgToHashAlgorithm(String alg) {
        return switch (alg) {
            case "RS256", "ES256", "PS256" -> "SHA-256";
            case "RS384", "ES384", "PS384" -> "SHA-384";
            case "RS512", "ES512", "PS512" -> "SHA-512";
            default -> "SHA-256";
        };
    }

    public JWTClaimsSet validateLogoutToken(String logoutTokenStr, String issuerUri, String clientId) {
        try {
            var processor = getOrCreateProcessor(issuerUri);
            JWTClaimsSet claims = processor.process(logoutTokenStr, null);

            validateIssuer(claims, issuerUri);
            validateAudience(claims, clientId);
            validateIssuedAt(claims);
            if (claims.getExpirationTime() != null) {
                validateExpiration(claims);
            }

            var events = claims.getJSONObjectClaim("events");
            if (events == null || !events.containsKey("http://schemas.openid.net/event/backchannel-logout")) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Logout token missing backchannel-logout event claim");
            }

            if (claims.getClaim("nonce") != null) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Logout token must not contain a nonce claim");
            }

            return claims;
        } catch (org.booklore.exception.APIException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OIDC logout token validation failed: {}", e.getMessage());
            throw ApiError.GENERIC_BAD_REQUEST.createException("Invalid OIDC logout token");
        }
    }

    private static java.net.URL uriToUrl(URI uri) {
        try {
            return uri.toURL();
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid JWKS URI: " + uri, e);
        }
    }
}
