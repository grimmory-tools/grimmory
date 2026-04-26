package org.grimmory.config.security;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.service.security.JwtSecretService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtSecretService jwtSecretService;
    @Getter
    public static final long accessTokenExpirationMs = 1000L * 60 * 60 * 2;  // 2 hours
    @Getter
    public static final long refreshTokenExpirationMs = 1000L * 60 * 60 * 24 * 30; // 30 days

    private SecretKey getSigningKey() {
        String secretKey = jwtSecretService.getSecret();
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(BookLoreUserEntity user, boolean isRefreshToken) {
        long expirationTime = isRefreshToken ? refreshTokenExpirationMs : accessTokenExpirationMs;
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("booklore")
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("isDefaultPassword", user.isDefaultPassword())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationTime)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateAccessToken(BookLoreUserEntity user) {
        return generateToken(user, false);
    }

    public String generateRefreshToken(BookLoreUserEntity user) {
        return generateToken(user, true);
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("Invalid token: {}", e.getMessage());
        }
        return false;
    }

    public Claims extractClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Graceful issuer enforcement: reject tokens with a wrong issuer,
        // but allow legacy tokens that have no issuer claim (issued before this check).
        // After a full token rotation cycle (~30 days), switch to hard .requireIssuer().
        String issuer = claims.getIssuer();
        if (issuer != null && !"booklore".equals(issuer)) {
            throw new JwtException("Invalid issuer: " + issuer);
        }

        return claims;
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object userIdClaim = extractClaims(token).get("userId");
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        throw new IllegalArgumentException("Invalid userId claim type");
    }
}