package org.booklore.config.security.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final long DEFAULT_EXPIRATION_MS = 1000L * 60 * 60 * 24 * 30; // 30 days
    private static final long DEFAULT_NOT_BEFORE_MS = 1000L * 60 * 2; // 2 minutes
    private static final int TOKEN_LENGTH = 48;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    private final RefreshTokenRepository refreshTokenRepository;

    private String generateRefreshToken(int length) {
        int byteLength = ((4 * length / 3) + 3) & ~3;
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes).substring(0, length);
    }

    @Transactional
    public String createRefreshToken(BookLoreUserEntity user) {
        return createRefreshToken(user, DEFAULT_EXPIRATION_MS);
    }

    @Transactional
    public String createRefreshToken(BookLoreUserEntity user, long expirationMs) {
        String token = generateRefreshToken(TOKEN_LENGTH);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(token)
                .notBeforeDate(Instant.now().plusMillis(DEFAULT_NOT_BEFORE_MS))
                .expiryDate(Instant.now().plusMillis(expirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return token;
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        var refreshTokenEntity = refreshTokenRepository.findByToken(token);

        boolean isRevoked = refreshTokenEntity.map(RefreshTokenEntity::isRevoked).orElse(false);
        boolean isNotReady = refreshTokenEntity.map(t -> t.getNotBeforeDate().isAfter(Instant.now())).orElse(false);
        boolean isExpired = refreshTokenEntity.map(t -> t.getExpiryDate().isBefore(Instant.now())).orElse(false);

        if (refreshTokenEntity.isEmpty() || isRevoked || isNotReady || isExpired) {
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
