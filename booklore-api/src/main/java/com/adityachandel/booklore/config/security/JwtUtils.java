package com.adityachandel.booklore.config.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.service.JwtSecretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Component
public class JwtUtils {

    private final JwtSecretService jwtSecretService;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtils(JwtSecretService jwtSecretService) {
        this.jwtSecretService = jwtSecretService;
        this.accessTokenExpirationMs = 36000000;  // 10 hours
        this.refreshTokenExpirationMs = 604800000; // 7 days
    }

    private SecretKey getSigningKey() {
        String secretKey = jwtSecretService.getSecret();
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(BookLoreUserEntity user, boolean isRefreshToken) {
        long expirationTime = isRefreshToken ? refreshTokenExpirationMs : accessTokenExpirationMs;
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("isDefaultPassword", user.isDefaultPassword())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
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
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }
}