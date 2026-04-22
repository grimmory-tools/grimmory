package org.booklore.config.security.oidc;

import com.github.benmanes.caffeine.cache.Cache;
import com.nimbusds.jwt.JWTClaimsSet;
import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.OidcSessionEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.OidcSessionRepository;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackchannelLogoutServiceTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private OidcTokenValidator oidcTokenValidator;

    @Mock
    private OidcSessionRepository oidcSessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BackchannelLogoutService backchannelLogoutService;

    private AppSettings appSettings;
    private OidcProviderDetails providerDetails;

    @BeforeEach
    void setUp() throws Exception {
        var field = BackchannelLogoutService.class.getDeclaredField("PROCESSED_JTIS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache = (Cache<String, Instant>) field.get(null);
        cache.invalidateAll();

        providerDetails = new OidcProviderDetails();
        providerDetails.setClientId("my-client");
        providerDetails.setIssuerUri("https://issuer.example.com");

        appSettings = new AppSettings();
        appSettings.setOidcEnabled(true);
        appSettings.setOidcProviderDetails(providerDetails);
    }

    @Test
    void handleLogoutToken_bySid_revokesSessionAndRefreshTokens() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-1")
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user = BookLoreUserEntity.builder().id(1L).username("testuser").build();
        var session = OidcSessionEntity.builder().user(user).oidcSessionId("session-123").revoked(false).build();
        when(oidcSessionRepository.findByOidcSessionIdAndRevokedFalse("session-123"))
                .thenReturn(List.of(session));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        backchannelLogoutService.handleLogoutToken("token");

        verify(oidcSessionRepository).save(session);
        assertThat(session.isRevoked()).isTrue();
        verify(notificationService).sendMessageToUser(eq("testuser"), eq(Topic.SESSION_REVOKED), any(Map.class));
        verify(auditService).log(eq(AuditAction.BACKCHANNEL_LOGOUT), eq("User"), eq(1L), any(String.class));
    }

    @Test
    void handleLogoutToken_bySubject_whenNoSid() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-2")
                .subject("user-sub")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user = BookLoreUserEntity.builder().id(2L).username("subuser").build();
        var session = OidcSessionEntity.builder().user(user).oidcSubject("user-sub").revoked(false).build();
        when(oidcSessionRepository.findByOidcSubjectAndOidcIssuerAndRevokedFalse("user-sub", "https://issuer.example.com"))
                .thenReturn(List.of(session));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        backchannelLogoutService.handleLogoutToken("token");

        verify(oidcSessionRepository).save(session);
        verify(oidcSessionRepository, never()).findByOidcSessionIdAndRevokedFalse(any());
        verify(notificationService).sendMessageToUser(eq("subuser"), eq(Topic.SESSION_REVOKED), any(Map.class));
    }

    @Test
    void handleLogoutToken_throwsWhenOidcDisabled() {
        appSettings.setOidcEnabled(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsWhenProviderDetailsNull() {
        appSettings.setOidcProviderDetails(null);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsWhenIssuerUriNull() {
        providerDetails.setIssuerUri(null);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsWhenJtiNull() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsWhenJtiBlank() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("   ")
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsOnJtiReplay() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("duplicate-jti")
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user = BookLoreUserEntity.builder().id(1L).username("testuser").build();
        var session = OidcSessionEntity.builder().user(user).oidcSessionId("session-123").revoked(false).build();
        when(oidcSessionRepository.findByOidcSessionIdAndRevokedFalse("session-123"))
                .thenReturn(List.of(session));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        backchannelLogoutService.handleLogoutToken("token");

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_throwsWhenBothSidAndSubNull() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-no-sid-sub")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        assertThatThrownBy(() -> backchannelLogoutService.handleLogoutToken("token"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void handleLogoutToken_revokesAllRefreshTokensForUser() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-rt")
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user = BookLoreUserEntity.builder().id(1L).username("testuser").build();
        var session = OidcSessionEntity.builder().user(user).oidcSessionId("session-123").revoked(false).build();
        when(oidcSessionRepository.findByOidcSessionIdAndRevokedFalse("session-123"))
                .thenReturn(List.of(session));

        var rt1 = RefreshTokenEntity.builder().revoked(false).build();
        var rt2 = RefreshTokenEntity.builder().revoked(false).build();
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of(rt1, rt2));

        backchannelLogoutService.handleLogoutToken("token");

        assertThat(rt1.isRevoked()).isTrue();
        assertThat(rt1.getRevocationDate()).isNotNull();
        assertThat(rt2.isRevoked()).isTrue();
        assertThat(rt2.getRevocationDate()).isNotNull();
        verify(refreshTokenRepository).save(rt1);
        verify(refreshTokenRepository).save(rt2);
    }

    @Test
    void handleLogoutToken_sendsWebSocketNotification() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-ws")
                .subject("user-sub")
                .claim("sid", "session-123")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user = BookLoreUserEntity.builder().id(1L).username("wsuser").build();
        var session = OidcSessionEntity.builder().user(user).oidcSessionId("session-123").revoked(false).build();
        when(oidcSessionRepository.findByOidcSessionIdAndRevokedFalse("session-123"))
                .thenReturn(List.of(session));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        backchannelLogoutService.handleLogoutToken("token");

        verify(notificationService).sendMessageToUser(
                eq("wsuser"),
                eq(Topic.SESSION_REVOKED),
                eq(Map.of("reason", "backchannel_logout"))
        );
    }

    @Test
    void handleLogoutToken_multipleSessionsRevokedBySid() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var claims = new JWTClaimsSet.Builder()
                .jwtID("jti-multi")
                .subject("user-sub")
                .claim("sid", "shared-sid")
                .build();
        when(oidcTokenValidator.validateLogoutToken("token", "https://issuer.example.com", "my-client"))
                .thenReturn(claims);

        var user1 = BookLoreUserEntity.builder().id(1L).username("user1").build();
        var user2 = BookLoreUserEntity.builder().id(2L).username("user2").build();
        var session1 = OidcSessionEntity.builder().user(user1).oidcSessionId("shared-sid").revoked(false).build();
        var session2 = OidcSessionEntity.builder().user(user2).oidcSessionId("shared-sid").revoked(false).build();
        when(oidcSessionRepository.findByOidcSessionIdAndRevokedFalse("shared-sid"))
                .thenReturn(List.of(session1, session2));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(any())).thenReturn(List.of());

        backchannelLogoutService.handleLogoutToken("token");

        assertThat(session1.isRevoked()).isTrue();
        assertThat(session2.isRevoked()).isTrue();
        verify(oidcSessionRepository).save(session1);
        verify(oidcSessionRepository).save(session2);
        verify(notificationService).sendMessageToUser(eq("user1"), eq(Topic.SESSION_REVOKED), any(Map.class));
        verify(notificationService).sendMessageToUser(eq("user2"), eq(Topic.SESSION_REVOKED), any(Map.class));
        verify(auditService, times(2)).log(eq(AuditAction.BACKCHANNEL_LOGOUT), eq("User"), any(Long.class), any(String.class));
    }

}
