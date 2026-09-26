package org.booklore.config.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.JwtUtils;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final long DEFAULT_EXPIRATION_MS = 1000L * 60 * 60 * 24 * 30; // 30 days
    private static final long DEFAULT_NOT_BEFORE_MS = 1000L * 60 * 2; // 2 minutes
    private static final Set<String> REFRESH_TOKEN_CLAIMS = Set.of("offline");

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public String createRefreshToken(BookLoreUserEntity user) {
        return createRefreshToken(user, DEFAULT_EXPIRATION_MS);
    }

    @Transactional
    public String createRefreshToken(BookLoreUserEntity user, long expirationMs) {
        String token = jwtUtils.generateToken(user, DEFAULT_NOT_BEFORE_MS, expirationMs, REFRESH_TOKEN_CLAIMS);

        String jwtId = jwtUtils.unsafeExtractJWTID(token);

        if (jwtId == null) {
            // This should not happen during runtime.
            throw new RuntimeException("Refresh token lacks an ID");
        }

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(jwtId)
                .expiryDate(Instant.now().plusMillis(expirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return token;
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        String jwtId = null;
        try {
            // Eventually we probably want to check for the `REFRESH_TOKEN_CLAIMS`
            // but for now we can assume that it's a refresh token if it's in the database.
            if (jwtUtils.validateToken(token, Set.of())) {
                jwtId = jwtUtils.unsafeExtractJWTID(token);
            }
        } catch (Exception e) {
            log.warn("Invalid JWT", e);
        }

        if (jwtId == null) {
            return Optional.empty();
        }

        var refreshTokenEntity = refreshTokenRepository.findByToken(jwtId);

        // For some reason we store the expiration in the database, too.
        boolean isRevoked = refreshTokenEntity.map(RefreshTokenEntity::isRevoked).orElse(false);
        boolean isExpired = refreshTokenEntity.map(t -> t.getExpiryDate().isBefore(Instant.now())).orElse(false);

        if (refreshTokenEntity.isEmpty() || isRevoked || isExpired) {
            return Optional.empty();
        }

        return refreshTokenEntity;
    }

    @Transactional
    public void revokeAllForUser(BookLoreUserEntity user) {
        var tokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);

        for (var token : tokens) {
            revoke(token);
        }
    }

    @Transactional
    public void revoke(RefreshTokenEntity refreshTokenEntity) {
        refreshTokenEntity.setRevoked(true);
        refreshTokenEntity.setRevocationDate(Instant.now());

        refreshTokenRepository.save(refreshTokenEntity);
    }

    @Transactional
    public String refresh(RefreshTokenEntity refreshTokenEntity) {
        revoke(refreshTokenEntity);

        return createRefreshToken(refreshTokenEntity.getUser());
    }
}
