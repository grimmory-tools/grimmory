package org.booklore.config.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.service.security.JwtSecretService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtSecretService jwtSecretService;
    private static final int MIN_SECRET_BYTES = 32;
    private static final String JWT_ISSUER = "booklore";

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_SCOPE = "scope";

    private DefaultJWTClaimsVerifier<?> claimsVerifier;

    @Getter
    public static final long accessTokenExpirationMs = 1000L * 60 * 60 * 2;  // 2 hours
    private static final Set<String> ACCESS_TOKEN_SCOPES = Set.of("api");

    @PostConstruct
    public void init() {
        validateSecret();
        this.claimsVerifier = new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(JWT_ISSUER).build(),
                Set.of("exp", "iat", "iss", "sub", CLAIM_USER_ID)
        );
    }

    public void validateSecret() {
        try {
            getSecretBytes();
        } catch (IllegalStateException e) {
            // Misconfiguration — fail fast.
            throw e;
        } catch (DataAccessException e) {
            // DB not ready yet; will be re-validated on first use.
            log.warn("Could not validate JWT secret at startup (database not ready), will validate on first use: {}", e.getMessage());
        }
    }

    private byte[] getSecretBytes() {
        byte[] key = jwtSecretService.getSecret().getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        return key;
    }

    public String generateToken(BookLoreUserEntity user, long notBeforeMs, long expirationMs, Set<String> scopes) {
        Instant now = Instant.now();

        try {
            JWSSigner signer = new MACSigner(getSecretBytes());

            var builder = new JWTClaimsSet.Builder()
                    .issuer(JWT_ISSUER)
                    .jwtID(UUID.randomUUID().toString())
                    .subject(user.getUsername())
                    .claim(CLAIM_USER_ID, user.getId())
                    .claim(CLAIM_SCOPE, String.join(" ", scopes))
                    .claim("isDefaultPassword", user.isDefaultPassword())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now.plusMillis(notBeforeMs)))
                    .expirationTime(Date.from(now.plusMillis(expirationMs)));

            JWTClaimsSet claimsSet = builder.build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (KeyLengthException e) {
            log.error("JWT secret is too short: {}", e.getMessage());
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256", e);
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("Could not generate token", e);
        }
    }

    public String generateAccessToken(BookLoreUserEntity user) {
        return generateToken(user, 0, accessTokenExpirationMs, ACCESS_TOKEN_SCOPES);
    }

    /**
     * Parses and verifies the JWT token signature.
     * Does NOT check for expiration or other claims.
     */
    private SignedJWT parseAndVerify(String token) throws Exception {
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (Exception e) {
            throw ApiError.JWT_INVALID.createException("Malformed token");
        }

        JWSVerifier verifier = new MACVerifier(getSecretBytes());
        if (!signedJWT.verify(verifier)) {
            throw ApiError.JWT_INVALID.createException("Invalid token signature");
        }
        return signedJWT;
    }

    /**
     * Validates the token's signature, expiration, and issuer.
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN_SCOPES);
    }

    /**
     * Validates the token's signature, expiration, issuer, and scopes.
     */
    public boolean validateToken(String token, Set<String> requiredScopes) {
        try {
            SignedJWT signedJWT = parseAndVerify(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);
            validateScopes(claims, requiredScopes);
            return true;
        } catch (Exception e) {
            log.debug("Invalid token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if claims are valid using the built-in Nimbus verifier (expiration, issuer, clock skew).
     * @throws BadJWTException if claims are invalid.
     */
    private void validateClaims(JWTClaimsSet claims) throws BadJWTException {
        claimsVerifier.verify(claims, null);
        Object userId = claims.getClaim(CLAIM_USER_ID);
        if (!(userId instanceof Number)) {
            throw new BadJWTException("Invalid userId claim type");
        }
    }

    private void validateScopes(JWTClaimsSet claims, Set<String> requiredScopes) throws BadJWTException {
        if (requiredScopes.isEmpty()) {
            return;
        }

        try {
            var scopeClaimStr = claims.getClaimAsString(CLAIM_SCOPE);
            scopeClaimStr = scopeClaimStr == null ? "" : scopeClaimStr;

            var scopeClaim = Set.of(scopeClaimStr.trim().split(" +"));

            if (!scopeClaim.containsAll(requiredScopes)) {
                throw new BadJWTException("Lacks expected scopes");
            }
        } catch (ParseException e) {
            throw new BadJWTException("Lacks expected scopes");
        }
    }

    /**
     * Extracts claims from a token after verifying signature.
     * Does not validate if the token is expired or otherwise revoked.
     *
     * @throws RuntimeException if token is invalid
     */
    private JWTClaimsSet unsafeExtractClaims(String token) {
        try {
            SignedJWT signedJWT = parseAndVerify(token);
            return signedJWT.getJWTClaimsSet();
        } catch (BadJWTException e) {
            throw ApiError.JWT_INVALID.createException(e.getMessage());
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw ApiError.JWT_INVALID.createException(e.getMessage());
        }
    }

    /**
     * Extracts JWT ID from a token after verifying signature.
     * Does not validate if the token is expired or otherwise revoked.
     *
     * @throws RuntimeException if token is invalid
     */
    public String unsafeExtractJWTID(String token) {
        return unsafeExtractClaims(token).getJWTID();
    }

    /**
     * Extracts username from token.
     * @throws RuntimeException if token is invalid
     */
    public String unsafeExtractUsername(String token) {
        try {
            return unsafeExtractClaims(token).getSubject();
        } catch (Exception e) {
            log.warn("Failed to extract username from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts user ID from token.
     * @throws RuntimeException if token is invalid
     */
    public long unsafeExtractUserId(String token) {
        Object userIdClaim = unsafeExtractClaims(token).getClaim(CLAIM_USER_ID);
        if (userIdClaim instanceof Number userIdNumber) {
            return userIdNumber.longValue();
        }
        throw ApiError.JWT_INVALID.createException("Invalid userId claim type");
    }
}
