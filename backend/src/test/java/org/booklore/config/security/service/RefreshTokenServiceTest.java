package org.booklore.config.security.service;

import org.booklore.config.security.JwtUtils;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private JwtUtils jwtUtils;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Captor
    private ArgumentCaptor<RefreshTokenEntity> refreshTokenEntityArgumentCaptor;

    @Test
    void createRefreshToken_usesJWTID() {
        when(jwtUtils.generateToken(any(), anyLong(), anyLong(), anySet())).thenReturn("example-token");
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("jwt-id");

        BookLoreUserEntity user = BookLoreUserEntity.builder().build();
        String actual = refreshTokenService.createRefreshToken(user);

        verify(refreshTokenRepository).save(refreshTokenEntityArgumentCaptor.capture());

        var entity = refreshTokenEntityArgumentCaptor.getValue();

        assertThat(actual).isEqualTo("example-token");
        assertThat(entity.getToken()).isEqualTo("jwt-id");
    }

    @Test
    void createRefreshToken_savesWithTTLInFuture() {
        when(jwtUtils.generateToken(any(), anyLong(), anyLong(), anySet())).thenReturn("example-token");
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("example");

        BookLoreUserEntity user = BookLoreUserEntity.builder().build();

        refreshTokenService.createRefreshToken(user);

        verify(refreshTokenRepository).save(refreshTokenEntityArgumentCaptor.capture());

        var entity = refreshTokenEntityArgumentCaptor.getValue();

        assertThat(entity.getExpiryDate()).isInTheFuture();
    }

    @Test
    void findByToken_retrievesValid() {
        when(jwtUtils.validateToken(anyString(), anySet())).thenReturn(true);
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("example");

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isFalse();
    }
    @Test
    void findByToken_ignoresExpired() {
        when(jwtUtils.validateToken(anyString(), anySet())).thenReturn(true);
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("example");

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("example")).thenReturn(Optional.of(refreshTokenEntity));

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void findByToken_ignoresInvalidJWT() {
        when(jwtUtils.validateToken(anyString(), anySet())).thenReturn(false);

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void findByToken_ignoresRevoked() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(true)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("jwt-id")).thenReturn(Optional.of(refreshTokenEntity));
        when(jwtUtils.validateToken("example", Set.of())).thenReturn(true);
        when(jwtUtils.unsafeExtractJWTID("example")).thenReturn("jwt-id");

        var actual = refreshTokenService.findByToken("example");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    void revoke_setsExpectedFields() {
        var refreshTokenEntity = RefreshTokenEntity.builder()
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        refreshTokenService.revoke(refreshTokenEntity);

        verify(refreshTokenRepository).save(refreshTokenEntityArgumentCaptor.capture());

        var entity = refreshTokenEntityArgumentCaptor.getValue();

        assertThat(entity.getRevocationDate()).isNotNull();
        assertThat(entity.isRevoked()).isTrue();
    }

    @Test
    void refresh_revokesOldToken() {
        when(jwtUtils.generateToken(any(), anyLong(), anyLong(), anySet())).thenReturn("example-token");
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("example");

        var user = BookLoreUserEntity.builder().build();

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token("old-token")
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
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
        when(jwtUtils.generateToken(any(), anyLong(), anyLong(), anySet())).thenReturn("example-token");
        when(jwtUtils.unsafeExtractJWTID(anyString())).thenReturn("example");

        var user = BookLoreUserEntity.builder().build();

        var refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token("old-token")
                .revoked(false)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
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
