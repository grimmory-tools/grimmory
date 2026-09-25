package org.booklore.config.security.service;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Captor
    private ArgumentCaptor<RefreshTokenEntity> refreshTokenEntityArgumentCaptor;

    @Test
    void createRefreshToken_generatesLongString() {
        BookLoreUserEntity user = BookLoreUserEntity.builder().build();
        String actual = refreshTokenService.createRefreshToken(user);

        assertThat(actual).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void createRefreshToken_savesWithTTLAndNBFInFuture() {
        BookLoreUserEntity user = BookLoreUserEntity.builder().build();

        refreshTokenService.createRefreshToken(user);

        verify(refreshTokenRepository).save(refreshTokenEntityArgumentCaptor.capture());

        var entity = refreshTokenEntityArgumentCaptor.getValue();

        assertThat(entity.getExpiryDate()).isInTheFuture();
        assertThat(entity.getNotBeforeDate()).isInTheFuture();
    }

    @Test
    void findByToken_retrievesValid() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isFalse();
    }
    @Test
    void findByToken_ignoresExpired() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void findByToken_ignoresNotBefore() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void findByToken_ignoresRevoked() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(true)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void revoke_setsExpectedFields() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        refreshTokenService.revoke(refreshTokenEntity);

        verify(refreshTokenRepository).save(refreshTokenEntityArgumentCaptor.capture());

        var entity = refreshTokenEntityArgumentCaptor.getValue();

        assertThat(entity.getRevocationDate()).isNotNull();
        assertThat(entity.isRevoked()).isTrue();
    }

    @Test
    void refresh_revokesOldToken() {
        var user = BookLoreUserEntity.builder().build();

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token("old-token")
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        refreshTokenService.refresh(refreshTokenEntity);

        verify(refreshTokenRepository, times(2)).save(refreshTokenEntityArgumentCaptor.capture());

        var entities = refreshTokenEntityArgumentCaptor.getAllValues();

        var oldToken = entities.getFirst();

        assertThat(oldToken.getToken()).isEqualTo("old-token");
        assertThat(oldToken.getRevocationDate()).isNotNull();
        assertThat(oldToken.isRevoked()).isTrue();
    }

    @Test
    void refresh_createsNewToken() {
        var user = BookLoreUserEntity.builder().build();

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token("old-token")
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .notBeforeDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        refreshTokenService.refresh(refreshTokenEntity);

        verify(refreshTokenRepository, times(2)).save(refreshTokenEntityArgumentCaptor.capture());

        var entities = refreshTokenEntityArgumentCaptor.getAllValues();

        var newToken = entities.getLast();

        assertThat(newToken.getToken()).isNotEqualTo("old-token");
        assertThat(newToken.getRevocationDate()).isNull();
        assertThat(newToken.isRevoked()).isFalse();
        assertThat(newToken.getUser()).isSameAs(user);
    }
}
