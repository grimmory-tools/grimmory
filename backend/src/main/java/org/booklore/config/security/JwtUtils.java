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
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtSecretService jwtSecretService;
    private static final int MIN_SECRET_BYTES = 32;
    private static final String JWT_ISSUER = "booklore";

    private DefaultJWTClaimsVerifier<?> claimsVerifier;

    @Getter
    public static final long accessTokenExpirationMs = 1000L * 60 * 60 * 2;  // 2 hours
    @Getter
    public static final long refreshTokenExpirationMs = 1000L * 60 * 60 * 24 * 30; // 30 days

    @PostConstruct
    public void init() {
        validateSecret();
        this.claimsVerifier = new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(JWT_ISSUER).build(),
                Set.of("exp", "iat", "iss", "sub", "userId")
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

    public String generateToken(BookLoreUserEntity user, boolean isRefreshToken) {
        long expirationTime = isRefreshToken ? refreshTokenExpirationMs : accessTokenExpirationMs;
        Instant now = Instant.now();

        try {
            JWSSigner signer = new MACSigner(getSecretBytes());

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(JWT_ISSUER)
                    .subject(user.getUsername())
                    .claim("userId", user.getId())
                    .claim("isDefaultPassword", user.isDefaultPassword())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(expirationTime)))
                    .build();

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
        return generateToken(user, false);
    }

    public String generateRefreshToken(BookLoreUserEntity user) {
        return generateToken(user, true);
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
            log.error("Malformed token", e);
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
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = parseAndVerify(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);
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
        Object userId = claims.getClaim("userId");
        if (!(userId instanceof Number)) {
            throw new BadJWTException("Invalid userId claim type");
        }
    }

    /**
     * Extracts claims from a token after verifying signature and validating expiration/issuer.
     * @throws RuntimeException if token is invalid or expired.
     */
    public JWTClaimsSet extractClaims(String token) {
        try {
            SignedJWT signedJWT = parseAndVerify(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);
            return claims;
        } catch (BadJWTException e) {
            throw ApiError.JWT_INVALID.createException(e.getMessage());
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw ApiError.JWT_INVALID.createException(e.getMessage());
        }
    }

    /**
     * Extracts username from token.
     * @throws RuntimeException if token is invalid or expired.
     */
    public String extractUsername(String token) {
        try {
            return extractClaims(token).getSubject();
        } catch (Exception e) {
            log.warn("Failed to extract username from token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts user ID from token.
     * @throws RuntimeException if token is invalid or expired.
     */
    public Long extractUserId(String token) {
        Object userIdClaim = extractClaims(token).getClaim("userId");
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        throw ApiError.JWT_INVALID.createException("Invalid userId claim type");
    }
}
